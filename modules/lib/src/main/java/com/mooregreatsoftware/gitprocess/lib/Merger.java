package com.mooregreatsoftware.gitprocess.lib;

import javaslang.control.Either;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ObjectId;

public interface Merger extends Combiner<Merger.SuccessfulMerge> {

    Either<String, SuccessfulMerge> merge(Branch branch);

    @Override
    default Either<String, SuccessfulMerge> combine(Branch branch) {
        return merge(branch);
    }

    @Override
    default String typeName() {
        return "merge";
    }


    static Merger of(GitLib gitLib) {
        if (gitLib instanceof JgitGitLib)
            return new JgitMerger((JgitGitLib)gitLib);
        throw new IllegalArgumentException("Don't know how to merge without JGit");
    }


    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************


    class SuccessfulMerge {
        private final MergeResult mergeResult;


        public SuccessfulMerge(MergeResult mergeResult) {
            this.mergeResult = mergeResult;
        }


        public String statusMsg() {
            return mergeResult.getMergeStatus().toString();
        }


        public ObjectId newHead() {
            return mergeResult.getNewHead();
        }


        public String toString() {
            return "SuccessfulMerge{" + statusMsg() + " - " + newHead().abbreviate(7).name() + "}";
        }

    }
}
