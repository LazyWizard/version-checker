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
import org.lwjgl.opengl.Display;

final class UpdateNotificationScript implements EveryFrameScript
{
    private float timeUntilWarn = .75f; // Ensures text appears
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
        final int modsWithoutUpdates = updateInfo.getHasNoUpdate().size(),
                modsWithUpdates = updateInfo.getHasUpdate().size(),
                modsThatFailedUpdateCheck = updateInfo.getFailed().size();

        // Display number of mods that are up-to-date
        if (modsWithoutUpdates > 0)
        {
            ui.addMessage(modsWithoutUpdates + (modsWithoutUpdates == 1
                    ? " mod is " : " mods are") + " up to date.",
                    Integer.toString(modsWithoutUpdates), Color.GREEN);
        }

        // Display number of mods with an update available
        if (modsWithUpdates > 0)
        {
            ui.addMessage("Found updates for " + modsWithUpdates
                    + (modsWithUpdates == 1 ? " mod." : " mods."),
                    Integer.toString(modsWithUpdates), Color.YELLOW);
        }

        // Display number of mods that failed the update check
        if (modsThatFailedUpdateCheck > 0)
        {
            ui.addMessage("Update check failed for " + modsThatFailedUpdateCheck
                    + (modsThatFailedUpdateCheck == 1 ? " mod." : " mods."),
                    Integer.toString(modsThatFailedUpdateCheck), Color.RED);
        }

