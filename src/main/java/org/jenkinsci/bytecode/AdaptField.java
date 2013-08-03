package org.jenkinsci.bytecode;

import org.jvnet.hudson.annotation_indexer.Indexed;
import org.kohsuke.asm3.MethodVisitor;
import org.kohsuke.asm3.Type;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;
import static org.kohsuke.asm3.Opcodes.*;
import static org.kohsuke.asm3.Type.*;

/**
 * Rewrites a field reference by adapting the type of the field.
 *
 * Given byte code that refers to "OldType ClassX.fieldY", the byte code gets rewritten
 * to refer to "NewType ClassX.fieldY".
 *
 * <p>
 * The get access will be rewritten as "v = (OldType)x.fieldY",
 * the set access will be rewritten as "x.fieldY = (NewType)v".
 * At runtime, if the type of the value doesn't match up, this will result in
 * {@link ClassCastException}.
 *
 * <p>
 * For adopting the return type of the method, see a separate project "bridge method injector"
 * that achieves the goal without the need for runtime transformation.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Target({FIELD,METHOD})
@Indexed
@AdapterAnnotation(AdaptField.FactoryImpl.class)
public @interface AdaptField {
    /**
     * Name of the field that's being adapted.
     */
    String name() default "";

    /**
     * Types this field was once known as.
     *
     * Byte code that refers to this field with these types will be the subject of rewrite.
     */
    Class[] was();

    public static class FactoryImpl extends AdapterAnnotationParser {
        @Override
        void parse(TransformationSpec spec, AnnotatedElement e) {
            AdaptField af = e.getAnnotation(AdaptField.class);
            Member mem = (Member)e;

            String name = af.name();
            if (name.length()==0)   name = mem.getName(); // default to the same name

            MemberRewriteSpec mrs = null;
            if (e instanceof Field)
                mrs = fieldToField((Field) e);
            if (e instanceof Method)
                mrs = fieldToMethod((Method) e);
            assert mrs!=null;
            spec.fields.addRewriteSpec(name, af.was(), mrs);
        }

        /**
         * Rewrites a field reference to another field access.
         */
        MemberRewriteSpec fieldToField(Field f) {
            final String newName = f.getName();
            final Type newType = Type.getType(f.getType());
            final String newTypeDescriptor = newType.getDescriptor();
            final String newTypeInternalName = isReferenceType(newType) ? newType.getInternalName() : null;

            return new MemberRewriteSpec(f) {
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

        MemberRewriteSpec fieldToMethod(Method m) {
            final String methodName = m.getName();
            final String methodDescriptor = Type.getMethodDescriptor(m);

            Class<?>[] params = m.getParameterTypes();
            boolean isGetter = params.length==0;

            // in this VM, what's the actual type of the value? is it primitive (as opposed to reference?)
            final boolean actuallyPrimitive = isGetter ? m.getReturnType().isPrimitive() : params[0].isPrimitive();

            if (Modifier.isStatic(m.getModifiers())) {
                if (isGetter) {
                    return new MemberRewriteSpec(m) {
                        @Override
                        boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                            switch (opcode) {
                            case GETSTATIC:
                                Type t = Type.getType(desc);
                                boolean expectedReference = isReferenceType(t);

                                if (actuallyPrimitive^expectedReference) {
                                    // rewrite "X.y" to "(T)X.z()".
                                    // we'll leave it up to HotSpot to optimize away casts
                                    delegate.visitMethodInsn(INVOKESTATIC, owner, methodName, methodDescriptor);
                                    if (expectedReference)
                                        delegate.visitTypeInsn(CHECKCAST, t.getInternalName());
                                    return true;
                                }
                            }
                            return false;
                        }
                    };
                } else {
                    return new MemberRewriteSpec(m) {
                        @Override
                        boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                            switch (opcode) {
                            case PUTSTATIC:
                                Type t = Type.getType(desc);
                                boolean expectedReference = isReferenceType(t);

                                if (actuallyPrimitive^expectedReference) {
                                    // rewrite "X.y=v" to "X.z(v)"
                                    // we expect the argument type to match
                                    delegate.visitMethodInsn(INVOKESTATIC, owner, methodName, methodDescriptor);
                                    return true;
                                }
                            }
                            return false;
                        }
                    };
                }
            } else {// instance method
                if (isGetter) {
                    return new MemberRewriteSpec(m) {
                        @Override
                        boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                            switch (opcode) {
                            case GETFIELD:
                                Type t = Type.getType(desc);
                                boolean expectedReference = isReferenceType(t);

                                if (actuallyPrimitive^expectedReference) {
                                    // rewrite "x.y" to "(T)x.z()".
                                    // we'll leave it up to HotSpot to optimize away casts
                                    delegate.visitMethodInsn(INVOKEVIRTUAL,owner,methodName,methodDescriptor);
                                    if (expectedReference)
                                        delegate.visitTypeInsn(CHECKCAST, t.getInternalName());
                                    return true;
                                }
                            }
                            return false;
                        }
                    };
                } else {
                    return new MemberRewriteSpec(m) {
                        @Override
                        boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                            switch (opcode) {
                            case PUTFIELD:
                                Type t = Type.getType(desc);
                                boolean expectedReference = isReferenceType(t);

                                if (actuallyPrimitive^expectedReference) {
                                    // rewrite "x.y=v" to "x.z(v)"
                                    // we expect the argument type to match
                                    delegate.visitMethodInsn(INVOKEVIRTUAL, owner, methodName, methodDescriptor);
                                    return true;
                                }
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
}
