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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.netbeans.api.nbfx.resources.ResourceRef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

/** Tests immutable product metadata and validation. */
public class ProductTest {

    @Test
    public void defensivelyCopiesAllLists() {
        List<ResourceRef> windowIcons = new ArrayList<>();
        windowIcons.add(ResourceRef.of("org.example", "window.png"));
        List<ResourceRef> installerIcons = new ArrayList<>();
        installerIcons.add(ResourceRef.of("org.example", "installer.png"));
        List<ResourceRef> stylesheets = new ArrayList<>();
        stylesheets.add(ResourceRef.of("org.example", "product.css"));
        List<ResourceRef> tokenResources = new ArrayList<>();
        tokenResources.add(ResourceRef.of("org.example", "tokens.json"));
        List<String> rootClasses = new ArrayList<>();
        rootClasses.add("product");
        List<String> credits = new ArrayList<>();
        credits.add("Example");
        List<ResourceRef> notices = new ArrayList<>();
        notices.add(ResourceRef.of("org.example", "NOTICE"));
        List<DesktopAssociation> associations = new ArrayList<>();
        associations.add(new DesktopAssociation("example", null, null, "open"));

        Product product = new Product(
                new ProductIdentity("example", "Example", "1.0", null, null, null, null),
                new ProductIcons(windowIcons, null, installerIcons),
                new ProductStyle(stylesheets, tokenResources, rootClasses),
                "theme.default", "example.default", "shell.default", "chrome.default",
                associations, new AboutInfo("", credits, notices, null));

        windowIcons.clear();
        installerIcons.clear();
        stylesheets.clear();
        tokenResources.clear();
        rootClasses.clear();
        credits.clear();
        notices.clear();
        associations.clear();

        assertEquals(1, product.icons().windowIcons().size());
        assertEquals(1, product.icons().installerIcons().size());
        assertEquals(1, product.style().stylesheets().size());
        assertEquals(1, product.style().tokenResources().size());
        assertEquals(1, product.style().rootStyleClasses().size());
        assertEquals(1, product.about().credits().size());
        assertEquals(1, product.about().notices().size());
        assertEquals(1, product.associations().size());
        assertThrows(UnsupportedOperationException.class,
                () -> product.associations().add(product.associations().get(0)));
    }

    @Test
    public void validatesIdentityAndAbsoluteUris() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProductIdentity(" ", "Example", "1.0", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductIdentity("example", " ", "1.0", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductIdentity("example", "Example", " ", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProductIdentity("example", "Example", "1.0", null, null,
                        URI.create("relative"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new AboutInfo("", List.of(), List.of(), URI.create("relative")));
    }

    @Test
    public void validatesDefaultIdsAndDesktopAssociations() {
        ProductIdentity identity = new ProductIdentity("example", "Example", "1.0",
                null, null, null, null);
        ProductIcons icons = new ProductIcons(List.of(), null, List.of());
        ProductStyle style = new ProductStyle(List.of(), List.of(), List.of());
        AboutInfo about = new AboutInfo("", List.of(), List.of(), null);
        assertThrows(IllegalArgumentException.class,
                () -> new Product(identity, icons, style, " ", "example.default", "shell.default",
                        "chrome.default", List.of(), about));
        assertThrows(IllegalArgumentException.class,
                () -> new DesktopAssociation(null, null, null, "open"));
        assertThrows(IllegalArgumentException.class,
                () -> new DesktopAssociation("example", null, null, " "));

        DesktopAssociation association = new DesktopAssociation(" ", "text/plain", null, "open");
        assertNull(association.extension());
        assertEquals("text/plain", association.mimeType());
    }
}
