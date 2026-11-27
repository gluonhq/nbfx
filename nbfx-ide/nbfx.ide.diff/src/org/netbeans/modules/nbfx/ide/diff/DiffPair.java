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
package org.netbeans.modules.nbfx.ide.diff;

import java.util.Objects;

/**
 * The two texts of one {@link DiffEntry}.
 *
 * @param left the text compared from, shown on the left
 * @param right the text compared to, shown on the right
 */
public record DiffPair(DiffSource left, DiffSource right) {

    /** Both are required. */
    public DiffPair {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }
}
