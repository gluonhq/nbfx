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
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.shell.ShellProfileRegistration;
import org.netbeans.api.nbfx.shell.ShellProfileRegistrations;
import org.netbeans.api.nbfx.shell.ShellRegionId;
import org.netbeans.api.nbfx.shell.WindowDecoration;
import org.netbeans.api.nbfx.shell.WindowKind;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/** Writes one {@code NbFx/ShellProfiles/<id>} folder per profile declaration. */
@ServiceProvider(service = Processor.class)
public class ShellProfileRegistrationProcessor extends LayerGeneratingProcessor {

    private final Set<String> emittedIds = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                ShellProfileRegistration.class.getCanonicalName(),
                ShellProfileRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ShellProfileRegistration.class)) {
            ShellProfileRegistration registration = element.getAnnotation(ShellProfileRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ShellProfileRegistrations.class)) {
            ShellProfileRegistrations registrations = element.getAnnotation(ShellProfileRegistrations.class);
            if (registrations != null) {
                for (ShellProfileRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, ShellProfileRegistration registration)
            throws LayerGenerationException {
        String id = registration.id();
        if (!isToken(id) || ".".equals(id) || "..".equals(id)
                || id.indexOf('/') >= 0 || id.indexOf('\\') >= 0) {
            throw new LayerGenerationException("Shell profile id must be a file-safe atomic token", element);
        }
        if (!emittedIds.add(id)) {
            throw new LayerGenerationException("Duplicate shell profile id: " + id, element);
        }
        Set<String> regions = new TreeSet<>();
        for (String value : registration.regions()) {
            try {
                regions.add(ShellRegionId.of(value).value());
            } catch (IllegalArgumentException ex) {
                throw new LayerGenerationException("Invalid shell profile region: " + value, element);
            }
        }
        LayerBuilder.File folder = layer(element).folder(LayerPaths.SHELL_PROFILES + "/" + id);
        if (!registration.windowKind().isBlank()) {
            folder.stringvalue("windowKind", enumValue(registration.windowKind(), WindowKind.class, element));
        }
        if (!regions.isEmpty()) {
            folder.stringvalue("regions", String.join(",", regions));
        }
        if (!registration.decoration().isBlank()) {
            folder.stringvalue("decoration",
                    enumValue(registration.decoration(), WindowDecoration.class, element));
        }
        folder.boolvalue("systemMenuBar", registration.systemMenuBar());
        folder.write();
    }

    private static boolean isToken(String value) {
        return value != null && !value.isBlank()
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character) || character == ',');
    }

    private static <T extends Enum<T>> String enumValue(String value, Class<T> type, Element element)
            throws LayerGenerationException {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new LayerGenerationException("Invalid shell profile " + type.getSimpleName() + ": " + value,
                    element);
        }
    }
}
