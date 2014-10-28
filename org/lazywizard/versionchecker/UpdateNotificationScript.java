package org.lazywizard.versionchecker;

import java.awt.Color;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import org.apache.log4j.Level;
import org.lazywizard.versionchecker.UpdateInfo.ModInfo;
import org.lazywizard.versionchecker.UpdateInfo.VersionFile;
import org.lwjgl.input.Keyboard;

final class UpdateNotificationScript implements EveryFrameScript
{
    private float timeUntilWarn = 1.5f; // Ensures text appears
    private boolean isUpdateCheckDone = false, hasWarned = false;
    private transient Future<UpdateInfo> futureUpdateInfo;
    private transient UpdateInfo updateInfo;

    UpdateNotificationScript(final Future<UpdateInfo> updateInfo)
    {
        this.futureUpdateInfo = updateInfo;
    }

    @Override
    public boolean isDone()
    {
        return false;
    }

    @Override
    public boolean runWhilePaused()
    {
        return true;
    }

    private void warnUpdates(CampaignUIAPI ui)
    {
        final List<ModInfo> hasUpdate = updateInfo.getHasUpdate();
        final List<ModInfo> hasNoUpdate = updateInfo.getHasNoUpdate();
        final List<ModInfo> failedCheck = updateInfo.getFailed();
        final String modsWithoutUpdates = Integer.toString(hasNoUpdate.size());
        final String modsWithUpdates = Integer.toString(hasUpdate.size());
        final String modsThatFailedUpdateCheck = Integer.toString(failedCheck.size());

        // Display number of mods that are up-to-date
        if (hasNoUpdate.size() > 0)
        {
            ui.addMessage(modsWithoutUpdates + " mods are up to date.",
                    modsWithoutUpdates, Color.GREEN);
        }

        // Display number of mods with an update available
        if (hasUpdate.size() > 0)
        {
            ui.addMessage("Found updates for " + modsWithUpdates
                    + (hasUpdate.size() > 1 ? " mods:" : " mod:"),
                    modsWithUpdates, Color.YELLOW);
            /*for (ModInfo tmp : hasUpdate)
             {
             ui.addMessage(" - " + tmp.getName() + " ("
             + tmp.getVersionString() + ")", Color.YELLOW);
             }*/
        }

        // Display number of mods that failed the update check
        if (failedCheck.size() > 0)
        {
            ui.addMessage("Update check failed for " + modsThatFailedUpdateCheck
                    + (failedCheck.size() > 1 ? " mods:" : " mod:"),
                    modsThatFailedUpdateCheck, Color.RED);
            /*for (ModInfo tmp : failedCheck)
             {
             ui.addMessage(" - " + tmp.getName(), Color.RED);
             }*/
        }

        String keyName = Keyboard.getKeyName(VCModPlugin.notificationKey);
        ui.addMessage("Press " + keyName + " for detailed update information.",
                keyName, Color.CYAN);
    }

    @Override
    public void advance(float amount)
    {
        // Don't do anything while in a menu/dialog
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (Global.getSector().isInNewGameAdvance() || ui.isShowingDialog())
        {
            return;
        }

        // Check if the update thread has finished
        if (!isUpdateCheckDone)
        {
            // We can't do anything if it's not done checking for updates
            if (!futureUpdateInfo.isDone())
            {
                return;
            }

            // Attempt to retrieve the update results from the other thread
            try
            {
                updateInfo = futureUpdateInfo.get(1l, TimeUnit.SECONDS);
                futureUpdateInfo = null;
            }
            catch (InterruptedException | ExecutionException | TimeoutException ex)
            {
                Global.getLogger(VersionChecker.class).log(Level.ERROR,
                        "Failed to retrieve mod update info", ex);
                ui.addMessage("Failed to retrieve mod update info!", Color.RED);
                ui.addMessage("Check starsector.log for details.", Color.RED);
                return;
            }

            isUpdateCheckDone = true;
        }

        // On first game load, warn about any updates available
        if (!hasWarned && timeUntilWarn < 1f)
        {
            warnUpdates(ui);
            hasWarned = true;
        }
        else
        {
            timeUntilWarn -= amount;
        }

        // User can press a key to summon a detailed update report
        if (Keyboard.isKeyDown(VCModPlugin.notificationKey))
        {
            ui.showInteractionDialog(new UpdateNotificationDialog(updateInfo),
                    Global.getSector().getPlayerFleet());
        }
    }

