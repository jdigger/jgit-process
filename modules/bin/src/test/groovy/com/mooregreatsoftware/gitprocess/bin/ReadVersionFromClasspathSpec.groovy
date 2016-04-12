/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        ReadVersionFromClasspath.versionFromJarFilename(filename, classFilename) == version

        when:
        version = '0.0.1-dev.16.uncommitted+3d01efe'
        filename = "file:/dir/lib-${version}.jar!/${classFilename}"

        then:
        ReadVersionFromClasspath.versionFromJarFilename(filename, classFilename) == version

        when:
        version = '1.1.1'
        filename = "file:/dir/lib-${version}.jar!/${classFilename}"

        then:
        ReadVersionFromClasspath.versionFromJarFilename(filename, classFilename) == version
    }


    def "version from a local class file"() {
        def filename = "/dir/classes/${classFilename}"

        expect:
        ReadVersionFromClasspath.versionFromJarFilename(filename, classFilename) == null
    }

}
