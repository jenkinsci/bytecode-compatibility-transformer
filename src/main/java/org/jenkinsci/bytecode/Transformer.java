package org.jenkinsci.bytecode;

import org.kohsuke.asm3.ClassAdapter;
import org.kohsuke.asm3.ClassReader;
import org.kohsuke.asm3.ClassWriter;
import org.kohsuke.asm3.MethodAdapter;
import org.kohsuke.asm3.MethodVisitor;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transform byte code where code references bytecode rewrite annotations.
 *
 * @author Kohsuke Kawaguchi
 */
public class Transformer {

    private volatile TransformationSpec spec = new TransformationSpec(); // start with empty

    public void loadRules(ClassLoader cl) throws IOException {
        loadRules(Collections.singleton(cl));
    }

    /**
     * Scans the rewrite instructions and prepare to rewrite classes that refer to them
     * accordingly.
     *
     * <p>
     * The effects of this method is cumulative.
     * The added rules are stored on top of what's already in this transformer.
     *
     * This method is concurrency safe, and can be invoked even when Transformer is already being in use.
     */
    public synchronized void loadRules(Collection<? extends ClassLoader> loaders) throws IOException {
        TransformationSpec spec = new TransformationSpec(this.spec);
        for (ClassLoader cl : loaders) {
            spec.loadRule(cl);
        }
        this.spec = spec;
    }

    /**
     * Transforms a class file.
     *
     * @param className
     *      Binary name of the class, such as "java.security.KeyStore$Builder$FileBuilder$1"
     * @param image
     *      Class file image loaded from the disk.
     * @return
     *      Transformed byte code.
     */
    public byte[] transform(final String className, byte[] image) {
        if (!spec.mayNeedTransformation(image))
            return image;

        ClassReader cr = new ClassReader(image);
        ClassWriter cw = new ClassWriter(/*ClassWriter.COMPUTE_FRAMES|*/ClassWriter.COMPUTE_MAXS);

        final boolean[] modified = new boolean[1];

        cr.accept(new ClassAdapter(cw) {
            @Override
            public MethodVisitor visitMethod(int access, final String methodName, final String methodDescriptor, String methodSignature, String[] exceptions) {
                final MethodVisitor base = super.visitMethod(access, methodName, methodDescriptor, methodSignature, exceptions);

                return new MethodAdapter(base) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                        ClassRewriteSpec o = spec.rewrites.get(owner);
                        if (o!=null) {
                            MemberRewriteSpec fr = o.methods.get(name);
                            if (fr!=null) {
                                LOGGER.log(Level.FINE, "Rewrote reference to {3}.{4}{5} in {0}.{1}{2}",
                                        new Object[]{className,methodName,methodDescriptor,owner,name,desc});

                                if (fr.visitMethodInsn(opcode, owner, name, desc, base)) {
                                    modified[0] = true;
                                    return;
                                }
                            }
                        }
                        super.visitMethodInsn(opcode, owner, name, desc);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        ClassRewriteSpec o = spec.rewrites.get(owner);
                        if (o!=null) {
                            MemberRewriteSpec fr = o.fields.get(name);
                            if (fr!=null) {
                                LOGGER.log(Level.FINE, "Rewrote reference to {3}.{4} in {0}.{1}{2}",
                                        new Object[]{className,methodName,methodDescriptor,owner,name});

                                if (fr.visitFieldInsn(opcode,owner,name,desc,base)) {
                                    modified[0] = true;
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
