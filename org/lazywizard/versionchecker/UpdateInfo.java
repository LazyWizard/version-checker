package org.lazywizard.versionchecker;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;

// TODO: ModInfo and VersionInfo need some cleanup
final class UpdateInfo
{
    private final List<ModInfo> hasUpdate = new ArrayList<>();
    private final List<ModInfo> hasNoUpdate = new ArrayList<>();
    private final List<ModInfo> failedCheck = new ArrayList<>();
    private int numModsChecked = 0;

    void addFailed(ModInfo mod)
    {
        failedCheck.add(mod);
        numModsChecked++;
    }

    List<ModInfo> getFailed()
    {
        return new ArrayList<>(failedCheck);
    }

    void addUpdate(ModInfo mod)
    {
        hasUpdate.add(mod);
        numModsChecked++;
    }

    List<ModInfo> getHasUpdate()
    {
        return new ArrayList<>(hasUpdate);
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
        return new ArrayList<>(hasNoUpdate);
    }

    static final class ModInfo implements Comparable<ModInfo>
    {
        private final VersionFile localVersion, remoteVersion;
        private final boolean failedUpdate;

        ModInfo(VersionFile localVersion, VersionFile remoteVersion)
        {
            this.localVersion = localVersion;
            this.remoteVersion = remoteVersion;
            failedUpdate = (remoteVersion == null);
        }

        String getName()
        {
            return localVersion.getName();
        }

        VersionFile getLocalVersion()
        {
            return localVersion;
        }

        VersionFile getRemoteVersion()
        {
            return remoteVersion;
        }

        boolean failedUpdateCheck()
        {
            return failedUpdate;
        }

        boolean isUpdateAvailable()
        {
            return localVersion.isOlderThan(remoteVersion);
        }

        public String getVersionString()
        {
            if (failedUpdate)
            {
                return localVersion.getVersion();
            }

            String operator;
            if (localVersion.isOlderThan(remoteVersion))
            {
                operator = " <= ";
            }
            else if (localVersion.isNewerThan(remoteVersion))
            {
                operator = " => ";
            }
            else
            {
                operator = " == ";
            }

            return localVersion.getVersion() + operator + remoteVersion.getVersion();
        }

        @Override
        public int compareTo(ModInfo other)
        {
            return localVersion.getName().compareTo(other.localVersion.getName());
        }
    }

    static final class VersionFile
    {
        private static final String MOD_THREAD_FORMAT
                = "http://fractalsoftworks.com/forum/index.php?topic=%d.0";
        private final int major, minor, modThreadId;
        private final String patch, masterURL, modName;

        VersionFile(final JSONObject json, boolean isMaster) throws JSONException
        {
            // Parse mod details (local version file only)
            masterURL = (isMaster ? null : json.getString("masterVersionFile"));
            modName = (isMaster ? null : json.optString("modName", "<unknown>"));
            modThreadId = (isMaster ? 0 : (int) json.optDouble("modThreadId", 0));

            // Parse version number
            JSONObject modVersion = json.getJSONObject("modVersion");
            major = modVersion.optInt("major", 0);
            minor = modVersion.optInt("minor", 0);
            patch = modVersion.optString("patch", "0");
        }

        boolean isOlderThan(VersionFile other)
        {
            if (other == null)
            {
                return false;
            }

            // DEBUG
            Global.getLogger(VersionChecker.class).log(Level.DEBUG,
                    modName + ": " + getVersion() + " vs " + other.getVersion());

            return (major < other.major)
                    || (major == other.major && minor < other.minor)
                    || (major == other.major && minor == other.minor
                    && patch.compareToIgnoreCase(other.patch) < 0);
        }

        boolean isNewerThan(VersionFile other)
        {
            return other.isOlderThan(this);
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
                return "v" + major + "." + minor;
            }

            // Support for character patch notation (v2.4b vs v2.4.1)
            if (isNumerical(patch))
            {
                return "v" + major + "." + minor + "." + patch;
            }
            else
            {
                return "v" + major + "." + minor + patch;
            }
        }

        String getMasterURL()
        {
            return masterURL;
        }

        String getThreadURL()
        {
            if (modThreadId == 0)
            {
                return null;
            }

            return String.format(MOD_THREAD_FORMAT, modThreadId);
        }

        @Override
        public String toString()
        {
            return getName() + " " + getVersion();
        }
    }
}
