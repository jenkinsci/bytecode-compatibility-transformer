package org.jenkinsci.bytecode;

import org.kohsuke.asm3.MethodVisitor;
import org.kohsuke.asm3.Type;

import java.lang.reflect.Member;

/**
 * Adapts a single field/method.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class MemberAdapter {
    /**
     * Type that declared the member that is being rewritten.
     */
    final Type owner;

    protected MemberAdapter(Type owner) {
        this.owner = owner;
    }

    protected MemberAdapter(Class owner) {
        this.owner = Type.getType(owner);
    }

    protected MemberAdapter(Member member) {
        this(member.getDeclaringClass());
    }

    boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
        return false;
    }

    boolean visitMethodInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
        return false;
    }

    /**
     * Merges multiple {@link MemberAdapter}s that rewrite
     * different accesses to the same member.
     *
     * Used to merge setter rewrite spec to getter rewrite spec.
     *
     * TODO: improve error handling.
     */
    MemberAdapter compose(final MemberAdapter rhs) {
        if (rhs==null)  return this;

        assert this.owner.equals(rhs.owner);

        final MemberAdapter lhs = this;
        return new MemberAdapter(owner) {
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
}
