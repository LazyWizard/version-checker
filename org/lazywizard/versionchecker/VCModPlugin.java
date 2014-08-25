package org.lazywizard.versionchecker;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
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
    private static boolean shouldWarn = false;
    private static Set<String> modsWithUpdates;

    @Override
    public void onApplicationLoad() throws Exception
    {
        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "version file", CSV_PATH, "lw_version_checker");

        int numMods = csv.length();
        Global.getLogger(VCModPlugin.class).log(Level.INFO,
                "Found " + numMods + " mods with version info");
        for (int x = 0; x < numMods; x++)
        {
            JSONObject row = csv.getJSONObject(x);
            String versionFile = row.getString("version file");

            try
            {

                String update = VersionChecker.checkForUpdate(
                        Global.getSettings().loadJSON(versionFile));

                if (update != null)
                {
                    if (modsWithUpdates == null)
                    {
                        modsWithUpdates = new HashSet<>();
                        shouldWarn = true;
                    }

                    modsWithUpdates.add(update);
                }
            }
            catch (JSONException ex)
            {
                Global.getLogger(VCModPlugin.class).log(Level.ERROR,
                        "Failed to parse version file \"" + versionFile + "\":", ex);
            }
        }
    }

    @Override
    public void onGameLoad()
    {
        if (shouldWarn)
        {
            if (modsWithUpdates == null || modsWithUpdates.isEmpty())
            {
                Global.getSector().getCampaignUI().addMessage(
                        "All mods are up to date.", Color.GREEN);
            }
            else
            {
                Global.getSector().getCampaignUI().addMessage(
                        "Updates found for mods: ", Color.YELLOW);
                for (String tmp : modsWithUpdates)
                {
                    Global.getSector().getCampaignUI().addMessage(
                            " - " + tmp, Color.YELLOW);
                }
            }

            shouldWarn = false;
            modsWithUpdates = null;
        }
    }
}
