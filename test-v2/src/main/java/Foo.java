import org.jenkinsci.bytecode.AdaptField;

import java.lang.String;
import java.util.*;

class Foo {
    static class Inner {
        @AdaptField(was=List.class)
        ArrayList x = new ArrayList();
        @AdaptField(was=Object[].class)
        String[] y;
        @AdaptField(was=Inner.class)
        Object z;

        @AdaptField(was=String.class)
        static final Object s = "hello";

        // field i is gone!
        int _i;
        @AdaptField(was=int.class)
        int i() {
            return _i;
        }
        @AdaptField(was=int.class)
        void i(int v) {
            _i=v;
        }

        // field j is gone!
        static boolean[] _j = {true,false};
        @AdaptField(name = "j", was=boolean[].class)
        static boolean[] getJ() {
            return _j;
        }
        @AdaptField(name = "j", was=boolean[].class)
        static void setJ(boolean[] v) {
            _j=v;
        }

        String version = "v2";
    }
}