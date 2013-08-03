package org.jenkinsci.bytecode;

import org.kohsuke.asm3.MethodVisitor;
import org.kohsuke.asm3.Type;

import java.lang.reflect.Member;

/**
 * Rewrites field access.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class MemberRewriteSpec {
    /**
     * Type that declared the member that is being rewritten.
     */
    final Type owner;

    protected MemberRewriteSpec(Type owner) {
        this.owner = owner;
    }

    protected MemberRewriteSpec(Class owner) {
        this.owner = Type.getType(owner);
    }

    protected MemberRewriteSpec(Member member) {
        this(member.getDeclaringClass());
    }

    boolean visitFieldInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
        return false;
    }

    boolean visitMethodInsn(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
        return false;
    }

    /**
     * Merges multiple {@link MemberRewriteSpec}s that rewrite
     * different accesses to the same member.
     *
     * Used to merge setter rewrite spec to getter rewrite spec.
     *
     * TODO: improve error handling.
     */
    MemberRewriteSpec compose(final MemberRewriteSpec rhs) {
        if (rhs==null)  return this;

        assert this.owner.equals(rhs.owner);

        final MemberRewriteSpec lhs = this;
        return new MemberRewriteSpec(owner) {
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
