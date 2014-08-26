package org.lazywizard.versionchecker;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;

class VersionInfo
{
    final int major, minor, patch;
    final String masterURL, modName, gameVersion;

    VersionInfo(final JSONObject versionFile) throws JSONException
    {
        // Parse mod details
        masterURL = versionFile.getString("masterVersionFile");
        modName = versionFile.optString("modName", "<unknown>");
        gameVersion = versionFile.optString("starsectorVersion", "");

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

    public String getVersion()
    {
        return major + "." + minor + "." + patch;
    }

    @Override
    public String toString()
    {
        return modName + " v" + getVersion();
    }
}
