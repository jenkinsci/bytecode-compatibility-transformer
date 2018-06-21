import java.util.ArrayList;
import java.util.List;

/**
 * Test class for JENKINS-19383
 */
public class Jenkins19383 {
    /**
     * IvySettings class has the triggers field, and that class won't have access to this class.
     */
    public List triggers = new ArrayList();
}