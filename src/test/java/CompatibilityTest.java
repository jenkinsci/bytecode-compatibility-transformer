import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.AntClassLoader;
import org.jenkinsci.bytecode.Transformer;
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

    private void assertFieldType(Class<?> i, Class<?> type, String name) throws NoSuchFieldException {
        assertSame(type, i.getDeclaredField(name).getType());
    }

    private AntClassLoader createClassLoader(String v) throws IOException {
        File f1 = new File("target/test-classes/"+ v);
        File f2 = new File("target/test-classes/client");
        final Transformer t = new Transformer();
        AntClassLoader cl = new AntClassLoader() {
            @Override
            protected Class<?> defineClassFromData(File container, byte[] classData, String classname) throws IOException {
                byte[] rewritten = t.transform(classname, classData);
                if (rewritten!=classData) {
                    FileUtils.writeByteArrayToFile(new File(classname+".class"),rewritten);
                    System.out.println("Modified "+classname);
                }
                return super.defineClassFromData(container, rewritten, classname);
            }
        };

        cl.addPathComponent(f1);
        cl.addPathComponent(f2);

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
