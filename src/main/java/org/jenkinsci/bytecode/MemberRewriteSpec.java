package org.jenkinsci.bytecode;

import org.kohsuke.asm3.MethodVisitor;

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
}
