package org.jenkinsci.bytecode;

import org.jvnet.hudson.annotation_indexer.Indexed;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Rewrites a field reference by adapting the type of the field.
 *
 * Given byte code that refers to "OldType ClassX.fieldY", the byte code gets rewritten
 * to refer to "NewType ClassX.fieldY".
 *
 * <p>
 * The get access will be rewritten as "v = (OldType)x.fieldY",
 * the set access will be rewritten as "x.fieldY = (NewType)v".
 * At runtime, if the type of the value doesn't match up, this will result in
 * {@link ClassCastException}.
 *
 * <p>
 * For adopting the return type of the method, see a separate project "bridge method injector"
 * that achieves the goal without the need for runtime transformation.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Target({FIELD,METHOD})
@Indexed
public @interface AdaptField {
    /**
     * Name of the field that's being adapted.
     */
    String value() default "";
}
