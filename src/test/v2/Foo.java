import org.jenkinsci.bytecode.Adapt;
import java.util.*;

class Foo {
    static class Inner {
        @Adapt
        ArrayList x = new ArrayList();
        @Adapt
        String[] y;
        @Adapt
        Object z;
    }
}