package org.lazywizard.versionchecker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
import java.util.Set;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;

class VersionChecker
{
    private static Set<JSONObject> versionFiles;

    public static void main(String[] args)
    {
        try (Scanner scanner = new Scanner(new File(
                "C:\\Users\\Rob\\Desktop\\Starfarer Mods\\"
                + "VersionChecker\\src\\versionchecker.version"))
                .useDelimiter("\\A"))
        {
            checkForUpdate(new JSONObject(sanitizeJSON(scanner.next())));
        }
        catch (JSONException | IOException ex)
        {
            ex.printStackTrace();
        }
    }

    static String sanitizeJSON(final String rawJSON)
    {
        StringBuilder result = new StringBuilder(rawJSON.length());

        // Exclude comments from JSON
        for (final String str : rawJSON.split("\n"))
        {
            if (str.trim().startsWith("#"))
            {
                continue;
            }

            if (str.contains("#"))
            {
                result.append(str.substring(0, str.lastIndexOf("#")));
            }
            else
            {
                result.append(str);
            }
        }

        return result.toString();
    }

    private static JSONObject getRemoteVersionFile(final String versionFileURL)
            throws JSONException, MalformedURLException, IOException
    {
        // Load JSON from external URL
        try (InputStream stream = new URL(versionFileURL).openStream();
                Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A"))
        {
            String rawText = scanner.next();
            return new JSONObject(sanitizeJSON(rawText));
        }
    }

    static String checkForUpdate(final JSONObject localVersionFile) throws JSONException
    {
        String versionFileURL = localVersionFile.getString("masterVersionFile");
        JSONObject remoteVersionFile;

        // Download the master version file for this mod
        try
        {
            remoteVersionFile = getRemoteVersionFile(versionFileURL);
        }
        catch (MalformedURLException ex)
        {
            Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                    "Invalid master version file URL \"" + versionFileURL
                    + "\"", ex);
            return null;
        }
        catch (IOException ex)
        {
            Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                    "Failed to load master version file at URL \"" + versionFileURL
                    + "\"", ex);
            return null;
        }
        catch (JSONException ex)
        {
            Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                    "Malformed JSON in remote version file at URL \"" + versionFileURL
                    + "\"", ex);
            return null;
        }

        VersionInfo localVersion, remoteVersion;

        // Parse local version information
        try
        {
            localVersion = new VersionInfo(localVersionFile);
        }
        catch (JSONException ex)
        {
            Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                    "Failed to parse local version info:", ex);
            return null;
        }

        // Parse remote version information
        try
        {
            remoteVersion = new VersionInfo(remoteVersionFile);
        }
        catch (JSONException ex)
        {
            Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                    "Failed to parse remote version info:", ex);
            return null;
        }

        // Check if there is a newer mod version available
        if (localVersion.isOlderThan(remoteVersion))
        {
            return localVersion.name + " (" + localVersion.getVersion() + " => "
                    + remoteVersion.getVersion() + ")";
        }

        return null;
    }
}
