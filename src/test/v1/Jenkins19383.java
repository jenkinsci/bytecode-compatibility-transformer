import java.util.ArrayList;
import java.util.List;

class Jenkins19383 {
    /**
     * IvySettings class has the triggers field, and that class won't have access to this class.
     */
    public List triggers = new ArrayList();
}