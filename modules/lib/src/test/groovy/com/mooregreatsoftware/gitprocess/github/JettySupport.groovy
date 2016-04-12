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

import groovy.transform.Immutable
import groovy.transform.TypeChecked
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.server.handler.HandlerList

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@TypeChecked
class JettySupport {

    Server server

    HandlerCollection handlerCollection

    GetHandler getHandler = new GetHandler()
    PostHandler postHandler = new PostHandler()


    JettySupport() {
        server = new Server(0)
        handlerCollection = new HandlerList()

        server.setHandler(handlerCollection)
    }


    void start() {
        addGetHandler(getHandler)
        addPostHandler(postHandler)

        server.start()
    }


    @SuppressWarnings("GroovyUnusedDeclaration")
    void close() {
        server.stop()
    }


    int getServerPort() {
        return ((ServerConnector)server.connectors[0]).localPort
    }


    void addGetHandler(SimpleHandler handler) {
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.method == "GET") {
                    handler.handle(request, response)
                    baseRequest.handled = true
                }
            }
        })
    }


    void addPostHandler(SimpleHandler handler) {
        handlerCollection.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.method == "POST") {
                    handler.handle(request, response)
                    baseRequest.handled = true
                }
            }
        })
    }


    void addPostPathAndParamResponse(String path, Map params, String resp) {
        postHandler.addPathAndParamResponse(path, params, resp)
    }


    void addPostHandler(String path, Map params, String resp) {
        postHandler.addPathAndParamResponse(path, params, resp)
    }

    // **********************************************************************
    //
    // HELPER CLASSES
    //
    // **********************************************************************


    static interface SimpleHandler {
        void handle(HttpServletRequest request, HttpServletResponse response)
    }


    @TypeChecked
    static class GetHandler implements SimpleHandler {
        Map<String, String> pathToResponse = [:]


        void addPathResponse(String path, String response) {
            pathToResponse.put(path, response)
        }


        @Override
        void handle(HttpServletRequest request, HttpServletResponse response) {
            def respStr = pathToResponse.find { it.key == request.pathInfo }?.value
            if (respStr != null) {
                response.writer.println(respStr)
                return
            }
            response.status = 404
        }
    }


    @TypeChecked
    static class PostHandler implements SimpleHandler {
        Map<RequestPredicate, String> predToResponse = [:]


        void addPathAndParamResponse(String path, Map params, String resp) {
            predToResponse.put(new PathAndParamPredicate(path: path, params: params), resp)
        }


        @Override
        void handle(HttpServletRequest request, HttpServletResponse response) {
            def respStr = predToResponse.find { it.key.eval(request) }?.value
            if (respStr != null) {
                response.writer.println(respStr)
                return
            }
            response.status = 404
        }
    }


    static interface RequestPredicate {
        boolean eval(HttpServletRequest request)
    }


    @Immutable
    @TypeChecked
    static class PathAndParamPredicate implements RequestPredicate {
        String path
        Map params


        boolean eval(HttpServletRequest request) {
            if (request.pathInfo == path) {
                return params.every { request.getParameter(it.key as String) == it.value }
            }
            return false
        }
    }

}
