import org.jenkinsci.bytecode.AdaptField;
import java.util.*;

class Jenkins19383 {
    @AdaptField(was=List.class)
    public LinkedList triggers = new LinkedList();
}