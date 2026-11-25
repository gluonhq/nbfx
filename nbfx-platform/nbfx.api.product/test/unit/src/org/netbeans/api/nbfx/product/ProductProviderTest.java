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

import java.util.List;
import org.junit.Test;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Tests exactly-one product provider resolution. */
public class ProductProviderTest {

    @Test
    public void resolvesOneProvider() {
        Product product = sampleProduct();
        ProductProvider provider = () -> product;
        assertSame(product, ProductProvider.resolve(Lookups.fixed(provider)));
    }

    @Test
    public void rejectsMissingAndDuplicateProviders() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> ProductProvider.resolve(Lookup.EMPTY));
        assertTrue(missing.getMessage(), missing.getMessage().contains("found 0"));

        Product product = sampleProduct();
        ProductProvider first = new NamedProviderA(product);
        ProductProvider second = new NamedProviderB(product);
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> ProductProvider.resolve(Lookups.fixed(first, second)));
        assertTrue(duplicate.getMessage(), duplicate.getMessage().contains("found 2"));
        assertTrue(duplicate.getMessage(), duplicate.getMessage().contains(NamedProviderA.class.getName()));
        assertTrue(duplicate.getMessage(), duplicate.getMessage().contains(NamedProviderB.class.getName()));
    }

    @Test
    public void rejectsNullProductAndHasNoPriority() {
        ProductProvider invalid = () -> null;
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ProductProvider.resolve(Lookups.fixed(invalid)));
        assertTrue(failure.getMessage(), failure.getMessage().contains("returned null"));
        assertThrows(NoSuchMethodException.class, () -> ProductProvider.class.getMethod("priority"));
    }

    private static Product sampleProduct() {
        return new Product(
                new ProductIdentity("example", "Example", "1.0", null, null, null, null),
                new ProductIcons(List.of(), null, List.of()),
                new ProductStyle(List.of(), List.of(), List.of()),
                "theme.default", "example.default", "shell.default", "chrome.default",
                List.of(), new AboutInfo("", List.of(), List.of(), null));
    }

    private static final class NamedProviderA implements ProductProvider {

        private final Product product;

        private NamedProviderA(Product product) {
            this.product = product;
        }

        @Override
        public Product product() {
            return product;
        }
    }

    private static final class NamedProviderB implements ProductProvider {

        private final Product product;

        private NamedProviderB(Product product) {
            this.product = product;
        }

        @Override
        public Product product() {
            return product;
        }
    }
}
