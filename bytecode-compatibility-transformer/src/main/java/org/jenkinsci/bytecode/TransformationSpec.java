package org.jenkinsci.bytecode;

import org.jenkinsci.bytecode.helper.LoggingHelper;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.FieldRefConstant;
import org.jenkinsci.constant_pool_scanner.MethodRefConstant;
import org.jvnet.hudson.annotation_indexer.Index;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jenkinsci.constant_pool_scanner.ConstantType.*;

/**
 * Definition of what to transform.
 */
class TransformationSpec {
    
    /**
     * Fields by their name and type (but without the owner class) that requires rewriting.
     *
     * The matching needs to happen without taking the owner class into account because
     * when a subtype refers to a field in a super type, javac generates field reference
     * with the owner type set to the sub type, and let JVM resolve it to the correct super type.
     */
    final MemberTransformSpec fields;

    /**
     * Methods by their name and type (but without the owner class.)
     */
    final MemberTransformSpec methods;

    TransformationSpec() {
        this.fields = new MemberTransformSpec(Kind.FIELD);
        this.methods = new MemberTransformSpec(Kind.METHOD);
    }

    /**
     * Copy constructor.
     */
    TransformationSpec(TransformationSpec that) {
        this.fields = new MemberTransformSpec(that.fields);
        this.methods = new MemberTransformSpec(that.methods);
    }

    void loadRule(ClassLoader cl) throws IOException {
        for (Class<? extends Annotation> annotation : Index.list(AdapterAnnotation.class,cl,Class.class)) {
            AdapterAnnotationParser f;
            AdapterAnnotation aa = annotation.getAnnotation(AdapterAnnotation.class);
            try {
                f = aa.value().newInstance();
            } catch (InstantiationException e) {
                LoggingHelper.asyncLog(LOGGER, Level.WARNING, e, "Failed to instantiate {0}", aa.value());
                continue;
            } catch (IllegalAccessException e) {
                LoggingHelper.asyncLog(LOGGER, Level.WARNING, e, "Failed to instantiate {0}", aa.value());
                continue;
            }

            for (AnnotatedElement e : Index.list(annotation, cl)) {
                f.parse(this, e);
            }
        }
    }

    /**
     * Looks the constant pool and determine if this class file may possibly require a rewrite
     * according to the current rules.
     */
    boolean mayNeedTransformation(byte[] image) {
        try {
            ConstantPool p = ConstantPoolScanner.parse(image, FIELD_REF, METHOD_REF);
            for (FieldRefConstant r : p.list(FieldRefConstant.class)) {
                if (fields.containsKey(new NameAndType(r))) {
                    LoggingHelper.asyncLog(LOGGER, Level.FINEST, "mayNeedTransformation returning true - fields.containsKey({0}) - {1}", r.getName(), r.getClazz());
                    return true;
                }
            }
            for (MethodRefConstant r : p.list(MethodRefConstant.class)) {
                if (methods.containsKey(new NameAndType(r))) {
                    LoggingHelper.asyncLog(LOGGER, Level.FINEST, "mayNeedTransformation returning true - methods.containsKey({0}) - {1}", r.getName(), r.getClazz());
                    return true;
                }
            }
            LoggingHelper.asyncLog(LOGGER, Level.FINEST, "mayNeedTransformation returning false");
            return false;
        } catch (IOException e) {
            LoggingHelper.asyncLog(LOGGER, Level.WARNING, e, "Failed to parse the constant pool");
            return false;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(TransformationSpec.class.getName());
}
