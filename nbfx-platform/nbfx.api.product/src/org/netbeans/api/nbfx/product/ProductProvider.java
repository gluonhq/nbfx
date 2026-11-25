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
package org.netbeans.api.nbfx.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.openide.util.Lookup;

/**
 * Supplies the single product descriptor for a distribution. Product modules register a public
 * no-argument implementation with {@code @ServiceProvider(service = ProductProvider.class)}.
 * Priorities and superseding are deliberately unsupported.
 *
 * @since 1.0
 */
@FunctionalInterface
public interface ProductProvider {

    /** Returns the provider's nonnull immutable product snapshot. */
    Product product();

    /** Resolves the product from the global lookup. */
    static Product resolve() {
        return resolve(Lookup.getDefault());
    }

    /**
     * Resolves exactly one provider. Zero providers, multiple providers and a null product are
     * startup errors; no provider is selected by priority or class name.
     *
     * @param lookup the lookup containing product providers
     * @return the single product
     * @throws IllegalStateException if exactly one nonnull product is not available
     */
    static Product resolve(Lookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        List<? extends ProductProvider> providers = new ArrayList<>(lookup.lookupAll(ProductProvider.class));
        if (providers.size() != 1) {
            String names = providers.stream()
                    .map(provider -> provider == null ? "null" : provider.getClass().getName())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Exactly one ProductProvider is required; found "
                    + providers.size() + (names.isEmpty() ? "" : ": " + names));
        }
        ProductProvider provider = providers.get(0);
        if (provider == null) {
            throw new IllegalStateException("ProductProvider lookup contains null");
        }
        Product product = provider.product();
        if (product == null) {
            throw new IllegalStateException("ProductProvider " + provider.getClass().getName()
                    + " returned null");
        }
        return product;
    }
}
