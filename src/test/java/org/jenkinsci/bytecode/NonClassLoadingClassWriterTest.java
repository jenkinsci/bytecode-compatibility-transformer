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

import org.junit.Test;
import org.kohsuke.asm5.Opcodes;

import static org.junit.Assert.assertThat;
import static org.hamcrest.core.Is.is;



public class NonClassLoadingClassWriterTest {

    @Test
    public void testCommonSuperClassInterface() {
        assertCommonSuperClass(HashSet.class, Set.class, Set.class);
        assertCommonSuperClass(HashSet.class, SortedSet.class, Object.class);
        assertCommonSuperClass(SortedSet.class, HashSet.class, Object.class);
        assertCommonSuperClass(SortedSet.class, Set.class, Set.class);
        assertCommonSuperClass(TreeSet.class, SortedSet.class, SortedSet.class);
        assertCommonSuperClass(SortedSet.class, TreeSet.class, SortedSet.class);
    }

    @Test
    public void testCommonSuperClassObject() {
        assertCommonSuperClass(Inet4Address.class, Inet6Address.class, InetAddress.class);
        assertCommonSuperClass(Inet6Address.class, Inet4Address.class, InetAddress.class);
    }

    @Test
    public void testNoCommonSuperClassObject() {
        assertCommonSuperClass(Inet4Address.class, HashSet.class, Object.class);
        assertCommonSuperClass(HashSet.class, Inet4Address.class, Object.class);
    }

    @Test
    public void testNoCommonSuperClassInterface() {
        assertCommonSuperClass(Inet4Address.class, Set.class, Object.class);
        assertCommonSuperClass(Set.class, Inet4Address.class, Object.class);
    }

    @Test
    public void test2NoCommonSuperClassInterface() {
        assertCommonSuperClass(NavigableSet.class, BeanContext.class, Object.class);
        assertCommonSuperClass(BeanContext.class, NavigableSet.class, Object.class);
    }

    
    /**
     * Checks that the 
     */
    public void assertCommonSuperClass(Class<?> class1, Class<?> class2, Class<?> expectedSuperClass) {
        NonClassLoadingClassWriter writer = new NonClassLoadingClassWriter(this.getClass().getClassLoader(), Opcodes.ASM5);
        String superClass = writer.getCommonSuperClass(class1.getName().replace('.', '/'), class2.getName().replace('.', '/'));
        assertThat(String.format("Common superclass for %s and %s is incorrect", class1.getName(), class2.getName()), 
                   superClass, is(expectedSuperClass.getName().replace('.', '/')));
        
    }
}
