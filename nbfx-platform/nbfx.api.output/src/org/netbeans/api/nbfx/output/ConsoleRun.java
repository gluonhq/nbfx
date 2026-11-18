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
 * One activity filling a {@link ConsolePanel}, from {@link ConsolePanel#startRun} to
 * {@link #finish()}. It is the identity of the run: finishing it ends this run and no other, so a
 * run that ends after the next one started - its last message still on its way - does not mark
 * the console as idle.
 *
 * @since 1.0
 */
public interface ConsoleRun {

    /**
     * Ends the run: the console is idle again, unless another run has started on it since. Safe
     * to call from any thread, and more than once.
     */
    void finish();
}
