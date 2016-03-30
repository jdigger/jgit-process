package com.mooregreatsoftware.gitprocess.bin

import spock.lang.Specification
import spock.lang.Subject

@Subject(ReadVersionFromClasspath)
@SuppressWarnings("GroovyPointlessBoolean")
class ReadVersionFromClasspathSpec extends Specification {
    final classFilename = 'com/mooregreatsoftware/gitprocess/lib/GitLib.class'


    def "version from a JAR file"() {
        def version
        def filename

        when:
        version = '0.0.1-dev.16.uncommitted+3d01efe'
        filename = "file:/dir/com/mooregreatsoftware/gitprocess/lib/${version}/lib-${version}.jar!/${classFilename}"

        then:
        ReadVersionFromClasspath.versionFromJarFilename(filename, classFilename).get() == version

        when:
        version = '0.0.1-dev.16.uncommitted+3d01efe'
        filename = "file:/dir/lib-${version}.jar!/${classFilename}"

        then:
        ReadVersionFromClasspath.versionFromJarFilename(filename, classFilename).get() == version

        when:
        version = '1.1.1'
        filename = "file:/dir/lib-${version}.jar!/${classFilename}"

        then:
        ReadVersionFromClasspath.versionFromJarFilename(filename, classFilename).get() == version
    }


    def "version from a local class file"() {
        def filename = "/dir/classes/${classFilename}"

        expect:
        ReadVersionFromClasspath.versionFromJarFilename(filename, classFilename).isPresent() == false
    }

}
