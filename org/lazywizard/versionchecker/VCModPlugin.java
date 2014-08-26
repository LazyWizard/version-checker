package org.lazywizard.versionchecker;

import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// TODO: Retrieve remote version info in a separate thread
public class VCModPlugin extends BaseModPlugin
{
    private static final String CSV_PATH = "data/config/version/version_files.csv";
    private static UpdateNotificationScript script = null;

    @Override
    public void onApplicationLoad() throws Exception
    {
        Global.getLogger(VersionChecker.class).setLevel(Level.WARN);

        List<VersionInfo> versionFiles = new ArrayList<>();
        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "version file", CSV_PATH, "lw_version_checker");

        int numMods = csv.length();
        Global.getLogger(VersionChecker.class).log(Level.INFO,
                "Found " + numMods + " mods with version info");
        for (int x = 0; x < numMods; x++)
        {
            JSONObject row = csv.getJSONObject(x);
            String versionFile = row.getString("version file");

            try
            {
                versionFiles.add(new VersionInfo(Global.getSettings().loadJSON(versionFile)));
            }
            catch (JSONException ex)
            {
                Global.getLogger(VersionChecker.class).log(Level.ERROR,
                        "Failed to parse version file \"" + versionFile + "\":", ex);
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
