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
package com.mooregreatsoftware.gitprocess.github

import com.mooregreatsoftware.gitprocess.lib.GitSpecification
import groovy.json.JsonOutput
import org.eclipse.jgit.transport.URIish
import spock.lang.AutoCleanup

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static java.net.HttpURLConnection.HTTP_CREATED
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED

class AuthorizerSpec extends GitSpecification {

    @AutoCleanup
    JettySupport jetty = new JettySupport()


    def setup() {
        createCommit("a")
        createFakeRemoteBranch("testRemote/master")
    }


    def "GetOauthToken"() {
        jetty.addPostHandler({ HttpServletRequest request, HttpServletResponse response ->
            if (request.pathInfo == "/authorizations") {
                response.status = HTTP_CREATED
                response.writer.println JsonOutput.toJson([token: 'a_token'])
                return
            }
            response.status = HTTP_NOT_FOUND
        } as JettySupport.SimpleHandler)

        jetty.start()

        origin.remoteConfig().remoteAdd("testRemote", new URIish("http://localhost:${jetty.serverPort}/jdigger/testproj"))

        when:
        def authorizer = new Authorizer(origin).username("tester").password("test_pw")

        then:
        authorizer.oauthToken == "a_token"
    }


    def "Need OTP"() {
        jetty.addPostHandler({ HttpServletRequest request, HttpServletResponse response ->
            if (request.pathInfo == "/authorizations") {
                def otpHeader = request.getHeader(Authorizer.GITHUB_2FA_HEADER)
                if (otpHeader == "3456") {
                    response.status = HTTP_CREATED
                    response.writer.println JsonOutput.toJson([token: 'a_token'])
                    return
                }
                else {
                    response.status = HTTP_UNAUTHORIZED
                    response.addHeader(Authorizer.GITHUB_2FA_HEADER, "required; app")
                    response.writer.println JsonOutput.toJson(
                        [message          : "Must specify two-factor authentication OTP code.",
                         documentation_url: "https://developer.github.com/v3/auth#working-with-two-factor-authentication"]
                    )
                    return
                }
            }
            response.status = HTTP_NOT_FOUND
        } as JettySupport.SimpleHandler)

        jetty.start()

        origin.remoteConfig().remoteAdd("testRemote", new URIish("http://localhost:${jetty.serverPort}/jdigger/testproj"))

        when:
        def authorizer = new Authorizer(origin, System.out, new BufferedInputStream(new ByteArrayInputStream("3456\n".bytes))).username("tester").password("test_pw")

        then:
        authorizer.oauthToken == "a_token"
    }

}
