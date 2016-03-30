package com.mooregreatsoftware.gitprocess;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamUtils {

    /**
     * Wrap the {@link Iterable} in a {@link Stream}
     */
    @Nonnull
    public static <T> Stream<T> stream(@Nonnull Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }


    /**
     * Wrap the {@link Iterator} in a {@link Stream}
     */
    @Nonnull
    public static <T> Stream<T> stream(@Nonnull Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL), false);
    }

}
