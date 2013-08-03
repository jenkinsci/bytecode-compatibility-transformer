Bytecode Compatibility Transformer
==================================

This Java library provides a set of annotations and bytecode transformer that helps you evolve
modular codebase without losing compatibility.

Imagine a modular system where two parts of the code are compiled separately and linked together at runtime.
You have module Foo that exposes class `Foo`, and you have module Bar that uses it.

    class Foo {
        public static final Foo INSTANCE = new Foo();
    }

    class Bar {
        void bar() {
            System.out.println(Foo.INSTANCE);
        }
    }

Now you are refactoring the Foo module, and you want to turn this singleton field into a method call.

    class Foo {
        public static final Foo getInstance() {
            return new Foo();
        }
    }

But unfortunately, you can't do this without breaking existing versions of the Bar module, since their
bytecode already has a reference to `Foo.INSTANCE` baked in.

This library helps you resolve this situation.

When you refactor the code, you put an annotation to the newly introduced method, signaling the fact
that a field reference to `INSTANCE` should resolve to a method call `getInstance()`:

    class Foo {
        @AdaptField(name="INSTANCE",was=Foo.class)
        public static Foo getInstance() {
            return new Foo();
        }
    }

In the module system where we load Foo and Bar, you then construct a `Transformer` and loads up
the definitions.

    import org.jenkinsci.bytecode.Transformer;
    Transformer t = new Transformer();
    t.loadRules(fooClassLoader);

The `loadRules` method call takes a `ClassLoader`, and looks for all the use of `AdaptField` annotations,
which is automatically indexed at compile-time through annotation processor.

The `Transformer` class has `byte[] transform(final String className, byte[] image)` method that transforms
bytecode according to the rules. You'll have to use this when loading the Bar module. This depends on the
module system in question, but for example, with `AntClassLoader` in Ant you can do the following:

    AntClassLoader cl = new AntClassLoader() {
        @Override
        protected Class<?> defineClassFromData(File container, byte[] classData, String className) throws IOException {
            return super.defineClassFromData(container, t.transform(className,classData), className);
        }
    };

Using This Library
==================

The Maven coordinates is as follows:

    <dependency>
      <groupId>org.jenkins-ci</groupId>
      <artifactId>bytecode-compatibility-transformer</artifactId>
      <version>1.3</version>
    </dependency>

    <repositories>
      <repository>
        <id>repo.jenkins-ci.org</id>
        <url>http://repo.jenkins-ci.org/public/</url>
      </repository>
    </repositories>


What Can Be Adapted
===================

You can adapt both instance and static field access. You can adapt to a field access to
either getter/setter methods or to another field.

A static field access must be adapted to another static field access, or static methods.
Similarly, An instance field access must be adapter to another instance field access, or instance methods.

When adapting a field to a method call, you can choose to only have a setter without any getter, as
in the first example above. In this case, an attempt to set to a field will not be rewritten, and therefore
it will result in `NoSuchFieldError`.

If you want to support both read/write, then you need to create a getter/setter method pair and annotate
both methods:

    class Foo {
        @AdaptField(name="INSTANCE",was=Foo.class)
        public static Foo getInstance() {
            return new Foo();
        }

        @AdaptField(name="INSTANCE",was=Foo.class)
        public static void setInstance(Foo foo) {
            ...
        }
    }

Adapting type
-------------
When you adapt a field of a reference type, you can choose a different type.
This is convenient when you change the field type to a subtype of what it used to be:

    // v1
    class Foo {
        public static final Foo INSTANCE = ...;
    }

    // v2
    class abstract Foo {
        @AdaptField(was=Foo.class)
        public static final FooImpl INSTANCE = ...;
    }

When you do this, a necessary cast operator is inserted both during get and set.
This allows you to adapt any reference type to any other reference type, not just to a subtype,
but obviously the actual execution of the code can fail with `ClassCastException`.


Sibling Projects
================
For adapting methods, see [bridge method injector](http://bridge-method-injector.infradna.com/) that provides
a related functionality but without a need for runtime class transformation.