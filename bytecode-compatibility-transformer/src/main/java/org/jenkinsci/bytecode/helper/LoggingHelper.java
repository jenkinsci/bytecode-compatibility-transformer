package org.jenkinsci.bytecode.helper;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Helper utility to allow asynchronous logging to prevent the introduction of deadlocks.
 * We need this intermediate as logging can cause classloading and also involves locks, and the transformer is only ever
 * invoked during class loading - so we should not really use logging as we could end up in a deadlock (however there
 * are times where we really want to use it)
 */
public final class LoggingHelper {

    /** 
     * Logs to JUL if the records log level is loggable for the given logger. 
     * @param log the Logger to use.
     * @param record the record to log
     */
    public static void asyncLog(final Logger log, final LogRecord record) {
        if (log.isLoggable(record.getLevel())) {
            populateStackAndLogAsync(log, record);
        }
    }

    /** 
     * Logs to JUL if the log level is loggable for the given logger, based on the given message and parameters 
     * @param log the Logger to use.
     * @param level the level of the log entry
     * @param message the message (with format placeholders) for the log entry
     * @param parameters optional parameters to go with the message
     */
    public static void asyncLog(final Logger log, final Level level, final String message, final Object...parameters) {
        if (log.isLoggable(level)) {
            LogRecord record = new LogRecord(level, message);
            record.setParameters(parameters);
            populateStackAndLogAsync(log, record);
        }
    }

    /** 
     * Logs to JUL if the log level is loggable for the given logger, based on the given message and parameters 
     * @param log the Logger to use.
     * @param level the level of the log entry
     * @param throwable the throwable to associate with the log entry
     * @param message the message (with format placeholders) for the log entry
     * @param parameters optional parameters to go with the message
     */
    public static void asyncLog(final Logger log, final Level level, final Throwable throwable, final String message, final Object...parameters) {
        if (log.isLoggable(level)) {
            LogRecord record = new LogRecord(level, message);
            record.setThrown(throwable);
            record.setParameters(parameters);
            populateStackAndLogAsync(log, record);
        }
    }

    private static void populateStackAndLogAsync(final Logger log, final LogRecord record) {
        // call to make sure the stack is populated.
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length > 3) {
            // [0] is Thread.getStackTrace
            // [1] is this method
            // [2] is the caller (still this class)
            // [3] is the actual consumer we want
            StackTraceElement caller = stack[3];
            record.setSourceClassName(caller.getClassName());
            record.setSourceMethodName(caller.getMethodName());
        }
        new Thread(() -> log.log(record), "bytecode-compatibility-transformer async logging").start();
    }
}
