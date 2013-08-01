package org.jenkinsci.bytecode;

import java.lang.reflect.AnnotatedElement;

/**
 * Creates {@link MemberRewriteSpec} and adds them to {@link TransformationSpec}
 * based on the annotation that has {@link AdapterAnnotation} on.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AdapterAnnotationParser {
    /**
     * @param spec
     *      Parsed rules should be added to this spec.
     * @param target
     *      The element on which the annotation is on.
     */
    abstract void parse(TransformationSpec spec, AnnotatedElement target);
}
