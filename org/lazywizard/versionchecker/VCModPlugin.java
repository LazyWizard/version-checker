package org.lazywizard.versionchecker;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.versionchecker.UpdateInfo.VersionFile;

public final class VCModPlugin extends BaseModPlugin
{
    private static final String SETTINGS_FILE = "data/config/version/version_checker.json";
    private static final String CSV_PATH = "data/config/version/version_files.csv";
    private static UpdateNotificationScript script = null;
    static boolean checkSSVersion = false;
    static int notificationKey;

    @Override
    // Note: if there's any significant change to how this function works,
    // the RecheckVersions console command will need to be updated as well
    public void onApplicationLoad() throws Exception
    {
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);
        notificationKey = settings.getInt("summonUpdateNotificationKey");
        checkSSVersion = settings.getBoolean("checkStarsectorVersion");
        VersionChecker.setMaxThreads(settings.getInt("maxUpdateThreads"));
        Log.setLevel(Level.toLevel(settings.getString("logLevel"), Level.WARN));

        final List<VersionFile> versionFiles = new ArrayList<>();
        final JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "version file", CSV_PATH, "lw_version_checker");

        final int numMods = csv.length(),
                csvPathLength = CSV_PATH.length() + 1;
        final List<String> modPaths = new ArrayList<>(numMods);
        Log.info("Found " + numMods + " mods with version info");
        for (int x = 0; x < numMods; x++)
        {
            final JSONObject row = csv.getJSONObject(x);
            final String versionFile = row.getString("version file");
            final String source = row.optString("fs_rowSource", null);
            if (source != null && source.length() > csvPathLength)
            {
                modPaths.add(source.substring(0, source.length() - csvPathLength));
            }

            try
            {
                versionFiles.add(new VersionFile(
                        Global.getSettings().loadJSON(versionFile), false));
            }
            catch (JSONException ex)
            {
                throw new RuntimeException("Failed to parse version file \""
                        + versionFile + "\"", ex);
            }
        }

        final List<ModSpecAPI> unsupportedMods = new ArrayList<>();
        for (ModSpecAPI mod : Global.getSettings().getModManager().getEnabledModsCopy())
        {
            if (!modPaths.contains(mod.getPath()))
            {
                unsupportedMods.add(mod);
            }
        }

        if (!versionFiles.isEmpty())
        {
            script = new UpdateNotificationScript(unsupportedMods,
                    VersionChecker.scheduleUpdateCheck(versionFiles));
        }
    }

    @Override
    public void onGameLoad(boolean newGame)
    {
        if (script != null && !script.isDone())
        {
            Global.getSector().addTransientScript(script);
        }
    }
}
