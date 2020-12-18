package org.jenkinsci.bytecode;

import org.kohsuke.asm6.ClassReader;
import org.kohsuke.asm6.ClassVisitor;
import org.kohsuke.asm6.ClassWriter;
import org.kohsuke.asm6.MethodVisitor;
import org.kohsuke.asm6.commons.JSRInlinerAdapter;

import org.jenkinsci.bytecode.helper.LoggingHelper;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.kohsuke.asm6.Opcodes.*;

/**
 * Transform byte code where code references bytecode rewrite annotations.
 *
 * @author Kohsuke Kawaguchi
 */
public class Transformer {
    
    private static Logger LOGGER = Logger.getLogger(Transformer.class.getName());

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
     * @deprecated - see {@link #transform(String, byte[], ClassLoader)}
     */
    @Deprecated
    public byte[] transform(final String className, byte[] image) {
        return transform(className, image, this.getClass().getClassLoader());
    }
    
    /**
     * Transforms a class file.
     *
     * @param className
     *      Binary name of the class, such as "java.security.KeyStore$Builder$FileBuilder$1"
     * @param image
     *      Class file image loaded from the disk.
     * @param classLoader
     *      The classloader to use when searching for a common parent of 2 classes (used for generating certain StackFrames)
     * @return
     *      Transformed byte code.
     */
    public byte[] transform(final String className, byte[] image, ClassLoader classLoader) {
        LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "transform({0}, {1})", className, classLoader);
        if (!spec.mayNeedTransformation(image)) {
            LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "no transformation required for {0}", className);
            return image;
        }
        /* 
         * StackFrames are only supported in bytecode 50 (JDK 6) and higher
         * so there is no need to recompute them for versions less than this.
         */
        final boolean regenerateStackMapTable = getBytecodeVersion(image) >= 50;

        final ClassReader cr = new ClassReader(image);
        final NonClassLoadingClassWriter cw = new NonClassLoadingClassWriter(classLoader, regenerateStackMapTable ? ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS : ClassWriter.COMPUTE_MAXS);

        final boolean[] modified = new boolean[1];

        // If code contains JSR/RET instructions then ASM fails to transform it with
        // java.lang.RuntimeException: JSR/RET are not supported with computeFrames option
        // so inline any JSR subroutines
        ClassVisitor jsrInliner = new ClassVisitor(ASM5,cw) {
            
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                final MethodVisitor base = super.visitMethod(access, name, desc, signature, exceptions);
                LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "jsrInliner.visitMethod({0}, {1}, {2}, {3}, {4})", access, name, desc, signature, exceptions);
                return new JSRInlinerAdapter(ASM5, base, access, name, desc, signature, exceptions) {

                   @Override
                    public void visitEnd() {
                       LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "visit end for {0}", name);
                       super.visitEnd();
                    } 
                };
            }
        };
        
        cr.accept(new ClassVisitor(ASM5, regenerateStackMapTable ? jsrInliner : cw) {
            private ClassRewritingContext context;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                // we need to set the version to at least 49 - otherwise we introduce opcodes (ldc) that are not available and things break.
                super.visit(Math.max(version,49), access, name, signature, superName, interfaces);
                this.context = new ClassRewritingContext(name);
            }

            @Override
            public MethodVisitor visitMethod(int access, final String methodName, final String methodDescriptor, final String methodSignature, String[] exceptions) {
                final MethodVisitor base = super.visitMethod(access, methodName, methodDescriptor, methodSignature, exceptions);

                return new MethodVisitor(ASM5,base) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        boolean _modified = spec.methods.rewrite(context,opcode,owner,name,desc, itf, base);
                        modified[0] |= _modified;
                        LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "{0}.{1}({2}) {3} modified",
                                   className, methodName, methodSignature == null ? "" : methodSignature,
                                                 _modified ? "was" : "was not" );
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        boolean _modified = spec.fields.rewrite(context,opcode,owner,name,desc, false, base);
                        modified[0] |= _modified;
                        LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "{0}.{1}({2}) {3} modified",
                                   className, methodName, methodSignature == null ? "" : methodSignature,
                                                 _modified ? "was" : "was not" );
                    }
                };
            }

            @Override
            public void visitEnd() {
                LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "visitEnd(1) for {0}", className);
                context.generateCheckerMethods(cw);
                super.visitEnd();
                LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "visitEnd(2) for {0}", className);
            }

        },ClassReader.SKIP_FRAMES);

        if (!modified[0]) {
            LoggingHelper.conditionallyLog(LOGGER, Level.FINER, "class {0} was not modified.", className);
            return image;            // untouched
        }
        LoggingHelper.conditionallyLog(LOGGER, Level.FINER, "class {0} was modified.", className);
        return cw.toByteArray();
    }
    
    /**
     * Inspects a byte array representation of a class and returns the version of the bytecode.
     * @param classData the class bytecode.
     * @return an integer representing the major version of the bytecode.
     */
    private int getBytecodeVersion(byte[] classData) {
        int version = (( classData[6] & 0xFF ) << 8 ) | (classData[7] & 0xFF);
        LoggingHelper.conditionallyLog(LOGGER, Level.FINEST, "bytecode version is {0}", version);
        return version;
    }
}
