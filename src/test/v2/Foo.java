import org.jenkinsci.bytecode.AdaptField;
import java.util.*;

class Foo {
    static class Inner {
        @AdaptField
        ArrayList x = new ArrayList();
        @AdaptField
        String[] y;
        @AdaptField
        Object z;

        @AdaptField
        static final Object s = "hello";

        // field i is gone!
        int _i;
        @AdaptField
        int i() {
            return _i;
        }
        @AdaptField
        void i(int v) {
            _i=v;
        }

        // field j is gone!
        static boolean[] _j = {true,false};
        @AdaptField("j")
        static boolean[] getJ() {
            return _j;
        }
        @AdaptField("j")
        static void setJ(boolean[] v) {
            _j=v;
        }

        String version = "v2";
    }
}