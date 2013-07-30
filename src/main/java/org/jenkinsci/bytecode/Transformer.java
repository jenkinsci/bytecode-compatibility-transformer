package org.jenkinsci.bytecode;

import org.jvnet.hudson.annotation_indexer.Index;
import org.kohsuke.asm3.ClassAdapter;
import org.kohsuke.asm3.ClassReader;
import org.kohsuke.asm3.ClassWriter;
import org.kohsuke.asm3.MethodAdapter;
import org.kohsuke.asm3.MethodVisitor;
import org.kohsuke.asm3.Type;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.kohsuke.asm3.Opcodes.*;

/**
 * Transform byte code where code references bytecode rewrite annotations.
 *
 * @author Kohsuke Kawaguchi
 */
public class Transformer {
    /**
     * Definition of what to transform.
     */
    class TransformationSpec {
        /**
         * From internal name of the type to the data structure.
         */
        private Map<String,ClassRewriteSpec> rewrites = new HashMap<String,ClassRewriteSpec>();

        TransformationSpec() {
        }

        public TransformationSpec(Collection<? extends ClassLoader> loaders) throws IOException {
            for (ClassLoader cl : loaders) {
                for (AnnotatedElement e : Index.list(Adapt.class,cl)) {
                    if (e instanceof Field) {
                        Field f = (Field) e;
                        ClassRewriteSpec r = createClassRewrite(f.getDeclaringClass());
                        r.fields.put(f.getName(),Type.getType(f.getType()));
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

    /**
     * All the references into this class that require a rewrite.
     */
    class ClassRewriteSpec {
        private final String internalName;

        /**
         * Fields to rewrite. From the name of the field to its correct descriptor.
         */
        private Map<String,Type> fields = new HashMap<String,Type>();

        public ClassRewriteSpec(String internalName) {
            this.internalName = internalName;
        }
    }

    private volatile TransformationSpec spec = new TransformationSpec(); // start with empty

    public void loadRules(ClassLoader cl) throws IOException {
        loadRules(Collections.singleton(cl));
    }

    /**
     * Scans the rewrite instructions and prepare to rewrite classes that refer to them
     * accordingly.
     *
     * This method is concurrency safe, and can be invoked even when Transformer is already being in use.
     */
    public void loadRules(Collection<? extends ClassLoader> loaders) throws IOException {
        spec = new TransformationSpec(loaders);
    }

    /**
     *
     * @param className
     *      Binary name of the class, such as "java.security.KeyStore$Builder$FileBuilder$1"
     * @param image
     *      Class file image loaded from the disk.
     *
     */
    public byte[] transform(final String className, byte[] image) {
        ClassReader cr = new ClassReader(image);
        ClassWriter cw = new ClassWriter(/*ClassWriter.COMPUTE_FRAMES|*/ClassWriter.COMPUTE_MAXS);

        final boolean[] modified = new boolean[1];

        cr.accept(new ClassAdapter(cw) {
            @Override
            public MethodVisitor visitMethod(int access, final String methodName, final String methodDescriptor, String methodSignature, String[] exceptions) {
                MethodVisitor base = super.visitMethod(access, methodName, methodDescriptor, methodSignature, exceptions);

                return new MethodAdapter(base) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        ClassRewriteSpec o = spec.rewrites.get(owner);
                        if (o!=null) {
                            Type d = o.fields.get(name);
                            if (d!=null) {
                                LOGGER.log(Level.FINE, "Rewrote reference to {3}.{4} in {0}.{1}{2}",
                                        new Object[]{className,methodName,methodDescriptor,owner,name});

                                modified[0] = true;

                                switch (opcode) {
                                case GETFIELD:
                                case GETSTATIC:
                                    // rewrite "x.y" to "(T)x.y".
                                    // we'll leave it up to HotSpot to optimize away casts
                                    super.visitFieldInsn(opcode, owner, name, d.getDescriptor());
                                    super.visitTypeInsn(CHECKCAST,Type.getType(desc).getInternalName());
                                    return;
                                case PUTFIELD:
                                case PUTSTATIC:
                                    // rewrite "x.y=v" to "x.y=(T)v"
                                    super.visitTypeInsn(CHECKCAST,d.getInternalName());
                                    super.visitFieldInsn(opcode, owner, name, d.getDescriptor());
                                    return;
                                }
                            }
                        }
                        super.visitFieldInsn(opcode, owner, name, desc);
                    }
                };
            }
        },cr.SKIP_FRAMES);

        if (!modified[0])  return image;            // untouched
        return cw.toByteArray();
    }

    private static final Logger LOGGER = Logger.getLogger(Transformer.class.getName());
}
