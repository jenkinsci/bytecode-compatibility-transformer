package org.jenkinsci.bytecode.helper;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper utility to direct logging to either System.out or the given Logger (if enabled).
 * We need this intermediate as logging can cause classloading and also involves locks, and the transformer is only ever
 * invoked during class loading - so we should not really use logging as we could end up in a deadlock (however there
 * are times where we really want to use it)
 */
public final class LoggingHelper {

    /**
     * Flag to allow the use if JUL (useful sometimes to get logging in a single place, but can cause deadlocks!)
     */
    public static final boolean JUL_ENABLED = Boolean.getBoolean(LoggingHelper.class.getName() + ".enableJUL");

    /** 
     * Logs to JUL if {@link #JUL_ENABLED} is true, otherwise prints the message to System.out 
     * @param log the Logger to use if JUL is enabled (and whose level will be checked to see if we should log at all
     * @param level One of the message level identifiers, e.g., SEVERE
     * @param supplier A function, which when called, produces the desired log message
     */
    public static void conditionallyLog(Logger log, Level level, Supplier<String> supplier) {
        if (log.isLoggable(level)) {
            if (JUL_ENABLED) {
                log.log(level, supplier);
            } else {
                System.out.println(log.getName() + " [" + level.getName() + "]" + supplier.get());
            }
        }
    }

    /** 
     * Logs to JUL if {@link #JUL_ENABLED} is true, otherwise prints the message to System.out 
     * @param log the Logger to use if JUL is enabled (and whose level will be checked to see if we should log at all
     * @param level One of the message level identifiers, e.g., SEVERE
     * @param supplier A function, which when called, produces the desired log message
     * @param t Throwable to associated with log message.
     */
    public static void conditionallyLog(Logger log, Level level, Supplier<String> supplier, Throwable t) {
        if (log.isLoggable(level)) {
            if (JUL_ENABLED) {
                log.log(level, t, supplier);
            } else {
                System.out.println(log.getName() + " [" + level.getName() + "]" + supplier.get());
                t.printStackTrace(System.out);
            }
        }
    }

}