    private static class UpdateNotificationDialog implements InteractionDialogPlugin
    {
        private static final int ENTRIES_PER_PAGE = 5;
        private final List<ModInfo> hasUpdate, hasNoUpdate, failedCheck;
        private InteractionDialogAPI dialog;
        private TextPanelAPI text;
        private OptionPanelAPI options;
        private List<ModInfo> currentList;
        private int currentPage = 0;

        private enum Menu
        {
            MAIN_MENU,
            LIST_UPDATES,
            LIST_NO_UPDATES,
            LIST_FAILED,
            PREVIOUS_PAGE,
            NEXT_PAGE,
            RETURN,
            EXIT
        }

        private UpdateNotificationDialog(UpdateInfo updateInfo)
        {
            hasUpdate = updateInfo.getHasUpdate();
            hasNoUpdate = updateInfo.getHasNoUpdate();
            failedCheck = updateInfo.getFailed();

            // Sort by mod name
            Collections.sort(hasUpdate);
            Collections.sort(hasNoUpdate);
            Collections.sort(failedCheck);
        }

        private void generateModMenu()
        {
            // Show as many mods as can fit into one page of options
            int offset = currentPage * ENTRIES_PER_PAGE,
                    max = Math.min(offset + ENTRIES_PER_PAGE, currentList.size());
            for (int x = offset; x < max; x++)
            {
                ModInfo mod = currentList.get(x);
                VersionFile local = mod.getLocalVersion();
                options.addOption((x + 1) + ": " + local.getName(), local);
                options.setEnabled(local, local.getThreadURL() != null);
            }

            // Support for multiple pages of options
            if (currentPage > 0)
            {
                options.addOption("Previous page", Menu.PREVIOUS_PAGE);
            }
            if (max < currentList.size())
            {
                options.addOption("Next page", Menu.NEXT_PAGE);
            }

            dialog.setPromptText("Select a mod to go to its forum thread:");
            options.addOption("Main menu", Menu.MAIN_MENU);
        }

        private void goToMod(ModInfo mod)
        {
            options.clearOptions();
            VersionFile local = mod.getLocalVersion();

            if (mod.isUpdateAvailable())
            {
                text.addParagraph("Update available for " + mod.getName()
                        + ": " + mod.getVersionString(), Color.YELLOW);
            }
            else
            {
                text.addParagraph(local.getName() + " is up-to-date ("
                        + local.getVersion() + ")", Color.GREEN);
            }

            String URL = local.getThreadURL();
            options.addOption("Go to forum thread", local);
            options.setEnabled(local, URL != null);

            dialog.setPromptText("Select an option:");
            options.addOption("Back", Menu.RETURN);
        }

