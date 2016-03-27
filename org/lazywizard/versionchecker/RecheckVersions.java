package org.lazywizard.versionchecker;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModPlugin;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

public class RecheckVersions implements BaseCommand
{
    @Override
    public CommandResult runCommand(String args, CommandContext context)
    {
        for (ModPlugin tmp : Global.getSettings().getModManager().getEnabledModPlugins())
        {
            if (tmp instanceof VCModPlugin)
            {
                VCModPlugin plugin = (VCModPlugin) tmp;
                try
                {
                    plugin.onApplicationLoad();

                    if (context.isInCampaign() || (context.isInCombat()
                            && (Global.getCombatEngine().isInCampaign()
                            || Global.getCombatEngine().isInCampaignSim())))
                    {
                        Global.getSector().removeScriptsOfClass(UpdateNotificationScript.class);
                        plugin.onGameLoad(false);
                    }

                    Console.showMessage("Update check started successfully.");
                    return CommandResult.SUCCESS;
                }
                catch (Exception ex)
                {
                    Console.showException("Something went wrong!", ex);
                    return CommandResult.ERROR;
                }
            }
        }

        Console.showMessage("Couldn't find Version Checker's ModPlugin!");
        return CommandResult.ERROR;
    }
}
