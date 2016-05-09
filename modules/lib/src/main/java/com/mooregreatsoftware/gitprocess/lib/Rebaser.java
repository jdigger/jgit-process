package com.mooregreatsoftware.gitprocess.lib;

import javaslang.control.Either;

public interface Rebaser extends Combiner<Rebaser.SuccessfulRebase> {
    Either<String, SuccessfulRebase> rebase(Branch branch);

    @Override
    default String typeName() {
        return "rebase";
    }

    @Override
    default Either<String, SuccessfulRebase> combine(Branch branch) {
        return rebase(branch);
    }


    static Rebaser of(GitLib gitLib) {
        if (gitLib instanceof JgitGitLib)
            return new JgitRebaser((JgitGitLib)gitLib);
        throw new IllegalArgumentException("Don't know how to rebase without JGit");
    }


    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************

    interface SuccessfulRebase {
        String statusMsg();
    }
}
