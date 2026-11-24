package com.smartbillingpro;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Runs every static {@code test*} method of the test classes and exits with a
 * non-zero status if any fail. Usage: {@code run-tests.bat} / {@code ./run-tests.sh}.
 */
public final class TestRunner {

    private static final Class<?>[] SUITES = {CoreTests.class, BackupTests.class, OutputTests.class};

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", System.getProperty("java.awt.headless", "true"));
        int passed = 0;
        int failed = 0;
        for (Class<?> suite : SUITES) {
            Method[] methods = suite.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method m : methods) {
                if (!m.getName().startsWith("test") || !Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (args.length > 0 && !m.getName().toLowerCase().contains(args[0].toLowerCase())) {
                    continue;
                }
                m.setAccessible(true);
                long start = System.currentTimeMillis();
                try {
                    m.invoke(null);
                    passed++;
                    System.out.printf("  PASS  %s.%s (%d ms)%n", suite.getSimpleName(), m.getName(),
                            System.currentTimeMillis() - start);
                } catch (InvocationTargetException e) {
                    failed++;
                    System.out.printf("  FAIL  %s.%s%n", suite.getSimpleName(), m.getName());
                    Throwable cause = e.getCause();
                    System.out.println("        " + cause);
                    for (StackTraceElement el : cause.getStackTrace()) {
                        if (el.getClassName().startsWith("com.smartbillingpro")) {
                            System.out.println("          at " + el);
                        }
                    }
                } catch (IllegalAccessException e) {
                    failed++;
                    System.out.println("  FAIL  " + m.getName() + ": " + e);
                }
            }
        }
        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
