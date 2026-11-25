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
package org.netbeans.modules.nbfx.api.layout.processors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.layout.LayoutRegistration;
import org.netbeans.api.nbfx.layout.LayoutRegistrations;
import org.netbeans.api.nbfx.layout.LayoutSpec;
import org.netbeans.api.nbfx.layout.LayoutXml;
import org.netbeans.api.nbfx.layout.ViewArea;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one self-contained {@code NbFx/Layouts/<id>.xml} resource per {@link LayoutRegistration}.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class LayoutRegistrationProcessor extends LayerGeneratingProcessor {

    private static final Pattern XML_ENCODING = Pattern.compile(
            "<\\?xml\\s+[^?]*?\\bencoding\\s*=\\s*(['\"])([^'\"]+)\\1",
            Pattern.CASE_INSENSITIVE);

    private final Set<String> emittedIds = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                LayoutRegistration.class.getCanonicalName(),
                LayoutRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(LayoutRegistration.class)) {
            LayoutRegistration registration = element.getAnnotation(LayoutRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(LayoutRegistrations.class)) {
            LayoutRegistrations registrations = element.getAnnotation(LayoutRegistrations.class);
            if (registrations != null) {
                for (LayoutRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, LayoutRegistration registration)
            throws LayerGenerationException {
        String id = registration.id();
        String xml = registration.xml();
        if (id.isBlank() || !id.equals(id.trim()) || id.indexOf('/') >= 0 || id.indexOf('\\') >= 0
                || ".".equals(id) || "..".equals(id)
                || id.chars().anyMatch(Character::isISOControl)) {
            throw new LayerGenerationException(
                    "@LayoutRegistration requires a non-blank file-safe id", element);
        }
        if (xml.isBlank()) {
            throw new LayerGenerationException("@LayoutRegistration requires non-blank XML", element);
        }
        validateEncoding(element, xml);
        LayoutSpec layout;
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            layout = LayoutXml.read(input);
        } catch (IOException | IllegalArgumentException ex) {
            throw new LayerGenerationException(
                    "@LayoutRegistration contains invalid layout XML: " + ex.getMessage(), element);
        }
        if (!id.equals(layout.id())) {
            throw new LayerGenerationException(
                    "@LayoutRegistration id must match the XML layout id " + layout.id(), element);
        }
        if (!emittedIds.add(id)) {
            throw new LayerGenerationException("Duplicate @LayoutRegistration id: " + id, element);
        }
        validateBundleKeys(element, layout);
        String canonicalXml;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            LayoutXml.write(layout, output);
            canonicalXml = output.toString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new LayerGenerationException("Cannot canonicalize layout XML: " + ex.getMessage(), element);
        }
        LayerBuilder.File file = layer(element).file(LayerPaths.LAYOUTS + "/" + id + ".xml");
        file.contents(canonicalXml);
        file.write();
    }

    private static void validateEncoding(Element element, String xml) throws LayerGenerationException {
        Matcher matcher = XML_ENCODING.matcher(xml);
        if (matcher.find() && !"UTF-8".equalsIgnoreCase(matcher.group(2).trim())) {
            throw new LayerGenerationException(
                    "@LayoutRegistration XML must use UTF-8 or omit the encoding declaration", element);
        }
    }

    private void validateBundleKeys(Element element, LayoutSpec layout) throws LayerGenerationException {
        // This validation-only builder is never written. bundlevalue performs source-bundle checks.
        LayerBuilder.File validator = layer(element).file("dummy/layout-bundle-validation");
        for (ViewArea area : layout.areas().values()) {
            String name = area.displayName();
            if (name.startsWith("#")) {
                if (name.length() == 1) {
                    throw new LayerGenerationException("Empty displayName bundle key", element);
                }
                validator.bundlevalue("displayName", name);
            }
        }
    }
}
