package org.jenkinsci.bytecode;

import org.kohsuke.asm3.ClassAdapter;
import org.kohsuke.asm3.ClassReader;
import org.kohsuke.asm3.ClassWriter;
import org.kohsuke.asm3.Label;
import org.kohsuke.asm3.MethodAdapter;
import org.kohsuke.asm3.MethodVisitor;
import org.kohsuke.asm3.Type;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

import static org.kohsuke.asm3.Opcodes.*;

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

        final ClassReader cr = new ClassReader(image);
        final ClassWriter cw = new ClassWriter(/*ClassWriter.COMPUTE_FRAMES|*/ClassWriter.COMPUTE_MAXS);

        final boolean[] modified = new boolean[1];

        cr.accept(new ClassAdapter(cw) {
            private ClassRewritingContext context;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(Math.max(version,49), access, name, signature, superName, interfaces);
                this.context = new ClassRewritingContext(name);
            }

            @Override
            public MethodVisitor visitMethod(int access, final String methodName, final String methodDescriptor, String methodSignature, String[] exceptions) {
                final MethodVisitor base = super.visitMethod(access, methodName, methodDescriptor, methodSignature, exceptions);

                return new MethodAdapter(base) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                        modified[0] |= spec.methods.rewrite(context,opcode,owner,name,desc,base);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        modified[0] |= spec.fields.rewrite(context,opcode,owner,name,desc,base);
                    }
                };
            }

            @Override
            public void visitEnd() {
                context.generateCheckerMethods(cw);
                super.visitEnd();
            }

        },cr.SKIP_FRAMES);

        if (!modified[0])  return image;            // untouched
        return cw.toByteArray();
    }
}
