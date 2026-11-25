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
package org.netbeans.modules.nbfx.api.actions.processors;

import org.netbeans.api.nbfx.actions.ActionReference;
import org.netbeans.api.nbfx.actions.ActionReferences;
import org.netbeans.api.nbfx.actions.ActionRegistration;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.layer.LayerPaths;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import org.openide.filesystems.annotations.LayerBuilder.File;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Generates the layer entries for {@link ActionRegistration} and {@link ActionReference}:
 * an action instance under {@code NbFx/Actions/<id>.instance} and one
 * {@code NbFx/<path>/<id>.ref} reference per declared UI surface.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class ActionRegistrationProcessor extends LayerGeneratingProcessor {

    private final Set<String> emittedIds = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                ActionRegistration.class.getCanonicalName(),
                ActionReference.class.getCanonicalName(),
                ActionReferences.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ActionRegistration.class)) {
            ActionRegistration registration = element.getAnnotation(ActionRegistration.class);
            if (registration != null) {
                registerAction(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ActionReference.class)) {
            ActionReference reference = element.getAnnotation(ActionReference.class);
            if (reference != null) {
                registerReference(element, reference);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ActionReferences.class)) {
            ActionReferences references = element.getAnnotation(ActionReferences.class);
            if (references != null) {
                for (ActionReference reference : references.value()) {
                    registerReference(element, reference);
                }
            }
        }
        return true;
    }

    private void registerAction(Element element, ActionRegistration registration)
            throws LayerGenerationException {
        String id = registration.id();
        if (id.isBlank() || !id.equals(id.trim()) || id.indexOf('/') >= 0 || id.indexOf('\\') >= 0
                || ".".equals(id) || "..".equals(id) || id.chars().anyMatch(Character::isISOControl)) {
            throw new LayerGenerationException("@ActionRegistration requires a file-safe id", element);
        }
        validateActionType(element);
        if (!hasPublicNoArgConstructor(element)) {
            throw new LayerGenerationException("Actions require a public no-argument constructor", element);
        }
        if (!emittedIds.add(id)) {
            throw new LayerGenerationException("Duplicate @ActionRegistration id: " + id, element);
        }
        File file = layer(element).instanceFile(LayerPaths.ACTIONS, id, Command.class);
        file.stringvalue("displayName", registration.displayName());
        file.stringvalue("iconName", registration.iconName());
        file.stringvalue("accelerator", registration.accelerator());
        file.intvalue("position", registration.position());
        file.write();
    }

    private void validateActionType(Element element) throws LayerGenerationException {
        if (!(element instanceof TypeElement type) || type.getKind() != ElementKind.CLASS) {
            throw new LayerGenerationException("Actions must be classes", element);
        }
        if (type.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new LayerGenerationException("Actions must not be abstract", element);
        }
        if (!type.getModifiers().contains(Modifier.PUBLIC)) {
            throw new LayerGenerationException("Actions must be public", element);
        }
        if (type.getNestingKind().isNested() && !type.getModifiers().contains(Modifier.STATIC)) {
            throw new LayerGenerationException("Nested actions must be static", element);
        }
        TypeElement contract = processingEnv.getElementUtils().getTypeElement(Command.class.getCanonicalName());
        if (contract == null || !processingEnv.getTypeUtils().isAssignable(type.asType(), contract.asType())) {
            throw new LayerGenerationException("Actions must implement Command", element);
        }
    }

    private static boolean hasPublicNoArgConstructor(Element element) {
        return element.getEnclosedElements().stream()
                .anyMatch(enclosed -> enclosed.getKind() == ElementKind.CONSTRUCTOR
                        && ((ExecutableElement) enclosed).getParameters().isEmpty()
                        && enclosed.getModifiers().contains(Modifier.PUBLIC));
    }

    private void registerReference(Element element, ActionReference reference)
            throws LayerGenerationException {
        String path = "NbFx/" + reference.path() + "/" + reference.id() + LayerPaths.REF_SUFFIX;
        org.openide.filesystems.annotations.LayerBuilder.File file = layer(element).file(path);
        file.stringvalue("actionId", reference.id())
                .intvalue("position", reference.position())
                .boolvalue("separatorBefore", reference.separatorBefore());
        if (!reference.mimeType().isBlank()) {
            file.stringvalue("mimeType", reference.mimeType());
        }
        file.write();
    }
}
