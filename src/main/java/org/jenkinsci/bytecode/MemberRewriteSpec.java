package org.jenkinsci.bytecode;

import org.kohsuke.asm3.MethodVisitor;
import org.kohsuke.asm3.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.kohsuke.asm3.Opcodes.*;
import static org.kohsuke.asm3.Type.*;

/**
 * Rewrites field access.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class MemberRewriteSpec {
    boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
        return false;
    }

    boolean visitMethodInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
        return false;
    }


    /**
     * Used to merge setter rewrite spec to getter rewrite spec.
     *
     * TODO: improve error handling.
     */
    MemberRewriteSpec compose(final MemberRewriteSpec rhs) {
        if (rhs==null)  return this;

        final MemberRewriteSpec lhs = this;
        return new MemberRewriteSpec() {
            @Override
            boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                return lhs.visitFieldInsn(opcode, owner, name, desc, delegate)
                    || rhs.visitFieldInsn(opcode, owner, name, desc, delegate);
            }

            @Override
            boolean visitMethodInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                return lhs.visitMethodInsn(opcode, owner, name, desc, delegate)
                    || rhs.visitMethodInsn(opcode, owner, name, desc, delegate);
            }
        };
    }

    /**
     * Rewrites a field reference to another field access.
     */
    static MemberRewriteSpec fieldToField(Field f) {
        final String newName = f.getName();
        final Type newType = Type.getType(f.getType());
        final String newTypeDescriptor = newType.getDescriptor();
        final String newTypeInternalName = isReferenceType(newType) ? newType.getInternalName() : null;

        return new MemberRewriteSpec() {
            @Override
            boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                switch (opcode) {
                case GETFIELD:
                case GETSTATIC:
                    // rewrite "x.y" to "(T)x.z".
                    // we'll leave it up to HotSpot to optimize away casts
                    delegate.visitFieldInsn(opcode, owner, newName, newTypeDescriptor);
                    Type t = Type.getType(desc);
                    if (isReferenceType(t))
                        delegate.visitTypeInsn(CHECKCAST, t.getInternalName());
                    return true;
                case PUTFIELD:
                case PUTSTATIC:
                    // rewrite "x.y=v" to "x.z=(T)v"
                    if (isReferenceType(newType))
                        delegate.visitTypeInsn(CHECKCAST, newTypeInternalName);
                    delegate.visitFieldInsn(opcode, owner, newName, newTypeDescriptor);
                    return true;
                }
                return false;
            }
        };
    }

    static MemberRewriteSpec fieldToMethod(Method m) {
        final String methodName = m.getName();
        final String methodDescriptor = Type.getMethodDescriptor(m);

        boolean isGetter = m.getParameterTypes().length==0;

        if (Modifier.isStatic(m.getModifiers())) {
            if (isGetter) {
                return new MemberRewriteSpec() {
                    @Override
                    boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                        switch (opcode) {
                        case GETSTATIC:
                            // rewrite "X.y" to "(T)X.z()".
                            // we'll leave it up to HotSpot to optimize away casts
                            delegate.visitMethodInsn(INVOKESTATIC, owner, methodName, methodDescriptor);
                            Type t = Type.getType(desc);
                            if (isReferenceType(t))
                                delegate.visitTypeInsn(CHECKCAST, t.getInternalName());
                            return true;
                        }
                        return false;
                    }
                };
            } else {
                return new MemberRewriteSpec() {
                    @Override
                    boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                        switch (opcode) {
                        case PUTSTATIC:
                            // rewrite "X.y=v" to "X.z(v)"
                            // we expect the argument type to match
                            delegate.visitMethodInsn(INVOKESTATIC, owner, methodName, methodDescriptor);
                            return true;
                        }
                        return false;
                    }
                };
            }
        } else {// instance method
            if (isGetter) {
                return new MemberRewriteSpec() {
                    @Override
                    boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                        switch (opcode) {
                        case GETFIELD:
                            // rewrite "x.y" to "(T)x.z()".
                            // we'll leave it up to HotSpot to optimize away casts
                            delegate.visitMethodInsn(INVOKEVIRTUAL,owner,methodName,methodDescriptor);
                            Type t = Type.getType(desc);
                            if (isReferenceType(t))
                                delegate.visitTypeInsn(CHECKCAST, t.getInternalName());
                            return true;
                        }
                        return false;
                    }
                };
            } else {
                return new MemberRewriteSpec() {
                    @Override
                    boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                        switch (opcode) {
                        case PUTFIELD:
                            // rewrite "x.y=v" to "x.z(v)"
                            // we expect the argument type to match
                            delegate.visitMethodInsn(INVOKEVIRTUAL, owner, methodName, methodDescriptor);
                            return true;
                        }
                        return false;
                    }
                };
            }
        }
    }

    private static boolean isReferenceType(Type t) {
        return t.getSort()== ARRAY || t.getSort()== OBJECT;
    }
}
