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
import org.kohsuke.asm5.ClassReader;
import org.kohsuke.asm5.ClassVisitor;
import org.kohsuke.asm5.Opcodes;
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
        LOGGER.log(Level.FINER, "Searching for common super class of {0} and {1}", new Object[] {type1, type2});
        // try and shortcut so we don't have to load and parse class data with ASM
        if (type1.equals(type2)) {
            // if the classes are equal then the common superclass is the class itself.
            return type1;
        }
        if (type1.equals(OBJECT_CLASS_DEF) || type2.equals(OBJECT_CLASS_DEF)) {
            // if either is java.lang.Object then java.lang.Object must be the common super class.
            return OBJECT_CLASS_DEF;
        }

        /**
         *  To find the common super class we must: 
         *  1) load the class definition (not the actual class - otherwise we can end up in circles...)
         *  2) check if we have already seen that class - if so that is the common class!
         *  3) get the super class and goto 1 (until there are no super classes).
         *  4) do the same for the other class.
         */
        ClassReader type1Reader = getClassReader(type1, classLoader);
        ClassReader type2Reader = getClassReader(type2, classLoader);
        
        Set<String> seenClasses = new HashSet<String>();

        /* 
         * Interfaces like "interface FooBar extends Foo" have a superclass of Object not of type Foo.
         * yet we require to know about Foo - so need to jump though some extra hoops.
         * but only if this is the type passed in.
         */
        if (isRepreseningInterface(type1Reader)) {
            seenClasses.add(type1Reader.getClassName());
            String[] interfaces = type1Reader.getInterfaces();
            if (interfaces != null) {
                for (String s : interfaces) {
                    System.out.println("adding " + s);
                    seenClasses.add(s);
                }
            }
            seenClasses.add(OBJECT_CLASS_DEF);
        }
        else {
            while (type1Reader != null) {
                // populate the class hierarchy of type1
                seenClasses.add(type1Reader.getClassName());
                type1Reader = getSuperClassReader(type1Reader, classLoader);
            }
        }
        if (isRepreseningInterface(type2Reader)) {
            seenClasses.add(type2Reader.getClassName());
            String[] interfaces = type2Reader.getInterfaces();
            if (interfaces != null) {
                for (String s : interfaces) {
                    System.out.println("adding " + s);
                    if (!seenClasses.add(s)) {
                        return s;
                    }
                }
            }
            // if we get here the super class can only be an object so no point adding it!
            return OBJECT_CLASS_DEF;
        }
        else {
            while (type2Reader != null) {
                // check the hierarchy of type2
                System.out.println("adding " + type2Reader.getClassName());
                if (!seenClasses.add(type2Reader.getClassName())) {
                    // we have already seen this class so this is the common one!
                    return type2Reader.getClassName();
                }
                type2Reader = getSuperClassReader(type2Reader, classLoader);
            }
        }
        // we should never get here as java.lang.Object is the root of everything!
        throw new RuntimeException("No common superclass found between " + type1 + " and " + type2 + " which is impossible as java.lang.Object should always be common.");
    }

    // only here for debugging - remove it.
    protected String getCommonSuperClassOLD(final String type1, final String type2) {
        return super.getCommonSuperClass(type1, type2);
    }

    private static ClassReader getClassReader(String className, ClassLoader classLoader) {
        try {
            InputStream resourceAsStream = classLoader.getResourceAsStream(className + ".class");
            if (resourceAsStream == null) {
                throw new RuntimeException("java.lang.ClassNotFoundException: " + className);
            }
            try {
                byte[] classDefinition = IOUtils.toByteArray(resourceAsStream);
                return new ClassReader(classDefinition);
            }
            finally {
                resourceAsStream.close();
            }
        } catch (IOException ex) {
            throw new RuntimeException("java.lang.ClassNotFoundException: " + className, ex);
        }
    }


    private static ClassReader getSuperClassReader(ClassReader reader, ClassLoader classLoader) {
        String superClassName = reader.getSuperName();
        if (superClassName == null) {
            return null;
        }
        return getClassReader(superClassName, classLoader);
    }

    /**
     * Inspect the class data represented by reader to determine if it represents an interface.
     * @param reader the reader representing the class to test.
     * @return {@code true} iff the class data represented by this reader is an interface.
     */
    private static boolean isRepreseningInterface(ClassReader reader) {
        return (reader.getAccess() & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE;
    }

}
