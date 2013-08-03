package org.jenkinsci.bytecode;

import org.jenkinsci.constant_pool_scanner.FieldRefConstant;

import java.lang.reflect.Member;

/**
 * {@linkplain Member#getName() Name of a member} and its descriptor.
 *
 * @author Kohsuke Kawaguchi
 */
final class NameAndType {
    final String name;
    final String descriptor;

    NameAndType(String descriptor, String name) {
        this.descriptor = descriptor;
        this.name = name;
    }

    NameAndType(FieldRefConstant ref) {
        this(ref.getDescriptor(),ref.getName());
    }

    @Override
    public boolean equals(Object o) {
        NameAndType that = (NameAndType) o;
        return descriptor.equals(that.descriptor) && name.equals(that.name);

    }

    @Override
    public int hashCode() {
        return 31*name.hashCode() + descriptor.hashCode();
    }
}
