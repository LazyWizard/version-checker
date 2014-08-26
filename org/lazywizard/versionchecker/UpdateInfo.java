package org.lazywizard.versionchecker;

import java.util.ArrayList;
import java.util.List;

class UpdateInfo
{
    final List<ModInfo> hasUpdates = new ArrayList<>();
    final List<VersionInfo> failedCheck = new ArrayList<>();

    void addFailed(VersionInfo version)
    {
        failedCheck.add(version);
    }

    void addUpdate(ModInfo mod)
    {
        hasUpdates.add(mod);
    }
}
