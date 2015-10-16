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

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * ClassWriter that will lookup classes from a specified list of {@link ClassLoader}s
 */
final class ClassWriter extends org.kohsuke.asm5.ClassWriter {

    /** Our logger */
    private final static Logger LOGGER = Logger.getLogger(ClassWriter.class.getName());
    
    /** classloader to use when looking for common superclasses */
    private final ClassLoader classLoader;
    
    /**
     * {@inheritDoc}
     * @param classLoader Classloader to be searched when searching for common superclass.
     */
    public ClassWriter(ClassLoader classLoader, int flags) {
        super(flags);
        this.classLoader = classLoader;
    }

    /**
     * Returns the common super type of the two given types. The default implementation of this method <i>loads</i> the
     * two given classes from the classloader specified in the constructor and uses the java.lang.Class methods to find
     * the common super class.
     * 
     * @param type1
     *            the internal name of a class.
     * @param type2
     *            the internal name of another class.
     * @return the internal name of the common super class of the two given classes.
     */
    protected String getCommonSuperClass(final String type1, final String type2) {
        LOGGER.log(Level.FINER, "Searching for common super class of {0} and {1}", new Object[] {type1, type2});
        Class<?> c = loadClass(type1.replace('/', '.'), classLoader);
        Class<?> d = loadClass(type2.replace('/', '.'), classLoader);
        if (c.isAssignableFrom(d)) {
            return type1;
        }
        if (d.isAssignableFrom(c)) {
            return type2;
        }
        if (c.isInterface() || d.isInterface()) {
            return "java/lang/Object";
        } else {
            do {
                c = c.getSuperclass();
            } while (!c.isAssignableFrom(d));
            return c.getName().replace('.', '/');
        }
    }


    private static Class<?> loadClass(String className, Collection<? extends ClassLoader> classLoaders) {
        for (ClassLoader cl : classLoaders) {
            try {
                return Class.forName(className, false, cl);
            } catch (ClassNotFoundException e) {
                // ignored - try the next classloader
            }
        }
        // if we got here we could not load the class.
        throw new RuntimeException("java.lang.ClassNotFoundException: " + className);
    }

    private static Class<?> loadClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("java.lang.ClassNotFoundException: " + className);
        }
    }
}
