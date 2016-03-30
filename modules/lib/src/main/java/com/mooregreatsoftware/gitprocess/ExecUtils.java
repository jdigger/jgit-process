package com.mooregreatsoftware.gitprocess;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Functions to make it more convenient to execute functions, such as wrapping lambdas.
 */
public final class ExecUtils {

    /**
     * Executes the {@link Callable}, wrapping any checked exceptions in either a {@link IllegalStateException} or
     * {@link RuntimeException}.
     * <p>
     * Acts like {@link #v(ExceptionAction)} but returns a value.
     *
     * @param callable what to run
     * @param <T>      what to return
     */
    public static <T> T e(Callable<T> callable) {
        try {
            return callable.call();
        }
        catch (GitAPIException | IOException e) {
            throw new IllegalStateException(e);
        }
        catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            else throw new RuntimeException(e);
        }
    }


    /**
     * Executes the {@link Callable}, wrapping any checked exceptions in either a {@link IllegalStateException} or
     * {@link RuntimeException}.
     * <p>
     * Acts like {@link #e(Callable)} but does not return a value.
     *
     * @param callable what to run
     * @see #e(Callable)
     */
    public static void v(ExceptionAction callable) {
        try {
            callable.call();
        }
        catch (GitAPIException | IOException e) {
            throw new IllegalStateException(e);
        }
        catch (Exception e) {
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            else throw new RuntimeException(e);
        }
    }


    /**
     * Similar interface as {@link Callable} but does not return anything. Used for {@link GitLib#v(ExceptionAction)}
     */
    public interface ExceptionAction {
        void call() throws Exception;
    }

}
