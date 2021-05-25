package org.jenkinsci.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.objectweb.asm.Opcodes.*;

/**
 * Remembers what class is being rewritten and what helper methods need to be generated into this class.
 *
 * @author Kohsuke Kawaguchi
 */
final class ClassRewritingContext {
    final String className;
    private final Map<Type,Integer> checkerMethods = new HashMap<Type,Integer>();

    ClassRewritingContext(String className) {
        this.className = className;
    }

    /**
     * Checks if the given (actual) type of the object is assignable to the suspected type.
     *
     * <p>
     * We cannot do this inline by directly generating an expression like {@code Foo.class.isAssignableFrom(Bar.class)}
     * because Foo.class might not be accessible. In contrast, Bar.class is always known to be accessible because
     * that's the owner class of the method/field being accessed in the unmodified byte code.
     * So we do this by using a private static helper method.
     *
     * <p>
     * This operation manipulates the operand stack as " -> Z".
     */
    public void callTypeCheckMethod(Type suspected, Type actual, MethodVisitor base) {
        base.visitLdcInsn(actual);
        Integer idx = checkerMethods.get(suspected);
        if (idx==null)
            checkerMethods.put(suspected,idx=checkerMethods.size());

        base.visitMethodInsn(INVOKESTATIC, className, checkerMethodName(idx), CHECKER_METHOD_DESCRIPTOR, false);
    }

    /**
     * Generates a type check method that handles {@link IllegalAccessError}.
     *
     * Example:
     *
     * private static void ____isAssignableFrom1(Class t) {
     *     try {
     *         return SUSPECTED_TYPE.isAssignableFrom(t);
     *     } catch (IllegalAccessError e) {
     *         // this happens when SUSPECTED_TYPE is not accessible in from the current class.
     *         return false;
     *     }
     * }
     */
    public void generateCheckerMethods(ClassVisitor base) {
        for (Entry<Type, Integer> e : checkerMethods.entrySet()) {
            MethodVisitor mv = base.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, checkerMethodName(e.getValue()), CHECKER_METHOD_DESCRIPTOR, null, new String[0]);

            Label startTry = new Label(), endTry = new Label(), handler = new Label();

            mv.visitLabel(startTry);
            mv.visitLdcInsn(e.getKey());
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "isAssignableFrom", "(Ljava/lang/Class;)Z", false);
            mv.visitInsn(IRETURN);
            mv.visitLabel(endTry);

            mv.visitTryCatchBlock(startTry,endTry,handler, ILLEGAL_ACCESS_ERROR);

            // exception handler
            mv.visitLabel(handler);
            mv.visitLdcInsn(0);
            mv.visitInsn(IRETURN);


            mv.visitMaxs(2,0);
            mv.visitEnd();
        }
    }

    private String checkerMethodName(int idx) {
        return "____isAssignableFrom"+idx;
    }

    private static final String CHECKER_METHOD_DESCRIPTOR = "(Ljava/lang/Class;)Z";
    public static final String ILLEGAL_ACCESS_ERROR = Type.getInternalName(IllegalAccessError.class);
}
