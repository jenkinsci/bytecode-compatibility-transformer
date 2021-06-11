package org.jenkinsci.bytecode.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.CoreMatchers.is;

public class LoggingHelperTest {

    @Test
    public void testSourceCorrectlySetWhenLoggingARecord() throws Exception {
        CapturingLogHandler h = new CapturingLogHandler();
        Logger logger = Logger.getLogger("test-logger");
        logger.addHandler(h);
        
        LogRecord lr = new LogRecord(Level.SEVERE, "this is a message");
        LoggingHelper.asyncLog(logger, lr);
        while (h.records.size() < 1) {
            Thread.sleep(50L);
        }
        assertEquals(h.records.size(), 1);
        assertThat(lr.getSourceClassName(), is("org.jenkinsci.bytecode.helper.LoggingHelperTest"));
        assertThat(lr.getSourceMethodName(), is("testSourceCorrectlySetWhenLoggingARecord"));
    }

    @Test
    public void testSourceCorrectlySetWhenLoggingAMessage() throws Exception {
        CapturingLogHandler h = new CapturingLogHandler();
        Logger logger = Logger.getLogger("test-logger");
        logger.addHandler(h);
        
        LoggingHelper.asyncLog(logger, Level.INFO, new Throwable(), "this is a message");
        while (h.records.size() < 1) {
            Thread.sleep(50L);
        }
        assertEquals(h.records.size(), 1);
        assertThat(h.records.get(0).getSourceClassName(), is("org.jenkinsci.bytecode.helper.LoggingHelperTest"));
        assertThat(h.records.get(0).getSourceMethodName(), is("testSourceCorrectlySetWhenLoggingAMessage"));
    }


    private static class CapturingLogHandler extends Handler {

        private List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() throws SecurityException {
            // no-op
        }
    }
}
