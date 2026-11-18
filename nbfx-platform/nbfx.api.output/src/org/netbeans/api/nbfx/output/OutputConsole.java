/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.api.nbfx.output;

/**
 * The application's output consoles: where a build, a run, a test or any other streaming activity
 * writes its text. The service is found in the global Lookup and is provided by the platform,
 * whose Output window shows the consoles it hands out; it is an API to call, not one to implement.
 * <p>
 * Asking for a console does not show anything: an activity that wants its output on screen calls
 * {@link ConsolePanel#show()}.
 *
 * @since 1.0
 */
public interface OutputConsole {

    /**
     * Returns the console named {@code name}, creating it if it does not exist: the console whose
     * {@linkplain #console(String, String) id} is its name. Safe to call from any thread.
     *
     * @param name the console name, shown on its tab
     * @return the console
     */
    default ConsolePanel console(String name) {
        return console(name, name);
    }

    /**
     * Returns the console identified by {@code id}, creating it with the name {@code name} if it
     * does not exist. The id is what makes two consoles the same one; the name is only what their
     * tab says, so two activities with the same name - the runs of two projects called alike -
     * get a console each by giving ids of their own. Safe to call from any thread.
     *
     * @param id   what identifies the console, such as the folder and the command of a build
     * @param name the console name, shown on its tab
     * @return the console
     */
    ConsolePanel console(String id, String name);
}
