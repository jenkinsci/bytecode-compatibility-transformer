package org.jenkinsci.bytecode;

import org.jvnet.hudson.annotation_indexer.Index;
import org.kohsuke.asm3.Type;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
            for (AnnotatedElement e : Index.list(AdaptField.class, cl)) {
                AdaptField af = e.getAnnotation(AdaptField.class);
                if (e instanceof Field) {
                    Field f = (Field) e;
                    ClassRewriteSpec r = createClassRewrite(f.getDeclaringClass());

                    String name = af.value();
                    if (name.length()==0)   name = f.getName(); // default to the same name
                    r.fields.put(name, MemberRewriteSpec.fieldToField(f));
                }
                if (e instanceof Method) {
                    Method m = (Method) e;
                    ClassRewriteSpec r = createClassRewrite(m.getDeclaringClass());

                    String name = af.value();
                    if (name.length()==0)   name = m.getName(); // default to the same name

                    MemberRewriteSpec existing = r.fields.get(name);
                    r.fields.put(name, MemberRewriteSpec.fieldToMethod(m).compose(existing));
                }
            }
        }
    }

    private ClassRewriteSpec createClassRewrite(Class c) {
        String name = Type.getInternalName(c);
        ClassRewriteSpec r = rewrites.get(name);
        if (r==null)
            rewrites.put(name,r=new ClassRewriteSpec(name));
        return r;
    }
}
