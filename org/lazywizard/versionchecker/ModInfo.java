package org.lazywizard.versionchecker;

class ModInfo
{
    private final VersionInfo oldVersion, newVersion;

    ModInfo(VersionInfo oldVersion, VersionInfo newVersion)
    {
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
    }

    boolean isUpdateAvailable()
    {
        return oldVersion.isOlderThan(newVersion);
    }

    @Override
    public String toString()
    {
        return oldVersion.modName + " (" + oldVersion.getVersion() + " => "
                + newVersion.getVersion() + ")";
    }
}
