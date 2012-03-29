package com.devexperts.aprof;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Dmitry Paraschenko
 */
public class IterationSpeedTest {
    private static ThreadLocal<AtomicInteger> integer = new ThreadLocal<AtomicInteger>();
    private static int invPoint;
    private static int invMethod;
    private static int location;

    public static void main(String[] args) throws IOException {
        AProfRegistry.init(new Configuration(), new ClassNameResolver() {
            public String resolve(String id) {
                return id;
            }
        });
        invPoint = AProfRegistry.registerLocation(IterationSpeedTest.class.getCanonicalName() + ".testAProf");
        invMethod = AProfRegistry.registerLocation(Integer.class.getCanonicalName() + ".valueOf");
        location = AProfRegistry.registerAllocationPoint(Integer.class.getCanonicalName(), Integer.class.getCanonicalName() + ".valueOf");

        testNonAprof(1000000);
        testAprof(1000000);

        int length = 100000000;
        
        long time = System.currentTimeMillis();
        testNonAprof(length);
        System.out.printf("nonaprof=%d\n", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        testAprof(length);
        System.out.printf("aprof=%d\n", System.currentTimeMillis() - time);


        System.out.println();


        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            AProfRegistry.getLocationStack();
        System.out.printf("stack=%d\n", System.currentTimeMillis() - time);

        LocationStack stack = AProfRegistry.getLocationStack();

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            stack.addInvocationPoint(invPoint);
        System.out.printf("markP-stack=%d\n", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            stack.removeInvocationPoint();
        System.out.printf("unmarkP-stack=%d\n", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            stack.addInvokedMethod(invMethod);
        System.out.printf("markM-stack=%d\n", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            stack.removeInvokedMethod();
        System.out.printf("unmarkM-stack=%d\n", System.currentTimeMillis() - time);

//        time = System.currentTimeMillis();
//        for (int i = 0; i < length; i++)
//            AProfOps.allocate(location);
//        System.out.printf("allocate=%d\n", System.currentTimeMillis() - time);

        System.out.println();

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            AProfOps.markInvocationPoint(invPoint);
        System.out.printf("markP=%d\n", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            AProfOps.unmarkInvocationPoint();
        System.out.printf("unmarkP=%d\n", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            AProfOps.markInvokedMethod(invMethod);
        System.out.printf("markM=%d\n", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            AProfOps.unmarkInvokedMethod();
        System.out.printf("unmarkM=%d\n", System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < length; i++)
            AProfOps.allocate(location);
        System.out.printf("allocate=%d\n", System.currentTimeMillis() - time);
    }

    private static void testNonAprof(int length) {
        for (int i = 0; i < length; i++) {
            Integer.valueOf(i);
        }
    }

    private static void testAprof(int length) {
        for (int i = 0; i < length; i++) {
            AProfOps.markInvocationPoint(invPoint);
            try {
                AProfOps.markInvokedMethod(invMethod);
                Integer.valueOf(i);
                AProfOps.allocate(location);
                AProfOps.unmarkInvokedMethod();
            } finally {
                AProfOps.unmarkInvocationPoint();
            }
        }
    }
}
