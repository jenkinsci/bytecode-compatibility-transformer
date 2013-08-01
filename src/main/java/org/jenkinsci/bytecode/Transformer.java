package org.jenkinsci.bytecode;

import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.FieldRefConstant;
import org.kohsuke.asm3.ClassAdapter;
import org.kohsuke.asm3.ClassReader;
import org.kohsuke.asm3.ClassWriter;
import org.kohsuke.asm3.MethodAdapter;
import org.kohsuke.asm3.MethodVisitor;

import java.io.DataInput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

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
        if (!scanFieldReference(image))
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
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        ClassRewriteSpec o = spec.rewrites.get(owner);
                        if (o!=null) {
                            FieldRewriteSpec fr = o.fields.get(name);
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

    /**
     * Looks the constant pool and determine if this class file may possibly require a rewrite.
     */
    private boolean scanFieldReference(byte[] image) {
        try {
            for (FieldRefConstant r : ConstantPoolScanner.parse(image, ConstantType.FIELD_REF).list(FieldRefConstant.class)) {
                ClassRewriteSpec s = spec.rewrites.get(r.getClazz());
                if (s!=null)
                    if (s.fields.get(r.getName())!=null)
                        return true;
            }
            return false;
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to parse the constant pool",e);
            return false;
        }
    }

    private static void skip(DataInput input, int bytes) throws IOException {
        int skipped = input.skipBytes(bytes);
        if (skipped != bytes) {
            throw new IOException("Truncated class file");
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Transformer.class.getName());
}
