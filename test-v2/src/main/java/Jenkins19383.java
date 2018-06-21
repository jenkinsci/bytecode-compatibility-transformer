import org.jenkinsci.bytecode.AdaptField;
import java.util.LinkedList;
import java.util.List;

/**
 * Test class for JENKINS-19383 and field adaptor
 */
class Jenkins19383 {
    @AdaptField(was=List.class)
    public LinkedList triggers = new LinkedList();
}