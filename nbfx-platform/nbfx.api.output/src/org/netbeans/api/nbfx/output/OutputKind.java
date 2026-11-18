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
 * What a piece of console text is, which is how the Output view decides its colour: the writer
 * says what the text means and the theme says what that looks like, in light and in dark.
 *
 * @since 1.1
 */
public enum OutputKind {

    /** Ordinary output, in the text colour of the console. */
    NORMAL,

    /** An error: a failed build, the error stream of a process. */
    ERROR,

    /** A warning. */
    WARNING,

    /** A success: the line reporting a build that passed. */
    SUCCESS,

    /** Text of lesser weight than the output around it, such as the name of the step being run. */
    MUTED
}
