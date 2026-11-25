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
package org.netbeans.api.nbfx.shell;

import java.util.Objects;

/**
 * One fresh per-shell contribution instance and its authoritative metadata. The caller owns the
 * instance and must call {@link ShellContribution#dispose()} when the mount is released.
 *
 * @param spec contribution metadata
 * @param contribution fresh contribution instance
 * @since 1.2
 */
public record ShellContributionEntry(ShellContributionSpec spec, ShellContribution contribution) {

    public ShellContributionEntry {
        spec = Objects.requireNonNull(spec, "spec");
        contribution = Objects.requireNonNull(contribution, "contribution");
    }
}
