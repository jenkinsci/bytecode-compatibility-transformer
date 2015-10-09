import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.AntClassLoader;
import org.jenkinsci.bytecode.Transformer;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class CompatibilityTest {
    @Test
    public void v1() throws Exception {
        verify(createClassLoader("v1"));
    }

    @Test
    public void v2() throws Exception {
        AntClassLoader cl = createClassLoader("v2");
        verify(cl);

        Class<?> i = cl.loadClass("Foo$Inner");
        assertFieldType(i, ArrayList.class, "x");
        assertFieldType(i, String[].class, "y");
        assertFieldType(i, Object.class, "z");
    }
    
    @Test
    @Ignore("TODO JENKINS-28799")
    public void testClassesAreOnlyReWrittenWhenNeeded() throws Exception {
        System.out.println("rewrtting tests");
        final Transformer t = new Transformer();
        AntClassLoader cl = new AntClassLoader() {
            @Override
            protected Class<?> defineClassFromData(File container, byte[] classData, String classname) throws IOException {
                byte[] rewritten = t.transform(classname, classData);
                if (rewritten!=classData ) {
                    fail(classname + " was rewritten without need");
                }
                return super.defineClassFromData(container, rewritten, classname);
            }
        };

        cl.addPathComponent(new File("target/test-classes/v2"));
        cl.addPathComponent(new File("target/test-classes/client"));
        cl.addPathComponent(new File("target/lib/ivy.jar"));

        t.loadRules(cl);

        cl.loadClass("org.apache.ivy.core.settings.IvySettings");
        System.out.println("rewrtting tests - done");
    }


    private void assertFieldType(Class<?> i, Class<?> type, String name) throws NoSuchFieldException {
        assertSame(type, i.getDeclaredField(name).getType());
    }

    private AntClassLoader createClassLoader(String v) throws IOException {
        final Transformer t = new Transformer();
        AntClassLoader cl = new AntClassLoader() {
            final File outputDir = new File("target/modified-classes");
            @Override
            protected Class<?> defineClassFromData(File container, byte[] classData, String classname) throws IOException {
                byte[] rewritten = t.transform(classname, classData);
                if (rewritten!=classData) {
                    File dst = new File(outputDir,classname.replace('.','/')+".class");
                    dst.getParentFile().mkdirs();
                    FileUtils.writeByteArrayToFile(dst,rewritten);
                    System.out.println("Modified "+classname);
                }
                return super.defineClassFromData(container, rewritten, classname);
            }
        };

        cl.addPathComponent(new File("target/test-classes/"+ v));
        cl.addPathComponent(new File("target/test-classes/client"));
        cl.addPathComponent(new File("target/lib/ivy.jar"));

        t.loadRules(cl);

        return cl;
    }

    private void verify(ClassLoader cl) throws Exception {
        TestSuite ts = new TestSuite(cl.loadClass("Main"));
        TestResult tr = new TestRunner().doRun(ts,false);

        if (tr.errorCount() + tr.failureCount()>0)
            throw new Error("test failures");
    }

}
