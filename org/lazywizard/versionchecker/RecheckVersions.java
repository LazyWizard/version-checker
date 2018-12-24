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
                final VCModPlugin plugin = (VCModPlugin) tmp;
                try
                {
                    plugin.onApplicationLoad();

                    // Remove any existing notification plugin and replace with the newly created one
                    if (context.isCampaignAccessible())
                    {
                        Global.getSector().removeTransientScriptsOfClass(UpdateNotificationScript.class);
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
