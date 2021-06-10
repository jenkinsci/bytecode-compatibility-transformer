package org.jenkinsci.bytecode;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

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
     * Rewrites an instruction to perform type adoption if necessary.
     *
     * @param opcode
     *      Instruction being considered for type adapting. This is either a field or method invocation
     *      instruction.
     * @param owner
     *      Owner type of the field/method  that {@code opcode} is referring to.
     * @param desc
     *      Method/field descriptor that {@code opcode} is calling/accessing.
     * @param intf
     *      Used for method calls only. if the method's owner class is an interface.
     * @param delegate
     *      Generate bytecode by calling this visitor.
     *
     * @return true
     *      if the instruction was rewritten. Otherwise do nothing and return false to let
     *      the caller pass {@code opcode} unmodified.
     */
    boolean adapt(ClassRewritingContext context, int opcode, String owner, String name, String desc, boolean intf, MethodVisitor delegate) {
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
            boolean adapt(ClassRewritingContext context, int opcode, String owner, String name, String desc, boolean intf, MethodVisitor delegate) {
                return lhs.adapt(context, opcode, owner, name, desc, intf, delegate)
                    || rhs.adapt(context, opcode, owner, name, desc, intf, delegate);
            }
        };
    }
}
