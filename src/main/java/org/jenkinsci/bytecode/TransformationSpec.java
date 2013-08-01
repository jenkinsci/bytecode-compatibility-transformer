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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.jenkinsci.constant_pool_scanner.ConstantType.*;

/**
 * Definition of what to transform.
 */
class TransformationSpec {
    /**
     * From internal name of the type to the data structure.
     */
    final Map<String,ClassRewriteSpec> rewrites = new HashMap<String,ClassRewriteSpec>();

    TransformationSpec() {
    }

    public TransformationSpec(Collection<? extends ClassLoader> loaders) throws IOException {
        for (ClassLoader cl : loaders) {
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
    }

    /**
     * Looks the constant pool and determine if this class file may possibly require a rewrite
     * according to the current rules.
     */
    boolean mayNeedTransformation(byte[] image) {
        try {
            ConstantPool p = ConstantPoolScanner.parse(image, FIELD_REF, METHOD_REF);
            for (FieldRefConstant r : p.list(FieldRefConstant.class)) {
                ClassRewriteSpec s = rewrites.get(r.getClazz());
                if (s!=null && s.fields.get(r.getName())!=null)
                    return true;
            }
            for (MethodRefConstant r : p.list(MethodRefConstant.class)) {
                ClassRewriteSpec s = rewrites.get(r.getClazz());
                if (s!=null && s.methods.get(r.getName())!=null)
                    return true;
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to parse the constant pool",e);
            return false;
        }
    }

    private ClassRewriteSpec createClassRewrite(Class c) {
        String name = Type.getInternalName(c);
        ClassRewriteSpec r = rewrites.get(name);
        if (r==null)
            rewrites.put(name,r=new ClassRewriteSpec(name));
        return r;
    }

    void addFieldRewriteSpec(Class owner, String name, MemberRewriteSpec spec) {
        ClassRewriteSpec c = createClassRewrite(owner);
        c.fields.put(name,spec.compose(c.fields.get(name)));
    }

    void addMethodRewriteSpec(Class owner, String name, MemberRewriteSpec spec) {
        ClassRewriteSpec c = createClassRewrite(owner);
        c.methods.put(name,spec.compose(c.methods.get(name)));
    }


    private static final Logger LOGGER = Logger.getLogger(TransformationSpec.class.getName());
}
