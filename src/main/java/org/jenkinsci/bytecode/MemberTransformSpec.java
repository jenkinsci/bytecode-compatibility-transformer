package org.jenkinsci.bytecode;

import org.kohsuke.asm3.Type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

/**
 * All the rewrites of {@linkplain #kind a specific member type} keyed by their name and descriptor.
 *
 * Different rewrite
 *
 * @author Kohsuke Kawaguchi
 */
final class MemberTransformSpec extends HashMap<NameAndType,Set<MemberRewriteSpec>> {
    final Kind kind;

    MemberTransformSpec(Kind kind) {
        this.kind = kind;
    }

    /**
     * Copy constructor.
     */
    MemberTransformSpec(MemberTransformSpec rhs) {
        this.kind = rhs.kind;
        for (Entry<NameAndType,Set<MemberRewriteSpec>> e : rhs.entrySet()) {
            put(e.getKey(),new HashSet<MemberRewriteSpec>(e.getValue()));
        }
    }

    void addRewriteSpec(String name, Class[] types, MemberRewriteSpec c) {
        OUTER:
        for (Class type : types) {
            NameAndType key = new NameAndType(Type.getDescriptor(type),name);

            Set<MemberRewriteSpec> specs = get(key);
            if (specs==null)  put(key, specs = new HashSet<MemberRewriteSpec>());

            for (MemberRewriteSpec existing : specs) {
                if (existing.owner.equals(c.owner)) {
                    // this transformer rewrites different access to the same member
                    specs.remove(existing);
                    specs.add(c.compose(existing));
                    continue OUTER;
                }
            }

            specs.add(c);
        }
    }
}
