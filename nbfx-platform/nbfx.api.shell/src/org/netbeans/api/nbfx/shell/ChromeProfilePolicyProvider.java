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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.openide.util.Lookup;

/**
 * Supplies an immutable product chrome-profile policy through NBM service lookup. Products register
 * one public no-argument implementation with
 * {@code @ServiceProvider(service = ChromeProfilePolicyProvider.class)}. Priority and superseding are
 * unsupported.
 *
 * @since 1.5
 */
@FunctionalInterface
public interface ChromeProfilePolicyProvider {

    /** Returns the provider's nonnull immutable policy. */
    ChromeProfilePolicy policy();

    /**
     * Resolves exactly one policy with {@code policyId}. Zero matching providers yields empty; a
     * null policy, null provider or multiple matching providers is an error.
     *
     * @param policyId the stable policy id named by product metadata
     * @param lookup the lookup containing policy providers
     * @return the matching policy, if one is registered
     */
    static Optional<ChromeProfilePolicy> resolve(String policyId, Lookup lookup) {
        policyId = ShellTokens.require(policyId, "policyId");
        Objects.requireNonNull(lookup, "lookup");
        List<ChromeProfilePolicy> matches = new ArrayList<>();
        for (ChromeProfilePolicyProvider provider : lookup.lookupAll(ChromeProfilePolicyProvider.class)) {
            if (provider == null) {
                throw new IllegalStateException("ChromeProfilePolicyProvider lookup contains null");
            }
            ChromeProfilePolicy policy = provider.policy();
            if (policy == null) {
                throw new IllegalStateException("ChromeProfilePolicyProvider "
                        + provider.getClass().getName() + " returned null");
            }
            if (policy.id().equals(policyId)) {
                matches.add(policy);
            }
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Exactly one ChromeProfilePolicyProvider is allowed for "
                    + policyId + "; found " + matches.size());
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }
}
