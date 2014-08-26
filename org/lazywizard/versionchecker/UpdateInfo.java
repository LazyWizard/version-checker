package org.lazywizard.versionchecker;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;

class UpdateInfo
{
    final List<ModInfo> hasUpdate = new ArrayList<>();
    final List<ModInfo> hasNoUpdate = new ArrayList<>();
    final List<VersionInfo> failedCheck = new ArrayList<>();
    private final long startTime;
    private int numModsChecked = 0;

    UpdateInfo()
    {
        startTime = System.nanoTime();
    }

    void addFailed(VersionInfo version)
    {
        failedCheck.add(version);
        numModsChecked++;
    }

    void addUpdate(ModInfo mod)
    {
        hasUpdate.add(mod);
        numModsChecked++;
    }

    void addNoUpdate(ModInfo mod)
    {
        hasNoUpdate.add(mod);
        numModsChecked++;
    }

    void finish()
    {
        long elapsedTime = System.nanoTime() - startTime;
        Global.getLogger(VersionChecker.class).log(Level.INFO,
                "Checked " + numModsChecked + " mods in "
                + DecimalFormat.getNumberInstance().format(
                        elapsedTime / 1000000000.0d) + " seconds.");
    }
}
