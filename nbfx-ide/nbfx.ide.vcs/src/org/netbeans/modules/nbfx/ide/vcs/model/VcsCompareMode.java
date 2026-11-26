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
package org.netbeans.modules.nbfx.ide.vcs.model;

/**
 * The two sides a status view compares. A version-controlled file has three states: the last commit
 * ({@code HEAD}), the index (what the next commit will contain) and the working tree (what is on disk).
 *
 * @since 1.1
 */
public enum VcsCompareMode {

    /** Everything that differs between the last commit and the files on disk, staged or not. */
    HEAD_VS_WORKING_TREE,

    /** What is staged: the changes the next commit will contain. */
    HEAD_VS_INDEX,

    /** What is not staged yet, including untracked files. */
    INDEX_VS_WORKING_TREE
}
