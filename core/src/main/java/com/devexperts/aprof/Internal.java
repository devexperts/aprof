package com.devexperts.aprof;

import java.lang.annotation.*;

/**
 * Marks classes that work from inside of {@link AProfOps}.
 * This annotation is for documentation purposes only.
 * The actual check for internal methods is performed in {@link AProfRegistry#isInternalLocation(String)} method.
 * Internal methods are instrumented with {@link AProfOpsInternal} to avoid recursion.
 *
 * @author Roman Elizarov
 */
@Documented
@Target(ElementType.TYPE)
public @interface Internal {
}
