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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.shell.ShellContribution;
import org.netbeans.api.nbfx.shell.ShellContributionRegistration;
import org.netbeans.api.nbfx.shell.ShellContributionRegistrations;
import org.netbeans.api.nbfx.shell.ShellRegionId;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one direct {@code .instance} contribution entry per {@link ShellContributionRegistration}.
 *
 * @since 1.2
 */
@ServiceProvider(service = Processor.class)
public class ShellContributionRegistrationProcessor extends LayerGeneratingProcessor {

    private final Set<String> emittedRegistrations = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                ShellContributionRegistration.class.getCanonicalName(),
                ShellContributionRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ShellContributionRegistration.class)) {
            ShellContributionRegistration registration = element.getAnnotation(ShellContributionRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ShellContributionRegistrations.class)) {
            ShellContributionRegistrations registrations = element.getAnnotation(ShellContributionRegistrations.class);
            if (registrations != null) {
                for (ShellContributionRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, ShellContributionRegistration registration)
            throws LayerGenerationException {
        ShellRegionId region;
        try {
            region = ShellRegionId.of(registration.region());
        } catch (IllegalArgumentException ex) {
            throw new LayerGenerationException("Invalid shell contribution region: " + ex.getMessage(), element);
        }
        if (region.equals(ShellRegionId.DOCK)) {
            throw new LayerGenerationException("DOCK is runtime-owned and cannot host contributions", element);
        }
        Set<String> profiles = new TreeSet<>();
        for (String profile : registration.profiles()) {
            if (!isToken(profile)) {
                throw new LayerGenerationException("Shell contribution profiles must be atomic tokens", element);
            }
            profiles.add(profile);
        }
        validateContributionType(element);
        if (!hasPublicNoArgConstructor(element)) {
            throw new LayerGenerationException("Shell contributions require a public no-argument constructor",
                    element);
        }
        String className = ((TypeElement) element).getQualifiedName().toString();
        String key = region.value() + "/" + className;
        if (!emittedRegistrations.add(key)) {
            throw new LayerGenerationException("Duplicate shell contribution registration: " + key, element);
        }
        String fileName = className.replace('.', '-').replace('$', '-');
        LayerBuilder.File file = layer(element).instanceFile(
                LayerPaths.SHELL_REGIONS + "/" + region.value(), fileName, ShellContribution.class);
        file.intvalue("position", registration.position());
        if (!profiles.isEmpty()) {
            file.stringvalue("profiles", String.join(",", profiles));
        }
        file.write();
    }

    private void validateContributionType(Element element) throws LayerGenerationException {
        if (!(element instanceof TypeElement type) || type.getKind() != ElementKind.CLASS) {
            throw new LayerGenerationException("Shell contributions must be classes", element);
        }
        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new LayerGenerationException("Shell contributions must not be abstract", element);
        }
        if (!type.getModifiers().contains(Modifier.PUBLIC)) {
            throw new LayerGenerationException("Shell contributions must be public", element);
        }
        if (type.getNestingKind().isNested() && !type.getModifiers().contains(Modifier.STATIC)) {
            throw new LayerGenerationException("Nested shell contributions must be static", element);
        }
        TypeElement contract = processingEnv.getElementUtils().getTypeElement(
                ShellContribution.class.getCanonicalName());
        if (contract == null || !processingEnv.getTypeUtils().isAssignable(type.asType(), contract.asType())) {
            throw new LayerGenerationException("Shell contributions must implement ShellContribution", element);
        }
    }

    private static boolean hasPublicNoArgConstructor(Element element) {
        return element.getEnclosedElements().stream()
                .anyMatch(enclosed -> enclosed.getKind() == ElementKind.CONSTRUCTOR
                        && ((ExecutableElement) enclosed).getParameters().isEmpty()
                        && enclosed.getModifiers().contains(Modifier.PUBLIC));
    }

    private static boolean isToken(String value) {
        return value != null && !value.isBlank()
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character) || character == ',');
    }
}
