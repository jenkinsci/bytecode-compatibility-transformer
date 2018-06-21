package org.jenkinsci.bytecode;

import org.jvnet.hudson.annotation_indexer.Indexed;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Meta-annotation for inserting bytecode transformation rule.
 *
 * @author Kohsuke Kawaguchi
 */
@Indexed
@Retention(RUNTIME)
@Target(ANNOTATION_TYPE)
/*package*/ @interface AdapterAnnotation {
    Class<? extends AdapterAnnotationParser> value();
}
