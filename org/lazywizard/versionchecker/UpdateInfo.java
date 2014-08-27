package org.lazywizard.versionchecker;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;

class UpdateInfo
{
    private final List<ModInfo> hasUpdate = new ArrayList<>();
    private final List<ModInfo> hasNoUpdate = new ArrayList<>();
    private final List<VersionInfo> failedCheck = new ArrayList<>();
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

    List<VersionInfo> getFailed()
    {
        return Collections.<VersionInfo>unmodifiableList(failedCheck);
    }

    void addUpdate(ModInfo mod)
    {
        hasUpdate.add(mod);
        numModsChecked++;
    }

    List<ModInfo> getHasUpdate()
    {
        return Collections.<ModInfo>unmodifiableList(hasUpdate);
    }

    void addNoUpdate(ModInfo mod)
    {
        hasNoUpdate.add(mod);
        numModsChecked++;
    }

    List<ModInfo> getHasNoUpdate()
    {
        return Collections.<ModInfo>unmodifiableList(hasNoUpdate);
    }

    void finish()
    {
        long elapsedTime = System.nanoTime() - startTime;
        Global.getLogger(VersionChecker.class).log(Level.INFO,
                "Checked " + numModsChecked + " mods in "
                + DecimalFormat.getNumberInstance().format(
                        elapsedTime / 1000000000.0d) + " seconds.");
    }

    static class ModInfo
    {
        private final VersionInfo oldVersion, newVersion;

        ModInfo(VersionInfo oldVersion, VersionInfo newVersion)
        {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        boolean isUpdateAvailable()
        {
            return oldVersion.isOlderThan(newVersion);
        }

        @Override
        public String toString()
        {
            return oldVersion.modName + " (" + oldVersion.getVersion() + " => "
                    + newVersion.getVersion() + ")";
        }
    }

    static class VersionInfo
    {
        private final int major, minor, patch;
        private final String masterURL, modName; //, gameVersion;

        VersionInfo(final JSONObject versionFile, boolean isMaster) throws JSONException
        {
            // Parse mod details
            masterURL = (isMaster ? "" : versionFile.getString("masterVersionFile"));
            modName = versionFile.optString("modName", "<unknown>");
            //gameVersion = versionFile.optString("starsectorVersion", "");

            // Parse version details
            JSONObject modVersion = versionFile.getJSONObject("modVersion");
            major = modVersion.optInt("major", 0);
            minor = modVersion.optInt("minor", 0);
            patch = modVersion.optInt("patch", 0);
        }

        boolean isOlderThan(VersionInfo other)
        {
            // DEBUG
            Global.getLogger(VersionChecker.class).log(Level.DEBUG,
                    modName + ": " + getVersion() + " vs " + other.getVersion());

            return (major < other.major)
                    || (major == other.major && minor < other.minor)
                    || (major == other.major && minor == other.minor && patch < other.patch);
        }

        String getName()
        {
            return modName;
        }

        String getVersion()
        {
            if (patch == 0)
            {
                if (minor == 0)
                {
                    return "" + major;
                }
                else
                {
                    return major + "." + minor;
                }
            }

            return major + "." + minor + "." + patch;
        }

        String getMasterURL()
        {
            return masterURL;
        }

        @Override
        public String toString()
        {
            return getName() + " v" + getVersion();
        }
    }
}
