package fr.umlv.smalljs;

import java.io.PrintStream;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.WrongMethodTypeException;

public class MethodHandleExamples {
  public static void main(String[] args) throws Throwable {
    // a method type, represent the return type and the parameter types of a function
    // By example, a method type that takes a String and returns void
    MethodType mt1 = MethodType.methodType(void.class, String.class);
    System.out.println(mt1);  // (String)void

    // and a method type that takes two ints and return a String
    var mt2 = MethodType.methodType(String.class, int.class, int.class);
    System.out.println(mt2);  // (int,int)String

    // Method has a static factory when only objects are used as parameters and return type
    var mt3 = MethodType.genericMethodType(3);
    System.out.println(mt3);  // (Object,Object,Object)Object


    // a method handle is a function pointer
    // to create one we need to first creates a Lookup object
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    // then we can ask for a fonction pointer to a method
    MethodHandle mh1;
    try {
      mh1 = lookup.findVirtual(String.class, "length",
          MethodType.methodType(int.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // if the method does not exist, NoSuchMethodException is thrown
      // if the method is not visible for the Lookup, IllegalAccessException is thrown
      // Usually, it should not hapen, so it's find to wrap then into an AssertionError

      throw new AssertionError(e);
    }

    // once created, a method handle as a method type
    System.out.println(mh1.type());  // (String)int

    // You can notice that the *method* is String::length but the corresponding *function*
    // takes a String and returns an int (String is the type of this)

    // to get a function pointer from a static method, we can use Lookup.findStatic
    MethodHandle mh2;
    try {
      mh2 = lookup.findStatic(Double.class, "parseDouble",
          MethodType.methodType(double.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      // if the method does not exist, NoSuchMethodException is thrown
      // if the method is not visible for the Lookup, IllegalAccessException is thrown
      // Usually, it should not hapen, so it's find to wrap then into an AssertionError

      throw new AssertionError(e);
    }

    // in that case, the corresponding method type, has no type for 'this'
    System.out.println(mh2.type());  // (String)double

    // to call the function pointer, we use the method "invokeExact"
    var aLength = (int) mh1.invokeExact("foo");
    System.out.println(aLength);  // 3

    // or
    var aDouble = (double) mh2.invokeExact("2.0");
    System.out.println(aDouble);  // 2.0

    // given that a MethodHandle can represent any functions, invokeExact can call any functions
    // if the argument types are not exactly the same as the parameter types
    // invokeExact throws a WrongMethodTypeException
    try {
      mh1.invokeExact(42);
    } catch (WrongMethodTypeException e) {
      // oops handle's method type (String)int but found (int)void
      System.out.println("oops " + e.getMessage());
    }

    // The return argument as to be specified, as a cast for the compiler
    // if the cat is not present, the compiler assumes 'void'
    // So the following code does not work at runtime
    try {
      mh1.invokeExact("foo");
    } catch (WrongMethodTypeException e) {
      // oops handle's method type (String)int but found (String)void
      System.out.println("oops " + e.getMessage());
    }

    // There is a slower way to call the method handle, using invoke
    // instead of invokeExact, in that case the argument and return value are copnverted
    // but the method call is slower
    Object barLength = mh1.invoke("bar");
    System.out.println(barLength);  // 3 (as a java.lang.Integer)

    // There idea of the package java.lang.invoke, is that the creation of a MethodHandle
    // is not fast but calling a method handle with invokeExact is fast
    // So if we want to convert the result of a call to String::length to an Object,
    // instead of using "invoke" as above, it's better to tranform the method handle to
    // another that return an Object
    var mh3 = mh1.asType(MethodType.methodType(Object.class, String.class));
    Object whizzLength = (Object) mh3.invokeExact("whizz");
    System.out.println(whizzLength);  // 5 as an Integer

    // a method that ends with ... (a varargs) has a special treatment
    // the method type uses Object[] but the method isVarargsCollector() returns true
    class VarargsExample {
      private static void print(Object... values) {}
    }
    var mh4 = lookup.findStatic(VarargsExample.class, "print",
        MethodType.methodType(void.class, Object[].class));
    System.out.println(mh4.type());  // (Object[])void
    System.out.println(mh4.isVarargsCollector());  // true

    // sadly, calling asType() or any other other methods that transforms the method handle
    // on a varargs make it losing the bit of information that it was a varargs
    // by example
    var mh5 = mh4.asType(MethodType.methodType(int.class, Object[].class));
    System.out.println(mh5.type());  // (Object[])int
    System.out.println(mh5.isVarargsCollector());  // false

    // if a method handle last parameter is an array, it can be seen as a varargs
    var mh6 = mh5.asVarargsCollector(Object[].class);
    System.out.println(mh6.isVarargsCollector());  // false

    // withVarargs marche comme asVarargsCollector mais est plus simple d'utilisation
    var mh7 = mh5.withVarargs(true);
    System.out.println(mh7.isVarargsCollector());  // false

    // so if a transformation is done on a method handle and the method handle was a varargs
    // withVarargs(true) must be called to re-enable its varargs status

    // The method handle API has two ways to do partial application
    // (i.e. provide a value for some parameters)
    // By example, with a fonction that takes two parameters
    class PartialApplicationExample {
      static char characterAtPosition(String s, int position) {
        return s.charAt(position);
      }
    }
    var mh8 = lookup.findStatic(PartialApplicationExample.class, "characterAtPosition",
        MethodType.methodType(char.class, String.class, int.class));

    // the method bindTo() set the first parameter, if it's an object
    var mh9 = mh8.bindTo("hello");
    System.out.println(mh9.type());  // (int)char
    System.out.println((char) mh9.invokeExact(1));  // 'e'

    // the method MethodHandles.insertArguments(mh, pos, values...)
    // works with any values
    var mh10 = MethodHandles.insertArguments(mh8, 0, "foobar", 3);
    System.out.println(mh10.type());  // ()char
    System.out.println((char) mh10.invokeExact());  // 'b'

    // here is another example, getting PrintStream::println and doing a partial application with System.out
    var mh11 = lookup.findVirtual(PrintStream.class, "println",
        MethodType.methodType(void.class, int.class));
    var mh12 = mh11.bindTo(System.out);
    mh12.invokeExact(42);  // 42

    // An astute reader would notice that the method that transform a method handle are
    // either defined in the class MethodHandle as instance method (for the 'code' methods) or
    // in the class MethodHandles (with an 's' at the end) as static methods


    // Using the partial application on the identity function, give us a function that returns
    // a constant. The API aleady provides that
    var mh13 = MethodHandles.constant(int.class, 42);
    System.out.println((int) mh13.invokeExact());  // 42


    // We can also change the value of the parameters by applying another function
    // using MethodHandles.filterArguments(mh, pos, filterMhs...)
    class FilterExample {
      static int filter(int x) { return 2 * x; }

      static void print(int value) {
        System.out.println(value);
      }
    }
    var printInt = lookup.findStatic(FilterExample.class, "print",
        MethodType.methodType(void.class, int.class));
    var filter = lookup.findStatic(FilterExample.class, "filter",
        MethodType.methodType(int.class, int.class));
    var mh14 = MethodHandles.filterArguments(printInt, 0, filter);
    mh14.invokeExact(21);  // 42

    // We can also filter the return value, using MethodHandles.filterReturValue(mh, filterMH)
    class FilterReturnValueExample {
      static int f() {
        return 21;
      }
    }
    var f = lookup.findStatic(FilterReturnValueExample.class, "f",
        MethodType.methodType(int.class));
    var mh15 = MethodHandles.filterReturnValue(f, filter);
    System.out.println((int) mh15.invokeExact());  // 42


    // We can create fake parameters using MethodHandles.dropArguments()
    class DropArgumentExample {
      static void print(Object value) {
        System.out.println(value);
      }
    }
    var printObject = lookup.findStatic(DropArgumentExample.class, "print",
        MethodType.methodType(void.class, Object.class));
    var mh16 = MethodHandles.dropArguments(printObject, 0, String.class, int.class);
    System.out.println(mh16.type());  // (String,int,Object)void
    mh16.invokeExact("foo", 13, (Object) "bar");  // bar


    // We can emulate an if ... else with 3 method handles, the test, the target (run if the test is true)
    // and the fallback (run if the test is false) using MethodHandles.guardWithTest(test, target, fallback)
    class GuardWithTestExample {
      static boolean test(Object o) {
        return o == null;
      }
      static int f1(Object o) { return 3; }
      static int f2(Object o) { return 4; }
    }
    var test = lookup.findStatic(GuardWithTestExample.class, "test",
        MethodType.methodType(boolean.class, Object.class));
    var f1 = lookup.findStatic(GuardWithTestExample.class, "f1",
        MethodType.methodType(int.class, Object.class));
    var f2 = lookup.findStatic(GuardWithTestExample.class, "f2",
        MethodType.methodType(int.class, Object.class));
    var guard = MethodHandles.guardWithTest(test, f1, f2);
    System.out.println(guard.type());  // (Object)int
    System.out.println((int) guard.invokeExact((Object) "foo"));  // 4

    // guardWithTest only works if the test, the target and the fallback have excatly the same parameter types
    // if this is not the case, MethodHandles.dropArguments must be used


    // MethodHandles.foldArguments(fun, combine) calls combine and insert the return value as first argument of fun
    class FoldExample {
      static int add(int a, int b) {
        return a + b;
      }
      static void fun(int result, int a, int b) {
        System.out.println("a:" + a + " b:" + b + " result:" + result);
      }
    }
    var add = lookup.findStatic(FoldExample.class, "add",
        MethodType.methodType(int.class, int.class, int.class));
    var fun = lookup.findStatic(FoldExample.class, "fun",
        MethodType.methodType(void.class, int.class, int.class, int.class));
    var mh17 = MethodHandles.foldArguments(fun, add);
    mh17.invokeExact(1, 2);  // a:1 b:2 result:3


    // In terms of optimization, the VM optimizes any method handle which is a constant
    // There are two ways to create a constant method handle
    // First, store it in a static final field
    class StaticFinalExample {
      static final MethodHandle ADD;
      static {
        var lookup = MethodHandles.lookup();
        try {
          ADD = lookup.findStatic(StaticFinalExample.class, "add",
              MethodType.methodType(int.class, int.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
          throw new AssertionError(e);
        }
      }

      private static int add(int a, int b) {
        return a + b;
      }
    }
    // this call is optimized as add(1, 2)
     System.out.println((int) StaticFinalExample.ADD.invokeExact(1, 2));  // 3

    // the other solution is to use a ConstantCallSite or a MutableCallSite
    // and then ask for it's dynamicInvoker.
    class ConstantCallSiteExample {
      static final MethodHandle MH;
      static {
        MH = new ConstantCallSite(MethodHandles.constant(Object.class, 42))
            .dynamicInvoker();
      }
    }
    // this is equivalent to (Integer) 42 but as a constant
     System.out.println( (Object)ConstantCallSiteExample.MH.invokeExact());  // 42


    // A mutable call site allows to write self modified code,
    // here the first call takes the argument and return it as return value, all subsequent calls
    // return the first value
    class Cache extends MutableCallSite {
      static final MethodHandle INITIALIZE;

      static {
        var lookup = MethodHandles.lookup();
        try {
          INITIALIZE = lookup.findVirtual(Cache.class, "initialize",
              MethodType.methodType(int.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
          throw new AssertionError(e);
        }
      }

      public Cache() {
        super(MethodType.methodType(int.class, int.class));
        // set the target to call initialize the first time
        setTarget(INITIALIZE.bindTo(this));
      }

      private int initialize(int value) {
        System.out.println("initialize is called once with " + value);
        // change the target so the same constant is returned every time
        var constant = MethodHandles.constant(int.class, value);
        var target = MethodHandles.dropArguments(constant, 0, int.class);
        setTarget(target);
        return value;
      }

      static final MethodHandle MH = new Cache().dynamicInvoker();
    }

    System.out.println( (int) Cache.MH.invokeExact(12));  // 12
    System.out.println( (int) Cache.MH.invokeExact(42));  // 12
    System.out.println( (int) Cache.MH.invokeExact(99));  // 12


    // There is a better way to write the code above
    // instead of returning the value initialize should return a method handle,
    // so it works with any method handles
    // Also using a MethodHandle.exactInvoker avoid that the method initialize is visible on the stack trace
    class Cache2 extends MutableCallSite {
      static final MethodHandle INITIALIZE;

      static {
        var lookup = MethodHandles.lookup();
        try {
          INITIALIZE = lookup.findVirtual(Cache2.class, "initialize",
              MethodType.methodType(MethodHandle.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
          throw new AssertionError(e);
        }
      }

      public Cache2() {
        super(MethodType.methodType(int.class, int.class));
        // set the target to call initialize the first time
        var initialize = INITIALIZE.bindTo(this);
        var exactInvoker = MethodHandles.exactInvoker(type());
        setTarget(MethodHandles.foldArguments(exactInvoker, initialize));
      }

      private MethodHandle initialize(int value) {
        System.out.println("initialize is called once with " + value);
        // change the target so the same constant is returned every time
        var constant = MethodHandles.constant(int.class, value);
        var target = MethodHandles.dropArguments(constant, 0, int.class);
        setTarget(target);
        return target;
      }

      static final MethodHandle MH = new Cache2().dynamicInvoker();
    }

    System.out.println( (int) Cache2.MH.invokeExact(17));  // 17
    System.out.println( (int) Cache2.MH.invokeExact(42));  // 17
    System.out.println( (int) Cache2.MH.invokeExact(99));  // 17
  }
}
