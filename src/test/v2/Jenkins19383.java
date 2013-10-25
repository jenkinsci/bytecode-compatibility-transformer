import org.jenkinsci.bytecode.AdaptField;
import java.util.LinkedList;
import java.util.List;

class Jenkins19383 {
    @AdaptField(was=List.class)
    public LinkedList triggers = new LinkedList();
}