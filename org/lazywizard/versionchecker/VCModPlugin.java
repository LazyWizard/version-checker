package org.lazywizard.versionchecker;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.versionchecker.UpdateInfo.VersionInfo;

public final class VCModPlugin extends BaseModPlugin
{
    private static final String SETTINGS_FILE = "data/config/version/version_checker.json";
    private static final String CSV_PATH = "data/config/version/version_files.csv";
    private static UpdateNotificationScript script = null;

    @Override
    public void onApplicationLoad() throws Exception
    {
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);
        VersionChecker.setMaxThreads(settings.optInt("maxThreads", 5));
        Global.getLogger(VersionChecker.class).setLevel(
                Level.toLevel(settings.optString("logLevel", "WARN")));

        final List<VersionInfo> versionFiles = new ArrayList<>();
        final JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "version file", CSV_PATH, "lw_version_checker");

        final int numMods = csv.length();
        Global.getLogger(VersionChecker.class).log(Level.INFO,
                "Found " + numMods + " mods with version info");
        for (int x = 0; x < numMods; x++)
        {
            JSONObject row = csv.getJSONObject(x);
            String versionFile = row.getString("version file");

            try
            {
                versionFiles.add(new VersionInfo(
                        Global.getSettings().loadJSON(versionFile), false));
            }
            catch (JSONException ex)
            {
                throw new RuntimeException("Failed to parse version file \""
                        + versionFile + "\"", ex);
            }
        }

        if (!versionFiles.isEmpty())
        {
            script = new UpdateNotificationScript(
                    VersionChecker.scheduleUpdateCheck(versionFiles));
        }
    }

    @Override
    public void onGameLoad()
    {
        if (script != null)
        {
            Global.getSector().addScript(script);
            script = null;
        }
    }
}
