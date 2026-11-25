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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.layout.PaneRole;
import org.netbeans.api.nbfx.layout.PresentationId;
import org.netbeans.api.nbfx.layout.ViewAreaCapability;
import org.netbeans.api.nbfx.layout.ViewAreaKind;
import org.netbeans.api.nbfx.layout.ViewAreaRegistration;
import org.netbeans.api.nbfx.layout.ViewAreaRegistrations;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one {@code NbFx/LayoutAreas/<role>} folder per {@link ViewAreaRegistration}.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class ViewAreaRegistrationProcessor extends LayerGeneratingProcessor {

    private final Set<String> emittedIds = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                ViewAreaRegistration.class.getCanonicalName(),
                ViewAreaRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ViewAreaRegistration.class)) {
            ViewAreaRegistration registration = element.getAnnotation(ViewAreaRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ViewAreaRegistrations.class)) {
            ViewAreaRegistrations registrations = element.getAnnotation(ViewAreaRegistrations.class);
            if (registrations != null) {
                for (ViewAreaRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, ViewAreaRegistration registration)
            throws LayerGenerationException {
        PaneRole id;
        ViewAreaKind kind;
        PresentationId presentation;
        try {
            id = PaneRole.of(registration.id());
            kind = ViewAreaKind.of(registration.kind());
            presentation = PresentationId.of(registration.presentation());
        } catch (IllegalArgumentException ex) {
            throw new LayerGenerationException("Invalid @ViewAreaRegistration id: " + ex.getMessage(), element);
        }
        if (registration.displayName().isBlank() || registration.policyId().isBlank()) {
            throw new LayerGenerationException(
                    "@ViewAreaRegistration requires non-blank displayName and policyId", element);
        }
        if (Double.isNaN(registration.weight()) || registration.weight() < 0.0d
                || registration.weight() > 1.0d) {
            throw new LayerGenerationException("@ViewAreaRegistration weight must be within [0, 1]", element);
        }
        if (!emittedIds.add(id.value())) {
            throw new LayerGenerationException("Duplicate @ViewAreaRegistration id: " + id, element);
        }
        LayerBuilder.File folder = layer(element).folder(LayerPaths.LAYOUT_AREAS + "/" + id.value());
        folder.bundlevalue("displayName", registration.displayName());
        folder.stringvalue("kind", kind.value());
        folder.stringvalue("location", registration.location().name());
        folder.doublevalue("weight", registration.weight());
        folder.stringvalue("presentation", presentation.value());
        folder.stringvalue("capabilities", registration.capabilities().length == 0 ? ""
                : Arrays.stream(registration.capabilities())
                        .map(ViewAreaCapability::name)
                        .sorted()
                        .collect(Collectors.joining(" ")));
        folder.stringvalue("policyId", registration.policyId());
        folder.stringvalue("visibility", registration.visibility().name());
        folder.stringvalue("emptyPolicy", registration.emptyPolicy().name());
        folder.intvalue("order", registration.order());
        folder.write();
    }
}