        private void goToMenu(Menu menu)
        {
            options.clearOptions();

            switch (menu)
            {
                case MAIN_MENU:
                    text.clear();
                    String numUpToDate = Integer.toString(hasNoUpdate.size());
                    String numHasUpdate = Integer.toString(hasUpdate.size());
                    String numFailed = Integer.toString(failedCheck.size());

                    text.addParagraph("There are " + numUpToDate
                            + " up-to-date mods");
                    text.highlightInLastPara(Color.GREEN, numUpToDate);
                    for (ModInfo info : hasNoUpdate)
                    {
                        text.addParagraph(" - " + info.getName() + " ("
                                + info.getVersionString() + ")");
                        text.highlightInLastPara(Color.GREEN, info.getName());
                    }

                    text.addParagraph("There are " + numHasUpdate
                            + " mods with updates available");
                    text.highlightInLastPara((hasUpdate.size() > 0
                            ? Color.YELLOW : Color.GREEN), numHasUpdate);
                    for (ModInfo info : hasUpdate)
                    {
                        text.addParagraph(" - " + info.getName() + " ("
                                + info.getVersionString() + ")");
                        text.highlightInLastPara(Color.YELLOW, info.getName());
                    }

                    text.addParagraph("There are " + numFailed
                            + " mods that failed their update check");
                    text.highlightInLastPara((failedCheck.size() > 0
                            ? Color.RED : Color.GREEN), numFailed);
                    for (ModInfo info : failedCheck)
                    {
                        text.addParagraph(" - " + info.getName() + ")");
                        text.highlightInLastPara(Color.RED, info.getName());
                    }

                    dialog.setPromptText("Select an option:");
                    options.addOption("List mods with updates", Menu.LIST_UPDATES);
                    options.setEnabled(Menu.LIST_UPDATES, !hasUpdate.isEmpty());
                    options.addOption("List mods without updates", Menu.LIST_NO_UPDATES);
                    options.setEnabled(Menu.LIST_NO_UPDATES, !hasNoUpdate.isEmpty());
                    options.addOption("List mods that failed update check", Menu.LIST_FAILED);
                    options.setEnabled(Menu.LIST_FAILED, !failedCheck.isEmpty());
                    options.addOption("Exit", Menu.EXIT);
                    break;
                case LIST_UPDATES:
                    currentList = hasUpdate;
                    currentPage = 0;
                    generateModMenu();
                    break;
                case LIST_NO_UPDATES:
                    currentList = hasNoUpdate;
                    currentPage = 0;
                    generateModMenu();
                    break;
                case LIST_FAILED:
                    currentList = failedCheck;
                    currentPage = 0;
                    generateModMenu();
                    break;
                case PREVIOUS_PAGE:
                    currentPage--;
                    generateModMenu();
                    break;
                case NEXT_PAGE:
                    currentPage++;
                    generateModMenu();
                    break;
                case RETURN:
                    generateModMenu();
                    break;
                case EXIT:
                default:
                    dialog.dismiss();
            }
        }

        @Override
        public void init(InteractionDialogAPI dialog)
        {
            this.dialog = dialog;
            this.options = dialog.getOptionPanel();
            this.text = dialog.getTextPanel();

            dialog.setTextWidth(dialog.getTextWidth() * 1.5f);
            goToMenu(Menu.MAIN_MENU);
        }

        @Override
        public void optionSelected(String optionText, Object optionData)
        {
            text.addParagraph(optionText, Color.CYAN);

            // Option was a menu? Go to that menu
            if (optionData instanceof Menu)
            {
                goToMenu((Menu) optionData);
            }
            // Option was a mod? Go to mod details page
            else if (optionData instanceof ModInfo)
            {
                goToMod((ModInfo) optionData);
            }
            // Option was version data? Launch that mod's forum thread
            else if (optionData instanceof VersionFile)
            {
                try
                {
                    VersionFile info = (VersionFile) optionData;
                    text.addParagraph("Launching web browser...");
                    options.setEnabled(info, false);
                    Desktop.getDesktop().browse(new URI(info.getThreadURL()));
                }
                catch (IOException | URISyntaxException ex)
                {
                    Global.getLogger(VersionChecker.class).log(Level.ERROR,
                            "Failed to launch browser:", ex);
                    text.addParagraph("Failed to launch browser: "
                            + ex.getMessage(), Color.RED);
                }
            }
        }

        @Override
        public void optionMousedOver(String optionText, Object optionData)
        {
        }

        @Override
        public void advance(float amount)
        {
        }

        @Override
        public void backFromEngagement(EngagementResultAPI battleResult)
        {
        }

        @Override
        public Object getContext()
        {
            return null;
        }

        @Override
        public Map<String, MemoryAPI> getMemoryMap()
        {
            return null;
        }
    }
}
