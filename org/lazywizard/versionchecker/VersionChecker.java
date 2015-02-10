package org.lazywizard.versionchecker;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.versionchecker.UpdateInfo.ModInfo;
import org.lazywizard.versionchecker.UpdateInfo.VersionFile;
import org.lwjgl.opengl.Display;

final class VersionChecker
{
    private static final String VANILLA_UPDATE_URL
            = "https://dl.dropboxusercontent.com/u/32722116/Version%20Files/vanilla.txt";
    private static int MAX_THREADS = 12;

    static void setMaxThreads(int maxThreads)
    {
        MAX_THREADS = maxThreads;
    }

    private static JSONObject sanitizeJSON(final String rawJSON) throws JSONException
    {
        StringBuilder result = new StringBuilder(rawJSON.length());

        // Remove elements that default JSON implementation can't parse
        for (final String str : rawJSON.split("\n"))
        {
            // Strip out whole-line comments
            if (str.trim().startsWith("#"))
            {
                continue;
            }

            // Strip out end-line comments
            if (str.contains("#"))
            {
                result.append(str.substring(0, str.lastIndexOf('#')));
            }
            else
            {
                result.append(str);
            }
        }

        return new JSONObject(result.toString());
    }

    private static VersionFile getRemoteVersionFile(final String versionFileURL)
    {
        // No valid master version URL entry was found in the .version file
        if (versionFileURL == null)
        {
            return null;
        }

        // Don't allow local files outside of dev mode
        if (!Global.getSettings().isDevMode()
                && versionFileURL.trim().toLowerCase().startsWith("file:"))
        {
            Global.getLogger(VersionChecker.class).log(Level.ERROR,
                    "Local URLs are not allowed unless devmode is enabled: \""
                    + versionFileURL + "\"");
            return null;
        }

        Global.getLogger(VersionChecker.class).log(Level.INFO,
                "Loading version info from remote URL " + versionFileURL);

        // Load JSON from external URL and parse version info from it
        try (InputStream stream = new URL(versionFileURL).openStream();
                Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A"))
        {
            return new VersionFile(sanitizeJSON(scanner.next()), true);

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
                    "Failed to load master version file from URL \""
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

    private static String getLatestSSVersion()
    {
        Global.getLogger(VersionChecker.class).log(Level.INFO,
                "Loading starsector update info from remote URL " + VANILLA_UPDATE_URL);

        // Get latest Starsector version from remote URL
        try (InputStream stream = new URL(VANILLA_UPDATE_URL).openStream();
                Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A"))
        {
            return scanner.next();

        }
        catch (MalformedURLException ex)
        {
            Global.getLogger(VersionChecker.class).log(Level.ERROR,
                    "Invalid vanilla update URL \"" + VANILLA_UPDATE_URL + "\"", ex);
            return null;
        }
        catch (IOException ex)
        {
            Global.getLogger(VersionChecker.class).log(Level.ERROR,
                    "Failed to load vanilla update data from URL \""
                    + VANILLA_UPDATE_URL + "\"", ex);
            return null;
        }
    }

    private static ModInfo checkForUpdate(final VersionFile localVersion)
    {
        // Download the master version file for this mod
        VersionFile remoteVersion = getRemoteVersionFile(localVersion.getMasterURL());

        // Return null master if downloading/parsing the master file failed
        if (remoteVersion == null)
        {
            return new ModInfo(localVersion, null);
        }

        // Return a container for version files that lets us compare the two
        return new ModInfo(localVersion, remoteVersion);
    }

    static Future<UpdateInfo> scheduleUpdateCheck(final List<VersionFile> localVersions)
    {
        // Start another thread to handle the update checks and wait on the results
        FutureTask<UpdateInfo> task = new FutureTask<>(new MainTask(localVersions));
        Thread thread = new Thread(task, "Thread-VC-Main");
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    private static final class MainTask implements Callable<UpdateInfo>
    {
        private final List<VersionFile> localVersions;

        private MainTask(final List<VersionFile> localVersions)
        {
            this.localVersions = localVersions;
        }

        private int getNumberOfThreads()
        {
            return Math.max(1, Math.min(MAX_THREADS, localVersions.size()));
        }

        private CompletionService<ModInfo> createCompletionService()
        {
            // Create thread pool and executor
            ExecutorService serviceInternal = Executors.newFixedThreadPool(
                    getNumberOfThreads(), new VCThreadFactory());
            CompletionService<ModInfo> service = new ExecutorCompletionService<>(serviceInternal);

            // Register update checks with thread executor
            for (final VersionFile version : localVersions)
            {
                service.submit(new SubTask(version));
            }

            return service;
        }

        @Override
        public UpdateInfo call() throws InterruptedException, ExecutionException
        {
            Global.getLogger(VersionChecker.class).log(Level.INFO,
                    "Starting update checks");
            final long startTime = System.nanoTime();

            // Check for updates in separate threads for faster execution
            CompletionService<ModInfo> service = createCompletionService();
            final UpdateInfo results = new UpdateInfo();

            // Poll for SS update, can block if site is down
            if (VCModPlugin.checkSSVersion)
            {
                final String currentVanilla = Display.getTitle(),
                        latestVanilla = getLatestSSVersion(); // Throws esceptions
                Global.getLogger(VersionChecker.class).log(Level.INFO,
                        "Local Starsector version is " + currentVanilla
                        + ", latest known is " + latestVanilla);
                if (latestVanilla != null && !latestVanilla.equalsIgnoreCase(currentVanilla))
                {
                    Global.getLogger(VersionChecker.class).log(Level.INFO,
                            "Starsector update available");
                    results.ssUpdate = latestVanilla;
                }
            }

            // Poll for results from the other threads until all have finished
            int modsToCheck = localVersions.size();
            while (modsToCheck > 0)
            {
                ModInfo tmp = service.take().get(); // Throws exceptions
                modsToCheck--;

                // Update check failed for some reason
                if (tmp.failedUpdateCheck())
                {
                    results.addFailed(new ModInfo(tmp.getLocalVersion(), null));
                }
                // Remote version is newer than local
                else if (tmp.isUpdateAvailable())
                {
                    results.addUpdate(tmp);
                }
                // Remote version is older/same as local
                else
                {
                    results.addNoUpdate(tmp);
                }
            }

            // Report how long the check took
            final String elapsedTime = DecimalFormat.getNumberInstance().format(
                    (System.nanoTime() - startTime) / 1000000000.0d);
            Global.getLogger(VersionChecker.class).log(Level.INFO,
                    "Checked game and " + results.getNumModsChecked()
                    + " mods in " + elapsedTime + " seconds");
            return results;
        }

        private static class SubTask implements Callable<ModInfo>
        {
            final VersionFile version;

            private SubTask(VersionFile version)
            {
                this.version = version;
            }

            @Override
            public ModInfo call() throws Exception
            {
                return checkForUpdate(version);
            }
        }
    }

    private static final class VCThreadFactory implements ThreadFactory
    {
        private int threadNum = 0;

        @Override
        public Thread newThread(Runnable r)
        {
            threadNum++;
            Thread thread = new Thread(r, "Thread-VC-" + threadNum);
            thread.setDaemon(true);
            thread.setPriority(3);
            return thread;
        }
    }

    private VersionChecker()
    {
    }
}
