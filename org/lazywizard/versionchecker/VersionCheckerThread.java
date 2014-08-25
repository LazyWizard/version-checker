package org.lazywizard.versionchecker;

import java.util.Set;
import java.util.concurrent.ExecutorService;

class VersionCheckerThread
{
    private static Set<VersionInfo> versionFiles;
    private static ExecutorService thread;
    private static boolean isActive = false, isFinished = false;

    static boolean isFinished()
    {
        return isFinished;
    }

    void addModToBeChecked(VersionInfo version)
    {
        
    }
}