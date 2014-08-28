package org.lazywizard.versionchecker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;

final class UpdateInfo
{
    private final List<ModInfo> hasUpdate = new ArrayList<>();
    private final List<ModInfo> hasNoUpdate = new ArrayList<>();
    private final List<VersionInfo> failedCheck = new ArrayList<>();
    private int numModsChecked = 0;

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

    int getNumModsChecked()
    {
        return numModsChecked;
    }

    List<ModInfo> getHasNoUpdate()
    {
        return Collections.<ModInfo>unmodifiableList(hasNoUpdate);
    }

    static final class ModInfo
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
            return oldVersion.getName() + " (" + oldVersion.getVersion()
                    + " => " + newVersion.getVersion() + ")";
        }
    }

    static final class VersionInfo
    {
        private final int major, minor;
        private final String patch;
        private final String masterURL, modName; //, gameVersion;

        VersionInfo(final JSONObject versionFile, boolean isMaster) throws JSONException
        {
            // Parse mod details
            masterURL = (isMaster ? null : versionFile.getString("masterVersionFile"));
            modName = versionFile.optString("modName", "<unknown>");
            //gameVersion = versionFile.optString("starsectorVersion", "");

            // Parse version details
            JSONObject modVersion = versionFile.getJSONObject("modVersion");

            major = modVersion.optInt("major", 0);
            minor = modVersion.optInt("minor", 0);
            patch = modVersion.optString("patch", "0");
        }

        boolean isOlderThan(VersionInfo other)
        {
            // DEBUG
            Global.getLogger(VersionChecker.class).log(Level.DEBUG,
                    modName + ": " + getVersion() + " vs " + other.getVersion());

            return (major < other.major)
                    || (major == other.major && minor < other.minor)
                    || (major == other.major && minor == other.minor
                    && patch.compareToIgnoreCase(other.patch) < 0);
        }

        String getName()
        {
            return modName;
        }

        private static boolean isNumerical(String str)
        {
            // Search for non-numeric characters in the string
            for (char tmp : str.toCharArray())
            {
                if (!Character.isDigit(tmp))
                {
                    return false;
                }
            }

            return true;
        }

        String getVersion()
        {
            // Don't show patch number if there isn't one
            if (patch.equals("0"))
            {
                // Don't show minor version if there isn't one
                if (minor == 0)
                {
                    return "" + major;
                }
                else
                {
                    return major + "." + minor;
                }
            }

            // Support for character patch notation (v2.4b vs v2.4.1)
            if (isNumerical(patch))
            {
                return major + "." + minor + "." + patch;
            }
            else
            {
                return major + "." + minor + patch;
            }
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
