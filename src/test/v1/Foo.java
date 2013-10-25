import java.util.*;

// this is the code against which the client is compiled
// the test will then run that client with the v2 version.
class Foo {
    static class Inner {
        List x = new ArrayList();
        Object[] y;
        Inner z;

        static final String s = "hello";

        int i;

        static boolean[] j = {true,false};

        String version = "v1";
    }
}