        // Warn if a Starsector update is available
        if (updateInfo.ssUpdate != null)
        {
            ui.addMessage("There is a game update available: " + updateInfo.ssUpdate,
                    updateInfo.ssUpdate, Color.YELLOW);
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
        if (!hasWarned && timeUntilWarn <= 0f)
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
        private static final String ANNOUNCEMENT_BOARD
                = "http://fractalsoftworks.com/forum/index.php?board=1.0";
        private static final int ENTRIES_PER_PAGE = 5;
        private final String ssUpdate;
        private final List<ModInfo> hasUpdate, hasNoUpdate, failedCheck;
        private InteractionDialogAPI dialog;
        private TextPanelAPI text;
        private OptionPanelAPI options;
        private List<ModInfo> currentList;
        private int currentPage = 1;

        private enum Menu
        {
            MAIN_MENU,
            UPDATE_VANILLA,
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
            ssUpdate = updateInfo.ssUpdate;

            // Sort by mod name
            Collections.sort(hasUpdate);
            Collections.sort(hasNoUpdate);
            Collections.sort(failedCheck);
        }

        private void generateModMenu()
        {
            // Show as many mods as can fit into one page of options
            final int offset = (currentPage - 1) * ENTRIES_PER_PAGE,
                    max = Math.min(offset + ENTRIES_PER_PAGE, currentList.size()),
                    numPages = 1 + ((currentList.size() - 1) / ENTRIES_PER_PAGE);
            for (int x = offset, y = 1; x < max; x++, y++)
            {
                ModInfo mod = currentList.get(x);
                VersionFile local = mod.getLocalVersion();
                options.addOption(local.getName(), local);
                options.setEnabled(local, local.getThreadURL() != null);
                //options.setShortcut(local, Keyboard.getKeyIndex(Integer.toString(y)),
                //        false, false, false, false);
            }

            // Support for multiple pages of options
            if (currentPage > 1)
            {
                options.addOption("Previous page", Menu.PREVIOUS_PAGE);
                //options.setShortcut(Menu.PREVIOUS_PAGE, Keyboard.KEY_LEFT,
                //        false, false, false, true);
            }
            if (currentPage < numPages)
            {
                options.addOption("Next page", Menu.NEXT_PAGE);
                //options.setShortcut(Menu.NEXT_PAGE, Keyboard.KEY_RIGHT,
                //        false, false, false, true);
            }

            // Show page number in prompt if multiple pages are present
            dialog.setPromptText("Select a mod to go to its forum thread"
                    + (numPages > 1 ? " (page " + currentPage + "/" + numPages + ")" : "") + ":");
            options.addOption("Main menu", Menu.MAIN_MENU);
            //options.setShortcut(Menu.MAIN_MENU, Keyboard.KEY_ESCAPE,
            //        false, false, false, true);
        }

        private void goToMenu(Menu menu)
        {
            options.clearOptions();

            switch (menu)
            {
                case MAIN_MENU:
                    text.clear();
                    final int numUpToDate = hasNoUpdate.size(),
                     numHasUpdate = hasUpdate.size(),
                     numFailed = failedCheck.size();

                    text.addParagraph((numUpToDate == 1)
                            ? "There is 1 up-to-date mod"
                            : "There are " + numUpToDate + " up-to-date mods");
                    text.highlightInLastPara(Color.GREEN,
                            Integer.toString(numUpToDate));
                    for (ModInfo info : hasNoUpdate)
                    {
                        text.addParagraph(" - " + info.getName() + " ("
                                + info.getVersionString() + ")");
                        text.highlightInLastPara(Color.GREEN, info.getName());
                    }

                    text.addParagraph((numHasUpdate == 1)
                            ? "There is 1 mod with an update available"
                            : "There are " + numHasUpdate + " mods with updates available");
                    text.highlightInLastPara((numHasUpdate > 0 ? Color.YELLOW
                            : Color.GREEN), Integer.toString(numHasUpdate));
                    for (ModInfo info : hasUpdate)
                    {
                        text.addParagraph(" - " + info.getName() + " ("
                                + info.getVersionString() + ")");
                        text.highlightInLastPara(Color.YELLOW, info.getName());
                    }

                    text.addParagraph((numFailed == 1)
                            ? "There is 1 mod that failed its update check"
                            : "There are " + numFailed + " mods that failed their update checks");
                    text.highlightInLastPara((numFailed > 0 ? Color.RED
                            : Color.GREEN), Integer.toString(numFailed));
                    for (ModInfo info : failedCheck)
                    {
                        text.addParagraph(" - " + info.getName() + " ("
                                + info.getVersionString() + ")");
                        text.highlightInLastPara(Color.RED, info.getName());
                    }

                    dialog.setPromptText("Select a category for forum thread links:");
                    options.addOption("List mods with updates", Menu.LIST_UPDATES);
                    options.setEnabled(Menu.LIST_UPDATES, !hasUpdate.isEmpty());
                    //options.setShortcut(Menu.LIST_UPDATES, Keyboard.KEY_1,
                    //        false, false, false, false);
                    options.addOption("List mods without updates", Menu.LIST_NO_UPDATES);
                    options.setEnabled(Menu.LIST_NO_UPDATES, !hasNoUpdate.isEmpty());
                    //options.setShortcut(Menu.LIST_NO_UPDATES, Keyboard.KEY_2,
                    //        false, false, false, false);
                    options.addOption("List mods that failed update check", Menu.LIST_FAILED);
                    options.setEnabled(Menu.LIST_FAILED, !failedCheck.isEmpty());
                    //options.setShortcut(Menu.LIST_FAILED, Keyboard.KEY_3,
                    //        false, false, false, false);

                    // Notify of game update if available
                    if (ssUpdate != null)
                    {
                        text.addParagraph("There is a game update available:\n - " + ssUpdate);
                        text.highlightInLastPara(Color.YELLOW, ssUpdate);

                        options.addOption("Download " + ssUpdate, Menu.UPDATE_VANILLA);
                        //options.setShortcut(Menu.UPDATE_VANILLA, Keyboard.KEY_4,
                        //        false, false, false, false);
                    }

                    options.addOption("Exit", Menu.EXIT);
                    //options.setShortcut(Menu.EXIT, Keyboard.KEY_ESCAPE,
                    //        false, false, false, true);
                    break;
                case UPDATE_VANILLA:
                    goToMenu(Menu.MAIN_MENU);
                    text.addParagraph("Opening update announcement subforum...");
                    options.setEnabled(Menu.UPDATE_VANILLA, false);
                    try
                    {
                        Desktop.getDesktop().browse(new URI(ANNOUNCEMENT_BOARD));
                    }
                    catch (IOException | URISyntaxException ex)
                    {
                        Global.getLogger(VersionChecker.class).log(Level.ERROR,
                                "Failed to launch browser:", ex);
                        text.addParagraph("Failed to launch browser: "
                                + ex.getMessage(), Color.RED);
                    }
                    break;
                case LIST_UPDATES:
                    currentList = hasUpdate;
                    currentPage = 1;
                    generateModMenu();
                    break;
                case LIST_NO_UPDATES:
                    currentList = hasNoUpdate;
                    currentPage = 1;
                    generateModMenu();
                    break;
                case LIST_FAILED:
                    currentList = failedCheck;
                    currentPage = 1;
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

            dialog.setTextWidth(Display.getWidth() * .9f);
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
            // Option was version data? Launch that mod's forum thread
            else if (optionData instanceof VersionFile)
            {
                try
                {
                    VersionFile info = (VersionFile) optionData;
                    text.addParagraph("Opening " + info.getName() + " forum thread...");
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
