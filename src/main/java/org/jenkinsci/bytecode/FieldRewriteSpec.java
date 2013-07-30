package org.jenkinsci.bytecode;

import org.kohsuke.asm3.MethodVisitor;
import org.kohsuke.asm3.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.kohsuke.asm3.Opcodes.*;

/**
 * Rewrites field access.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class FieldRewriteSpec {
    abstract void visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate);

    /**
     * Used to merge setter rewrite spec to getter rewrite spec.
     *
     * TODO: improve error handling.
     */
    FieldRewriteSpec compose(final FieldRewriteSpec rhs) {
        if (rhs==null)  return this;

        final FieldRewriteSpec lhs = this;
        return new FieldRewriteSpec() {
            @Override
            void visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                lhs.visitFieldInsn(opcode,owner,name,desc,delegate);
                rhs.visitFieldInsn(opcode,owner,name,desc,delegate);
            }
        };
    }

    /**
     * Rewrites a field reference to another field access.
     */
    static FieldRewriteSpec toField(Field f) {
        final String newName = f.getName();
        final Type newType = Type.getType(f.getType());
        final String newTypeDescriptor = newType.getDescriptor();
        final String newTypeInternalName = newType.getInternalName();

        return new FieldRewriteSpec() {
            @Override
            void visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                switch (opcode) {
                case GETFIELD:
                case GETSTATIC:
                    // rewrite "x.y" to "(T)x.z".
                    // we'll leave it up to HotSpot to optimize away casts
                    delegate.visitFieldInsn(opcode, owner, newName, newTypeDescriptor);
                    delegate.visitTypeInsn(CHECKCAST, Type.getType(desc).getInternalName());
                    break;
                case PUTFIELD:
                case PUTSTATIC:
                    // rewrite "x.y=v" to "x.z=(T)v"
                    delegate.visitTypeInsn(CHECKCAST, newTypeInternalName);
                    delegate.visitFieldInsn(opcode, owner, newName, newTypeDescriptor);
                    break;
                }
            }
        };
    }

    static FieldRewriteSpec toMethod(Method m) {
        final String methodName = m.getName();
        final String methodDescriptor = Type.getMethodDescriptor(m);

        boolean isGetter = m.getParameterTypes().length==0;

        if (Modifier.isStatic(m.getModifiers())) {
            if (isGetter) {
                return new FieldRewriteSpec() {
                    @Override
                    void visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                        switch (opcode) {
                        case GETSTATIC:
                            // rewrite "X.y" to "(T)X.z()".
                            // we'll leave it up to HotSpot to optimize away casts
                            delegate.visitMethodInsn(INVOKESTATIC, owner, methodName, methodDescriptor);
                            delegate.visitTypeInsn(CHECKCAST, Type.getType(desc).getInternalName());
                            break;
                        }
                    }
                };
            } else {
                return new FieldRewriteSpec() {
                    @Override
                    void visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                        switch (opcode) {
                        case PUTSTATIC:
                            // rewrite "X.y=v" to "X.z(v)"
                            // we expect the argument type to match
                            delegate.visitMethodInsn(opcode, owner, methodName, methodDescriptor);
                            break;
                        }
                    }
                };
            }
        } else {// instance method
            if (isGetter) {
                return new FieldRewriteSpec() {
                    @Override
                    void visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                        switch (opcode) {
                        case GETFIELD:
                            // rewrite "x.y" to "(T)x.z()".
                            // we'll leave it up to HotSpot to optimize away casts
                            delegate.visitMethodInsn(INVOKEVIRTUAL,owner,methodName,methodDescriptor);
                            delegate.visitTypeInsn(CHECKCAST, Type.getType(desc).getInternalName());
                            break;
                        }
                    }
                };
            } else {
                return new FieldRewriteSpec() {
                    @Override
                    void visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                        switch (opcode) {
                        case PUTFIELD:
                            // rewrite "x.y=v" to "x.z(v)"
                            // we expect the argument type to match
                            delegate.visitMethodInsn(opcode, owner, methodName, methodDescriptor);
                            break;
                        }
                    }
                };
            }
        }
    }
}
