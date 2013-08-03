package org.jenkinsci.bytecode;

import org.kohsuke.asm3.MethodVisitor;

/**
 * Rewriting a method reference and a field reference takes a very similar code path,
 * but we eventually need to do certain things differently.
 *
 * This strategy patterns abstracts that away.
 *
 * @author Kohsuke Kawaguchi
 */
enum Kind {
    FIELD {
        @Override
        void visit(MethodVisitor visitor, int opcode, String owner, String name, String desc) {
            visitor.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        boolean visit(MemberRewriteSpec mrs, int opcode, String owner, String name, String desc, MethodVisitor delegate) {
            return mrs.visitFieldInsn(opcode,owner,name,desc,delegate);
        }
    },

    METHOD {
        @Override
        void visit(MethodVisitor visitor, int opcode, String owner, String name, String desc) {
            visitor.visitMethodInsn(opcode,owner,name,desc);
        }

        @Override
        boolean visit(MemberRewriteSpec mrs, int opcode, String owner, String name, String desc, MethodVisitor delegate) {
            return mrs.visitMethodInsn(opcode,owner,name,desc,delegate);
        }
    };

    abstract void visit(MethodVisitor visitor, int opcode, String owner, String name, String desc);

    abstract boolean visit(MemberRewriteSpec mrs, int opcode, String owner, String name, String desc, MethodVisitor delegate);
}
