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



import java.beans.beancontext.BeanContext;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.hamcrest.core.IsEqual;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.Assert.assertThat;
import static org.hamcrest.core.Is.is;



public class NonClassLoadingClassWriterTest {

    @Test
    public void testCommonSuperClassInterface() {
        assertCommonSuperClass(HashSet.class, Set.class); // Set.class
        assertCommonSuperClass(HashSet.class, SortedSet.class); // Object.class
        assertCommonSuperClass(SortedSet.class, Set.class); // Set.class
        assertCommonSuperClass(TreeSet.class, SortedSet.class); // SortedSet.class
    }

    @Test
    public void testCommonSuperClassObject() {
        assertCommonSuperClass(Inet4Address.class, Inet6Address.class); //InetAddress.class
    }

    @Test
    public void testNoCommonSuperClassObject() {
        assertCommonSuperClass(Inet4Address.class, HashSet.class); // Object.class
    }

    @Test
    public void testNoCommonSuperClassInterface() {
        assertCommonSuperClass(Inet4Address.class, Set.class); // Object.class
    }

    @Test
    public void test2NoCommonSuperClassInterface() {
        assertCommonSuperClass(NavigableSet.class, BeanContext.class); // Object.class
    }

    
    /**
     * Checks that the 
     */
    public void assertCommonSuperClass(Class<?> class1, Class<?> class2) {
        NonClassLoadingClassWriter writerUnderTest = new NonClassLoadingClassWriter(NonClassLoadingClassWriterTest.class.getClassLoader(), Opcodes.ASM9);
        OrgClassWriter orgWriter = new OrgClassWriter(Opcodes.ASM9);

        String cls1 = class1.getName().replace('.', '/');
        String cls2 = class2.getName().replace('.', '/');

        String superClassA = writerUnderTest.getCommonSuperClass(cls1, cls2);
        String superClassB = writerUnderTest.getCommonSuperClass(cls2, cls1);

        assertThat(String.format("common superclass for %s and %s is not reflective.", class1.getName(), class2.getName()), 
                   superClassA, is(superClassB));
        assertThat(String.format("Common superclass for %s and %s is incorrect", class1.getName(), class2.getName()), 
                           superClassA, is(orgWriter.getCommonSuperClass(cls1, cls2)));

    }

    /**
     * Class that extends the upstream ClassWriter purely so we can access #getCommonSuperClass for testing.
     */
    static class OrgClassWriter extends ClassWriter {

        public OrgClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return super.getCommonSuperClass(type1, type2);
        }
    }
}
