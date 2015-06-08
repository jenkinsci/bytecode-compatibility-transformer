package org.jenkinsci.bytecode;

import org.kohsuke.asm5.ClassReader;
import org.kohsuke.asm5.ClassVisitor;
import org.kohsuke.asm5.ClassWriter;
import org.kohsuke.asm5.MethodVisitor;
import org.kohsuke.asm5.commons.JSRInlinerAdapter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import static org.kohsuke.asm5.Opcodes.*;

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
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);

        final boolean[] modified = new boolean[1];

        // If code contains JSR/RET instructions then ASM fails to transform it with
        // java.lang.RuntimeException: JSR/RET are not supported with computeFrames option
        // so inline any JSR subroutines
        ClassVisitor jsrInliner = new ClassVisitor(ASM5,cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                final MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
                return new JSRInlinerAdapter(base, access, name, desc, signature, exceptions);
            }
        };
        
        cr.accept(new ClassVisitor(ASM5,jsrInliner) {
            private ClassRewritingContext context;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                // version 50 (JDK 6) required to generate StackMapTable
                super.visit(Math.max(version,50), access, name, signature, superName, interfaces);
                this.context = new ClassRewritingContext(name);
            }

            @Override
            public MethodVisitor visitMethod(int access, final String methodName, final String methodDescriptor, String methodSignature, String[] exceptions) {
                final MethodVisitor base = super.visitMethod(access, methodName, methodDescriptor, methodSignature, exceptions);

                return new MethodVisitor(ASM5,base) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        modified[0] |= spec.methods.rewrite(context,opcode,owner,name,desc, itf, base);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        modified[0] |= spec.fields.rewrite(context,opcode,owner,name,desc, false, base);
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
