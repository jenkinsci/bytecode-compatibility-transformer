import junit.framework.*;
import java.util.*;

/**
 * Code that refers to classes that change.
 */
public class Main extends TestCase {
    Foo.Inner i = new Foo.Inner();
    public void testFooX() {
        i.x.add(1);
        i.x.add(2);
        List x = i.x;
        assertEquals(2,x.size());
        i.x = x;    // in v2 (i.x) is typed as ArrayList but this will still work
        assertSame(i.x,x);
    }

    public void testFooY() {
        assertNull(i.y);
        i.y = new String[1];
        assertEquals(1,i.y.length);
        i.y[0] = "Hello";
    }

    public void testFooZ() {
        assertNull(i.z);
        i.z = i;
        assertSame(i.z,i);
        i = i.z;
        System.out.println(i);
    }
}