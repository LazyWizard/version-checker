package org.lazywizard.versionchecker;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

// Minor utility class to ensure all logging is done under the same class
class Log
{
    private static final Logger Log = Logger.getLogger(VersionChecker.class);

    static void setLevel(Level level)
    {
        Log.setLevel(level);
    }

    static void info(Object message)
    {
        Log.info(message);
    }

    static void info(Object message, Throwable ex)
    {
        Log.info(message, ex);
    }

    static void debug(Object message)
    {
        Log.debug(message);
    }

    static void debug(Object message, Throwable ex)
    {
        Log.debug(message, ex);
    }

    static void warn(Object message)
    {
        Log.warn(message);
    }

    static void warn(Object message, Throwable ex)
    {
        Log.warn(message, ex);
    }

    static void error(Object message)
    {
        Log.error(message);
    }

    static void error(Object message, Throwable ex)
    {
        Log.error(message, ex);
    }

    static void fatal(Object message)
    {
        Log.fatal(message);
    }

    static void fatal(Object message, Throwable ex)
    {
        Log.fatal(message, ex);
    }

    private Log()
    {
    }
}
