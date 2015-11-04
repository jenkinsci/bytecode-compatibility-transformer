/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
*/
package org.jenkinsci.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.bytecode.helper.ClassLoadingReferenceTypeHierachyReader;
import org.kohsuke.asm5.ClassReader;
import org.kohsuke.asm5.ClassVisitor;
import org.kohsuke.asm5.Opcodes;
import org.kohsuke.asm5.Type;
import org.kohsuke.asm5.xml.ASMContentHandler;


/**
 * ClassWriter that will lookup classes from a specified list of {@link ClassLoader}s when searching for a common supertype.
 * @see #getCommonSuperClass(String, String)
 */
final class NonClassLoadingClassWriter extends org.kohsuke.asm5.ClassWriter {

    /** Our logger */
    private final static Logger LOGGER = Logger.getLogger(NonClassLoadingClassWriter.class.getName());
    
    /** classloader to use when looking for common superclasses */
    private final ClassLoader classLoader;

    /** the name of the Object.class */
    private static final String OBJECT_CLASS_DEF = "java/lang/Object";

    /**
     * {@inheritDoc}
     * @param classLoader Classloader to be searched when searching for common superclass.
     */
    public NonClassLoadingClassWriter(ClassLoader classLoader, int flags) {
        super(flags);
        this.classLoader = classLoader;
    }

    /**
     * Returns the common super type of the two given types. The implementation of this method <i>loads</i> class definitions and uses ASM to inspect the code to find
     * the common super class.
     * 
     * @param type1
     *            the internal name of a class.
     * @param type2
     *            the internal name of another class.
     * @return the internal name of the common super class of the two given classes.
     */
    @Override
    protected String getCommonSuperClass(final String type1, final String type2) {
        ClassLoadingReferenceTypeHierachyReader hr = new ClassLoadingReferenceTypeHierachyReader(classLoader);
        return hr.getCommonSuperClass(type1, type2);
    }

}
