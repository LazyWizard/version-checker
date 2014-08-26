package org.lazywizard.versionchecker;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

class UpdateNotificationScript implements EveryFrameScript
{
    private boolean hasWarned = false;
    private final List<UpdateInfo> updateInfo;

    UpdateNotificationScript(final List<UpdateInfo> updateInfo)
    {
        this.updateInfo = Collections.<UpdateInfo>unmodifiableList(updateInfo);
    }

    @Override
    public boolean isDone()
    {
        return hasWarned;
    }

    @Override
    public boolean runWhilePaused()
    {
        return false;
    }

    @Override
    public void advance(float amount)
    {
        // TODO
        if (updateInfo.isEmpty())
        {
            Global.getSector().getCampaignUI().addMessage(
                    "All mods are up to date.", Color.GREEN);
        }
        else
        {
            Global.getSector().getCampaignUI().addMessage(
                    "Found updates for " + updateInfo.size() + " mods:", Color.YELLOW);
            for (UpdateInfo tmp : updateInfo)
            {
                    Global.getSector().getCampaignUI().addMessage(
                            " - " + tmp, Color.YELLOW);
            }
        }

        hasWarned = true;
    }
}
