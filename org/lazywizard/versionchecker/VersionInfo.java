package org.lazywizard.versionchecker;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;

class VersionInfo
{
    final int major, minor, patch;
    final String masterURL, name, gameVersion; // Unused for now

    VersionInfo(final JSONObject versionFile) throws JSONException
    {
        // Parse version file
        masterURL = versionFile.getString("masterVersionFile");
        name = versionFile.optString("modName", "<unknown>");
        gameVersion = versionFile.optString("starsectorVersion", "");
        JSONObject modVersion = versionFile.getJSONObject("modVersion");
        major = modVersion.optInt("major", 0);
        minor = modVersion.optInt("minor", 0);
        patch = modVersion.optInt("patch", 0);
    }

    boolean isOlderThan(VersionInfo other)
    {
        // DEBUG
        //Global.getLogger(VersionInfo.class).log(Level.DEBUG,
        System.out.println(
                name + " " + getVersion() + " vs " + other.getVersion());

        return (major < other.major)
                || (major == other.major && minor < other.minor)
                || (major == other.major && minor == other.minor && patch < other.patch);
    }

    public String getVersion()
    {
        return "v" + major + "." + minor + "." + patch;
    }

    @Override
    public String toString()
    {
        return name + " " + getVersion();
    }
}
