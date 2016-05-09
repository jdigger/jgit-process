package com.mooregreatsoftware.gitprocess.lib;

import javaslang.control.Either;

public interface Combiner<T> {
    Either<String, T> combine(Branch branch);

    String typeName();
}
