package org.lazywizard.versionchecker;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.versionchecker.UpdateInfo.ModInfo;
import org.lazywizard.versionchecker.UpdateInfo.VersionInfo;

final class VersionChecker
{
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
        // No master version URL entry was found in the .version file
        if (versionFileURL == null)
        {
            return null;
        }

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
            Global.getLogger(VersionChecker.class).log(Level.ERROR,
                    "Invalid master version file URL \""
                    + versionFileURL + "\"", ex);
            return null;
        }
        catch (IOException ex)
        {
            Global.getLogger(VersionChecker.class).log(Level.ERROR,
                    "Failed to load master version file at URL \""
                    + versionFileURL + "\"", ex);
            return null;
        }
        catch (JSONException ex)
        {
            Global.getLogger(VersionChecker.class).log(Level.ERROR,
                    "Malformed JSON in remote version file at URL \""
                    + versionFileURL + "\"", ex);
            return null;
        }
    }

    private static ModInfo checkForUpdate(final VersionInfo localVersion)
    {
        // Download the master version file for this mod
        String masterURL = localVersion.getMasterURL();
        JSONObject remoteVersionFile = getRemoteVersionFile(masterURL);
        if (remoteVersionFile == null)
        {
            return null;
        }

        // Parse remote version information
        VersionInfo remoteVersion;
        try
        {
            remoteVersion = new VersionInfo(remoteVersionFile, true);
        }
        catch (JSONException ex)
        {
            Global.getLogger(VersionChecker.class).log(Level.ERROR,
                    "Failed to parse remote version info:", ex);
            return null;
        }

        return new ModInfo(localVersion, remoteVersion);
    }

    static Future<UpdateInfo> scheduleUpdateCheck(final List<VersionInfo> localVersions)
    {
        ExecutorService service = Executors.newSingleThreadExecutor();
        Future<UpdateInfo> result = service.submit(
                new VersionCheckerCallable(localVersions));
        service.shutdown();
        return result;
    }

    private static class VersionCheckerCallable implements Callable<UpdateInfo>
    {
        private final List<VersionInfo> localVersions;

        private VersionCheckerCallable(final List<VersionInfo> localVersions)
        {
            this.localVersions = localVersions;
        }

        @Override
        public UpdateInfo call() throws Exception
        {
            final long startTime = System.nanoTime();
            final UpdateInfo results = new UpdateInfo();
            for (VersionInfo version : localVersions)
            {
                ModInfo tmp = checkForUpdate(version);

                if (tmp == null)
                {
                    results.addFailed(version);
                }
                else if (tmp.isUpdateAvailable())
                {
                    results.addUpdate(tmp);
                }
                else
                {
                    results.addNoUpdate(tmp);
                }
            }

            final String elapsedTime = DecimalFormat.getNumberInstance().format(
                    (System.nanoTime() - startTime) / 1000000000.0d);
            Global.getLogger(VersionChecker.class).log(Level.INFO,
                    "Checked " + results.getNumModsChecked() + " mods in "
                    + elapsedTime + " seconds.");
            return results;
        }
    }

    private VersionChecker()
    {
    }
}
