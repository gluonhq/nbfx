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
package org.netbeans.modules.nbfx.api.content.processors;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.content.DropHandler;
import org.netbeans.api.nbfx.content.DropRegistration;
import org.netbeans.api.nbfx.content.DropRegistrations;
import org.netbeans.api.nbfx.content.DropTargetKind;
import org.netbeans.api.nbfx.content.TransferKind;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one {@code NbFx/DropHandlers} instance per {@link DropRegistration}.
 *
 * @since 1.5
 */
@ServiceProvider(service = Processor.class)
public class DropRegistrationProcessor extends LayerGeneratingProcessor {

    private final Set<String> emitted = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                DropRegistration.class.getCanonicalName(),
                DropRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(DropRegistration.class)) {
            DropRegistration registration = element.getAnnotation(DropRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(DropRegistrations.class)) {
            DropRegistrations registrations = element.getAnnotation(DropRegistrations.class);
            if (registrations != null) {
                for (DropRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, DropRegistration registration) throws LayerGenerationException {
        validateHandlerType(element);
        if (!hasPublicNoArgConstructor(element)) {
            throw new LayerGenerationException("Drop handlers require a public no-argument constructor", element);
        }
        DropTargetKind targetKind;
        try {
            targetKind = DropTargetKind.of(registration.targetKind());
        } catch (RuntimeException ex) {
            throw new LayerGenerationException("Invalid drop target kind: " + ex.getMessage(), element);
        }
        String targetId = registration.targetId();
        if (targetId == null || targetId.isBlank()) {
            throw new LayerGenerationException("Drop handler targetId must not be blank", element);
        }
        targetId = targetId.trim();
        Set<String> sources = new TreeSet<>();
        for (String source : registration.sources()) {
            try {
                sources.add(TransferKind.of(source).value());
            } catch (RuntimeException ex) {
                throw new LayerGenerationException("Invalid drop source kind: " + ex.getMessage(), element);
            }
        }
        if (sources.isEmpty()) {
            throw new LayerGenerationException("Drop handler sources must not be empty", element);
        }
        String className = ((TypeElement) element).getQualifiedName().toString();
        String key = className + "/" + targetKind.value() + "/" + targetId;
        if (!emitted.add(key)) {
            throw new LayerGenerationException("Duplicate drop handler registration: " + key, element);
        }
        String fileName = (className + '-' + targetKind.value() + '-' + targetId)
                .replace('.', '-').replace('$', '-').replace(':', '-').replaceAll("[^A-Za-z0-9_-]", "-");
        LayerBuilder.File file = layer(element).instanceFile(LayerPaths.DROP_HANDLERS, fileName, DropHandler.class);
        file.intvalue("position", registration.position());
        file.stringvalue("targetKind", targetKind.value());
        file.stringvalue("targetId", targetId);
        file.stringvalue("sources", String.join(",", sources));
        file.write();
    }

    private void validateHandlerType(Element element) throws LayerGenerationException {
        if (!(element instanceof TypeElement type) || type.getKind() != ElementKind.CLASS) {
            throw new LayerGenerationException("Drop handlers must be classes", element);
        }
        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new LayerGenerationException("Drop handlers must not be abstract", element);
        }
        if (!type.getModifiers().contains(Modifier.PUBLIC)) {
            throw new LayerGenerationException("Drop handlers must be public", element);
        }
        if (type.getNestingKind().isNested() && !type.getModifiers().contains(Modifier.STATIC)) {
            throw new LayerGenerationException("Nested drop handlers must be static", element);
        }
        TypeElement contract = processingEnv.getElementUtils().getTypeElement(DropHandler.class.getCanonicalName());
        if (contract == null || !processingEnv.getTypeUtils().isAssignable(type.asType(), contract.asType())) {
            throw new LayerGenerationException("Drop handler registrations must implement DropHandler", element);
        }
    }

    private static boolean hasPublicNoArgConstructor(Element element) {
        boolean hasCtor = false;
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                hasCtor = true;
                if (enclosed.getModifiers().contains(Modifier.PUBLIC)
                        && ((javax.lang.model.element.ExecutableElement) enclosed).getParameters().isEmpty()) {
                    return true;
                }
            }
        }
        return !hasCtor; // implicit default ctor
    }
}
