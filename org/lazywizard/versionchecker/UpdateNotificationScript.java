package org.lazywizard.versionchecker;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

class UpdateNotificationScript implements EveryFrameScript
{
    private boolean hasWarned = false;
    private final Future<List<UpdateInfo>> futureUpdateInfo;

    UpdateNotificationScript(final Future<List<UpdateInfo>> updateInfo)
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
            List<UpdateInfo> updateInfo;
            try
            {
                updateInfo = futureUpdateInfo.get(1l, TimeUnit.SECONDS);
            }
            catch (InterruptedException | ExecutionException | TimeoutException ex)
            {
                throw new RuntimeException(ex);
            }

            if (updateInfo.isEmpty())
            {
                Global.getSector().getCampaignUI().addMessage(
                        "All mods are up to date.", Color.GREEN);
            }
            else
            {
                Global.getSector().getCampaignUI().addMessage(
                        "Found updates for " + updateInfo.size()
                        + (updateInfo.size() > 1 ? " mods:" : " mod:"),
                        Color.YELLOW);
                for (UpdateInfo tmp : updateInfo)
                {
                    Global.getSector().getCampaignUI().addMessage(
                            " - " + tmp, Color.YELLOW);
                }
            }

            hasWarned = true;
        }
    }
}
