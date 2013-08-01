package org.jenkinsci.bytecode;

import java.util.HashMap;
import java.util.Map;

/**
 * All the references into this class that require a rewrite.
 */
class ClassRewriteSpec {
    final String internalName;

    /**
     * Fields to rewrite. From the name of the field to its correct descriptor.
     */
    final Map<String,MemberRewriteSpec> fields = new HashMap<String,MemberRewriteSpec>();

    /**
     * Methods to rewrite. From the name of the method to its correct descriptor.
     */
    final Map<String,MemberRewriteSpec> methods = new HashMap<String,MemberRewriteSpec>();

    public ClassRewriteSpec(String internalName) {
        this.internalName = internalName;
    }
}
