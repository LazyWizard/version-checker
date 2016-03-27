package org.lazywizard.versionchecker;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

final class UpdateInfo
{
    private final List<ModInfo> hasUpdate = new ArrayList<>();
    private final List<ModInfo> hasNoUpdate = new ArrayList<>();
    private final List<ModInfo> failedCheck = new ArrayList<>();
    private int numModsChecked = 0;
    private String ssUpdate = null, ssUpdateError = null;

    void setSSUpdate(String latestVersion)
    {
        this.ssUpdate = latestVersion;
    }

    String getSSUpdate()
    {
        return ssUpdate;
    }

    void setFailedSSError(String ssUpdateError)
    {
        this.ssUpdateError = ssUpdateError;
    }

    String getFailedSSError()
    {
        return ssUpdateError;
    }

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
        private final String errorMessage;

        ModInfo(VersionFile localVersion, VersionFile remoteVersion)
        {
            this.localVersion = localVersion;
            this.remoteVersion = remoteVersion;
            errorMessage = null;
        }

        ModInfo(VersionFile localVersion, String errorMessage)
        {
            this.localVersion = localVersion;
            this.errorMessage = errorMessage;
            remoteVersion = null;
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
            return (remoteVersion == null);
        }

        String getErrorMessage()
        {
            return errorMessage;
        }

        boolean isUpdateAvailable()
        {
            return localVersion.isOlderThan(remoteVersion);
        }

        boolean isLocalNewer()
        {
            return localVersion.isNewerThan(remoteVersion);
        }

        String getVersionString()
        {
            if (remoteVersion == null || localVersion.isSameAs(remoteVersion))
            {
                return localVersion.getVersion();
            }

            return localVersion.getVersion() + " vs " + remoteVersion.getVersion();
        }

        @Override
        public int compareTo(ModInfo other)
        {
            return localVersion.getName().compareTo(other.localVersion.getName());
        }
    }

    static final class VersionFile implements Comparable<VersionFile>
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

        boolean isSameAs(VersionFile other)
        {
            return (compareTo(other) == 0);
        }

        boolean isOlderThan(VersionFile other)
        {
            return (compareTo(other) < 0);
        }

        boolean isNewerThan(VersionFile other)
        {
            return (compareTo(other) > 0);
        }

        String getName()
        {
            return modName;
        }

        private static boolean startsWithDigit(String str)
        {
            return (!str.isEmpty() && Character.isDigit(str.charAt(0)));
        }

        String getVersion()
        {
            // Don't show patch number if there isn't one
            if (patch.equals("0"))
            {
                return "v" + major + "." + minor;
            }

            // Support for character patch notation (v2.4b vs v2.4.1)
            if (startsWithDigit(patch))
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

        private static String[] splitPatch(String patch)
        {
            String digit = "", str = "";
            for (int i = 0; i < patch.length(); i++)
            {
                final char ch = patch.charAt(i);
                if (Character.isDigit(ch))
                {
                    digit += ch;
                }
                else
                {
                    str = patch.substring(i);
                    break;
                }
            }

            //System.out.println(digit + " | " + str);
            return new String[]
            {
                digit, str
            };
        }

        private static int comparePatch(String patch, String other)
        {
            // Compare digits as digits, so v11 is considered newer than v9
            if (startsWithDigit(patch) && startsWithDigit(other))
            {
                final String[] subPatch = splitPatch(patch),
                        subOther = splitPatch(other);
                final int numPatch = Integer.parseInt(subPatch[0]),
                        numOther = Integer.parseInt(subOther[0]);

                // If digits are the same, compare any remaining characters
                if (numPatch == numOther)
                {
                    return subPatch[1].compareTo(subOther[1]);
                }

                return Integer.compare(numPatch, numOther);
            }

            return patch.compareToIgnoreCase(other);
        }

        @Override
        public int compareTo(VersionFile other)
        {
            if (other == null)
            {
                // TODO: Remove this before releasing! Only here because I honestly can't remember when other will be null
                throw new RuntimeException("Other was null for " + getName());
            }

            if (major == other.major && minor == other.minor
                    && patch.equalsIgnoreCase(other.patch))
            {
                return 0;
            }

            if ((major < other.major) || (major == other.major && minor < other.minor)
                    || (major == other.major && minor == other.minor
                    && comparePatch(patch, other.patch) < 0))
            {
                return -1;
            }

            return 1;
        }

        /*private static void compare(String local, String remote)
        {
            final int comparison = comparePatch(local, remote);
            final String result = (comparison == 0 ? " equals "
                    : (comparison > 0 ? " is newer than " : " is older than "));
            System.out.println(local + result + remote);
        }

        public static void main(String[] args)
        {
            final String[] versions = new String[]
            {
                "11a-rc1", "11a-rc2",
                "9", "11",
                "1.2.3.4","4.3.2.1",
                "1234","4321",
                "a1","b1",
                "a1","a1a",
                "a1","a2",
                "5a1","5a2",
                "5a1","5b1",
                "9a","11a",
                "11a","11b"
            };

            for (int i = 0; i < versions.length; i += 2)
            {
                compare(versions[i], versions[i + 1]);
                compare(versions[i + 1], versions[i]);
            }
        }*/
    }
}
