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
import java.util.Map;
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
     * From internal name of class/interface to its details of rewrite.
     */
    final Map<String,ClassRewriteSpec> rewrites = new HashMap<String,ClassRewriteSpec>();

    /**
     * Name + descriptor that requires rewriting.
     */
    Map<NameAndType,Set<ClassRewriteSpec>> fields = new HashMap<NameAndType,Set<ClassRewriteSpec>>(); // maybe we need to add type here too to narrow the search

    TransformationSpec() {
    }

    /**
     * Copy constructor.
     */
    TransformationSpec(TransformationSpec that) {
        for (Entry<String, ClassRewriteSpec> e : that.rewrites.entrySet()) {
            rewrites.put(e.getKey(),new ClassRewriteSpec(e.getValue()));
        }
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

    void addFieldRewriteSpec(Class owner, String name, Class[] types, MemberRewriteSpec spec) {
        ClassRewriteSpec c = createClassRewrite(owner);
        spec = spec.compose(c.fields.get(name));
        spec.owner = c;
        c.fields.put(name, spec);

        for (Class type : types) {
            NameAndType key = new NameAndType(Type.getDescriptor(type),name);

            Set<ClassRewriteSpec> classes = fields.get(key);
            if (classes==null)  fields.put(key,classes=new HashSet<ClassRewriteSpec>());
            classes.add(c);
        }
    }

    void addMethodRewriteSpec(Class owner, String name, MemberRewriteSpec spec) {
        ClassRewriteSpec c = createClassRewrite(owner);
        spec = spec.compose(c.methods.get(name));
        spec.owner = c;
        c.methods.put(name, spec);
    }

    private static final Logger LOGGER = Logger.getLogger(TransformationSpec.class.getName());
}
