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

    /**
     * @param opcode
     *      Instruction being considered for type adapting. This is either a field or method invocation
     *      instruction.
     * @param owner
     *      Owner type of the field/method  that {@code opcode} is referring to.
     * @param desc
     *      Method/field descriptor that {@code opcode} is calling/accessing.
     * @param delegate
     *      Generate bytecode by calling this visitor.
     *
     * @return true
     *      if the instruction was rewritten. Otherwise do nothing and return false to let
     *      the caller pass {@code opcode} unmodified.
     */
    boolean adapt(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
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
            boolean adapt(int opcode, String owner, String name, String desc, MethodVisitor delegate) {
                return lhs.adapt(opcode, owner, name, desc, delegate)
                    || rhs.adapt(opcode, owner, name, desc, delegate);
            }
        };
    }
}
