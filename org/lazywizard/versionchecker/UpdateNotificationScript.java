package org.lazywizard.versionchecker;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import org.apache.log4j.Level;
import org.lazywizard.versionchecker.UpdateInfo.ModInfo;
import org.lazywizard.versionchecker.UpdateInfo.VersionInfo;

final class UpdateNotificationScript implements EveryFrameScript
{
    private boolean hasWarned = false;
    private final Future<UpdateInfo> futureUpdateInfo;

    UpdateNotificationScript(final Future<UpdateInfo> updateInfo)
    {
        this.futureUpdateInfo = updateInfo;
    }

    @Override
    public boolean isDone()
    {
        return hasWarned || futureUpdateInfo.isCancelled();
    }

    @Override
    public boolean runWhilePaused()
    {
        return false;
    }

    @Override
    public void advance(float amount)
    {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (!ui.isShowingDialog() && futureUpdateInfo.isDone())
        {
            hasWarned = true;

            // Attempt to retrieve the update results from the other thread
            UpdateInfo updateInfo;
            try
            {
                updateInfo = futureUpdateInfo.get(1l, TimeUnit.SECONDS);
            }
            catch (InterruptedException | ExecutionException | TimeoutException ex)
            {
                Global.getLogger(VersionChecker.class).log(Level.FATAL,
                        "Failed to retrieve mod update info", ex);
                ui.addMessage("Failed to retrieve mod update info!", Color.RED);
                ui.addMessage("Check starsector.log for details.", Color.RED);
                return;
            }

            final List<ModInfo> hasUpdate = updateInfo.getHasUpdate();
            final List<ModInfo> hasNoUpdate = updateInfo.getHasNoUpdate();
            final List<VersionInfo> failedCheck = updateInfo.getFailed();
            final int modsWithoutUpdates = hasNoUpdate.size();
            final int modsWithUpdates = hasUpdate.size();
            final int modsThatFailedUpdateCheck = failedCheck.size();

            // Display number of mods that are up-to-date
            if (modsWithoutUpdates > 0)
            {
                ui.addMessage(modsWithoutUpdates + " mods are up to date.", Color.GREEN);
            }

            // List mods with an update available
            if (modsWithUpdates > 0)
            {
                ui.addMessage("Found updates for " + modsWithUpdates
                        + (modsWithUpdates > 1 ? " mods:" : " mod:"), Color.YELLOW);
                for (ModInfo tmp : hasUpdate)
                {
                    ui.addMessage(" - " + tmp, Color.YELLOW);
                }
            }

            // List mods that failed the update check
            if (modsThatFailedUpdateCheck > 0)
            {
                ui.addMessage("Update check failed for " + modsThatFailedUpdateCheck
                        + (modsThatFailedUpdateCheck > 1 ? " mods:" : " mod:"), Color.RED);
                for (VersionInfo tmp : failedCheck)
                {
                    ui.addMessage(" - " + tmp, Color.RED);
                }
            }
        }
    }
}
