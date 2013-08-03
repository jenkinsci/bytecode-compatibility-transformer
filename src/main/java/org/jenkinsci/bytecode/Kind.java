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
    },

    METHOD {
        @Override
        void visit(MethodVisitor visitor, int opcode, String owner, String name, String desc) {
            visitor.visitMethodInsn(opcode,owner,name,desc);
        }
    };

    abstract void visit(MethodVisitor visitor, int opcode, String owner, String name, String desc);
}
