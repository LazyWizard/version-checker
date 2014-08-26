package org.lazywizard.versionchecker;

import java.awt.Color;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;

class UpdateNotificationScript implements EveryFrameScript
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
        if (futureUpdateInfo.isDone())
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
                Global.getLogger(UpdateNotificationScript.class).log(Level.FATAL,
                        "Failed to retrieve mod update info!", ex);
                Global.getSector().getCampaignUI().addMessage(
                        "Failed to retrieve mod update info!", Color.RED);
                Global.getSector().getCampaignUI().addMessage(
                        "Check starsector.log for details.", Color.RED);
                return;
            }

            // List mods with an update available
            int modsWithUpdates = updateInfo.hasUpdates.size();
            if (modsWithUpdates == 0)
            {
                Global.getSector().getCampaignUI().addMessage(
                        "All mods are up to date.", Color.GREEN);
            }
            else
            {
                Global.getSector().getCampaignUI().addMessage(
                        "Found updates for " + modsWithUpdates
                        + (modsWithUpdates > 1 ? " mods:" : " mod:"), Color.YELLOW);
                for (ModInfo tmp : updateInfo.hasUpdates)
                {
                    Global.getSector().getCampaignUI().addMessage(
                            " - " + tmp, Color.YELLOW);
                }
            }

            // List mods that failed the update check
            int modsThatFailedUpdateCheck = updateInfo.failedCheck.size();
            if (modsThatFailedUpdateCheck > 0)
            {
                Global.getSector().getCampaignUI().addMessage(
                        "Update check failed for " + modsThatFailedUpdateCheck
                        + (modsThatFailedUpdateCheck > 1 ? " mods:" : " mod:"), Color.RED);
                for (VersionInfo tmp : updateInfo.failedCheck)
                {
                    Global.getSector().getCampaignUI().addMessage(
                            " - " + tmp, Color.RED);
                }
            }
        }
    }
}