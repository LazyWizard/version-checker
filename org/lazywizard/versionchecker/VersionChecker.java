package org.lazywizard.versionchecker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;

class VersionChecker
{
    public static void main(String[] args)
    {
        org.apache.log4j.BasicConfigurator.configure();

        try (Scanner scanner = new Scanner(new File(
                "C:\\Users\\Rob\\Desktop\\Starfarer Mods\\"
                + "VersionChecker\\src\\versionchecker.version"), "UTF-8")
                .useDelimiter("\\A"))
        {
            Global.getLogger(VersionChecker.class).log(Level.DEBUG,
                    checkForUpdate(new VersionInfo(new JSONObject(
                            sanitizeJSON(scanner.next())))).toString());
        }
        catch (JSONException | IOException ex)
        {
            Global.getLogger(VersionChecker.class).log(Level.ERROR, ex);
        }
    }

    private static String sanitizeJSON(final String rawJSON)
    {
        StringBuilder result = new StringBuilder(rawJSON.length());

        // Remove elements that default JSON implementation can't parse
        for (final String str : rawJSON.split("\n"))
        {
            if (str.trim().startsWith("#"))
            {
                continue;
            }

            // TODO: Detect when within quotation marks (# would be valid then)
            if (str.contains("#"))
            {
                result.append(str.substring(0, str.lastIndexOf('#')));
            }
            else
            {
                result.append(str);
            }
        }

        return result.toString();
    }

    private static JSONObject getRemoteVersionFile(final String versionFileURL)
    {
        Global.getLogger(VersionChecker.class).log(Level.INFO,
                "Loading version info from remote URL " + versionFileURL);

        // Load JSON from external URL
        try (InputStream stream = new URL(versionFileURL).openStream();
                Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A"))
        {
            String rawText = scanner.next();
            return new JSONObject(sanitizeJSON(rawText));
        }
        catch (MalformedURLException ex)
        {
            Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                    "Invalid master version file URL \""
                    + versionFileURL + "\"", ex);
            return null;
        }
        catch (IOException ex)
        {
            Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                    "Failed to load master version file at URL \""
                    + versionFileURL + "\"", ex);
            return null;
        }
        catch (JSONException ex)
        {
            Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                    "Malformed JSON in remote version file at URL \""
                    + versionFileURL + "\"", ex);
            return null;
        }
    }

    static UpdateInfo checkForUpdate(final VersionInfo localVersion)
    {
        // Download the master version file for this mod
        JSONObject remoteVersionFile = getRemoteVersionFile(localVersion.masterURL);
        if (remoteVersionFile == null)
        {
            return null;
        }

        // Parse remote version information
        VersionInfo remoteVersion;
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

        return new UpdateInfo(localVersion, remoteVersion);
    }
}
