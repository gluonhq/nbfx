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
package org.netbeans.modules.nbfx.api.shell.processors;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.shell.ShellRegionId;
import org.netbeans.api.nbfx.shell.ShellRegionKind;
import org.netbeans.api.nbfx.shell.ShellRegionRegistration;
import org.netbeans.api.nbfx.shell.ShellRegionRegistrations;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one {@code NbFx/ShellRegions/<id>} metadata folder per {@link ShellRegionRegistration}.
 *
 * @since 1.1
 */
@ServiceProvider(service = Processor.class)
public class ShellRegionRegistrationProcessor extends LayerGeneratingProcessor {

    private final Set<String> emittedIds = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                ShellRegionRegistration.class.getCanonicalName(),
                ShellRegionRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ShellRegionRegistration.class)) {
            ShellRegionRegistration registration = element.getAnnotation(ShellRegionRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ShellRegionRegistrations.class)) {
            ShellRegionRegistrations registrations = element.getAnnotation(ShellRegionRegistrations.class);
            if (registrations != null) {
                for (ShellRegionRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, ShellRegionRegistration registration)
            throws LayerGenerationException {
        ShellRegionId id;
        ShellRegionKind kind;
        try {
            id = ShellRegionId.of(registration.id());
            kind = ShellRegionKind.of(registration.kind());
        } catch (IllegalArgumentException ex) {
            throw new LayerGenerationException(
                    "Invalid @ShellRegionRegistration id/kind: " + ex.getMessage(), element);
        }
        Set<String> profiles = new TreeSet<>();
        for (String profile : registration.profiles()) {
            if (!isToken(profile)) {
                throw new LayerGenerationException("Shell region profiles must be nonblank atomic tokens", element);
            }
            profiles.add(profile);
        }
        String styleClass = registration.styleClass();
        if (!styleClass.isBlank() && !isToken(styleClass)) {
            throw new LayerGenerationException("Shell region styleClass must be one CSS class token", element);
        }
        if (!emittedIds.add(id.value())) {
            throw new LayerGenerationException("Duplicate @ShellRegionRegistration id: " + id, element);
        }
        LayerBuilder.File folder = layer(element).folder(LayerPaths.SHELL_REGIONS + "/" + id.value());
        folder.stringvalue("kind", kind.value());
        folder.intvalue("position", registration.position());
        if (!profiles.isEmpty()) {
            folder.stringvalue("profiles", String.join(",", profiles));
        }
        if (!styleClass.isBlank()) {
            folder.stringvalue("styleClass", styleClass);
        }
        folder.boolvalue("visibleByDefault", registration.visibleByDefault());
        folder.write();
    }

    private static boolean isToken(String value) {
        return value != null && !value.isBlank()
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character) || character == ',');
    }
}
