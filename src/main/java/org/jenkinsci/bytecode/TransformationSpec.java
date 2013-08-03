package org.jenkinsci.bytecode;

import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.FieldRefConstant;
import org.jenkinsci.constant_pool_scanner.MethodRefConstant;
import org.jvnet.hudson.annotation_indexer.Index;
import org.kohsuke.asm3.Type;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
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
    final RewriteMap fields = new RewriteMap(); // maybe we need to add type here too to narrow the search

    /**
     * Methods by their name and type (but without the owner class.)
     */
    final RewriteMap methods = new RewriteMap(); // maybe we need to add type here too to narrow the search

    class RewriteMap extends HashMap<NameAndType,Set<MemberRewriteSpec>> {
        void copyFrom(RewriteMap rhs) {
            for (Entry<NameAndType,Set<MemberRewriteSpec>> e : rhs.entrySet()) {
                put(e.getKey(),new HashSet<MemberRewriteSpec>(e.getValue()));
            }
        }

        void addRewriteSpec(String name, Class[] types, MemberRewriteSpec c) {
            OUTER:
            for (Class type : types) {
                NameAndType key = new NameAndType(Type.getDescriptor(type),name);

                Set<MemberRewriteSpec> specs = get(key);
                if (specs==null)  put(key, specs = new HashSet<MemberRewriteSpec>());

                for (MemberRewriteSpec existing : specs) {
                    if (existing.owner.equals(c.owner)) {
                        // this transformer rewrites different access to the same member
                        specs.remove(existing);
                        specs.add(c.compose(existing));
                        continue OUTER;
                    }
                }

                specs.add(c);
            }
        }
    }

    TransformationSpec() {
    }

    /**
     * Copy constructor.
     */
    TransformationSpec(TransformationSpec that) {
        this.fields.copyFrom(that.fields);
        this.methods.copyFrom(that.methods);
    }

    void loadRule(ClassLoader cl) throws IOException {
        for (Class<? extends Annotation> annotation : Index.list(AdapterAnnotation.class,cl,Class.class)) {
            AdapterAnnotationParser f;
            AdapterAnnotation aa = annotation.getAnnotation(AdapterAnnotation.class);
            try {
                f = aa.value().newInstance();
            } catch (InstantiationException e) {
                LOGGER.log(Level.WARNING, "Failed to instantiate "+aa.value(),e);
                continue;
            } catch (IllegalAccessException e) {
                LOGGER.log(Level.WARNING, "Failed to instantiate " + aa.value(), e);
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
                if (fields.containsKey(new NameAndType(r)))
                    return true;
            }
            for (MethodRefConstant r : p.list(MethodRefConstant.class)) {
                if (methods.containsKey(new NameAndType(r)))
                    return true;
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to parse the constant pool",e);
            return false;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(TransformationSpec.class.getName());
}
