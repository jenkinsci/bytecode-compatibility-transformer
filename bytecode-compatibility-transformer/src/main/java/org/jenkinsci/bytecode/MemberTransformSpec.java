package org.jenkinsci.bytecode;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/**
 * All the adapters of {@linkplain #kind a specific member type} keyed by their name and descriptor.
 *
 * Adapters that share the same name and the descriptor will be aggregated to a set.
 * This is because at the time of rewrite we cannot statically determine which adapter
 * should be actually effective.
 *
 * @author Kohsuke Kawaguchi
 */
final class MemberTransformSpec extends HashMap<NameAndType,Set<MemberAdapter>> {
    final Kind kind;

    MemberTransformSpec(Kind kind) {
        this.kind = kind;
    }

    /**
     * Copy constructor.
     */
    MemberTransformSpec(MemberTransformSpec rhs) {
        this.kind = rhs.kind;
        for (Entry<NameAndType,Set<MemberAdapter>> e : rhs.entrySet()) {
            put(e.getKey(),new HashSet<MemberAdapter>(e.getValue()));
        }
    }

    void addRewriteSpec(String name, Class type, MemberAdapter c) {
        NameAndType key = new NameAndType(Type.getDescriptor(type),name);

        Set<MemberAdapter> specs = get(key);
        if (specs==null)  put(key, specs = new HashSet<MemberAdapter>());

        for (MemberAdapter existing : specs) {
            if (existing.owner.equals(c.owner)) {
                // this adapter rewrites a different access to the same member
                specs.remove(existing);
                specs.add(c.compose(existing));
                return;
            }
        }

        specs.add(c);
    }

    public boolean rewrite(ClassRewritingContext context, int opcode, String owner, String name, String desc, boolean intf, MethodVisitor base) {
        Set<MemberAdapter> adapters = get(new NameAndType(desc, name));

        boolean modified = false;
        if (adapters !=null) {
            Label end = new Label();
            Label next = new Label();
            for (MemberAdapter fr : adapters) {
                base.visitLabel(next);
                next = new Label();

                context.callTypeCheckMethod(fr.owner, Type.getObjectType(owner), base);
                base.visitJumpInsn(IFEQ,next);

                // if assignable
                if (fr.adapt(context,opcode,owner,name,desc, intf, base)) {
                    modified = true;
                } else {
                    // failed to rewrite
                    kind.visit(base, opcode, owner, name, desc, intf);
                }

                base.visitJumpInsn(GOTO,end);
            }

            base.visitLabel(next);      // if this field turns out to be unrelated
            kind.visit(base, opcode, owner, name, desc, intf);

            base.visitLabel(end);   // all branches join here
        } else {
            kind.visit(base, opcode, owner, name, desc, intf);
        }

        return modified;
    }

    /**
     * Inserts a debug println into the byte code.
     */
    private void println(MethodVisitor base, String msg) {
        base.visitFieldInsn(GETSTATIC, "java/lang/System","out","Ljava/io/PrintStream;");
        base.visitLdcInsn(msg);
        base.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream","println","(Ljava/lang/String;)V", false);
    }
}
