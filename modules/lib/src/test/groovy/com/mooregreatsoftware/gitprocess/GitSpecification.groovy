package com.mooregreatsoftware.gitprocess

import spock.lang.Specification

abstract class GitSpecification extends Specification implements GitSpecHelper {

    def cleanup() {
        gitLib.workingDirectory().deleteDir()
        gitLib.close()
    }

}
