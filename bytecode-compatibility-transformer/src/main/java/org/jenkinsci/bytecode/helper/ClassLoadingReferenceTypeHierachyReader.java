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
package org.jenkinsci.bytecode.helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

/**
 * A {@link TypeHierarchyReader} that uses a given ClassLoader to locate class definitions.
 * Like its super class this class does not load the class nor does it provide any caching - 
 * but it uses the specified ClassLoader in order to find the byte-code.
 */
public class ClassLoadingReferenceTypeHierachyReader extends TypeHierarchyReader {

    /** The ClassLoader used to locate the byte-code to parse. */
    private ClassLoader classLoader;
    
    /**
     * 
     * @param classLoader the {@link ClassLoader} used to locate the byte-code to parse.
     */
    public ClassLoadingReferenceTypeHierachyReader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Returns a {@link ClassReader} instance which has read the class file represented by the {@link Type} t.
     * This implementation returns a {@link ClassReader} which has been initialised with the class data located using
     * the classLoader provided in the constructor.
     */
    @Override
    protected ClassReader reader(Type t) throws IOException {
        URL url = classLoader.getResource(t.getInternalName() + ".class");
        if (url == null) {
            throw new RuntimeException("java.lang.ClassNotFoundException: " + t.getClassName());
        }
        byte[] classDefinition = IOUtils.toByteArray(url);
        return new ClassReader(classDefinition);
    }
    
}
