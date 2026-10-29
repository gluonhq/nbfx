package com.gluonhq.netbeans.nbfx.java.model;

import com.gluonhq.netbeans.nbfx.api.elements.SourceElementKind;
import com.gluonhq.netbeans.nbfx.api.elements.SourceModifiers;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.JavaSource;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = SourceAnalysis.class)
public final class DefaultSourceAnalysis implements SourceAnalysis {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-source-analysis", 2, true);
    private final ConcurrentMap<FileObject, ClasspathInfo> classpaths = new ConcurrentHashMap<>();

    @Override
    public FileObject inMemoryFile(FileObject original, String text) throws IOException {
        return InMemorySources.copyOf(original, text);
    }

    @Override
    public ClasspathInfo classpath(FileObject owner, boolean refresh) {
        if (refresh) {
            ClasspathInfo created = ClasspathInfo.create(owner);
            classpaths.put(owner, created);
            return created;
        }
        return classpaths.computeIfAbsent(owner, ClasspathInfo::create);
    }

    @Override
    public <R> R read(FileObject file, String text, Function<CompilationInfo, R> body) throws IOException {
        FileObject source = text == null ? file : inMemoryFile(file, text);
        JavaSource javaSource = JavaSource.create(classpath(file, false), source);
        CompletableFuture<R> result = new CompletableFuture<>();
        javaSource.runUserActionTask(info -> {
            if (info.toPhase(JavaSource.Phase.RESOLVED).compareTo(JavaSource.Phase.RESOLVED) < 0) {
                result.complete(null);
                return;
            }
            result.complete(body.apply(info));
        }, true);
        return result.join();
    }

    @Override
    public CompletableFuture<Void> analyze(FileObject file, String text,
            CancellableTask<CompilationController> task) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        RP.post(() -> {
            try {
                FileObject source = text == null ? file : inMemoryFile(file, text);
                JavaSource.create(classpath(file, false), source).runUserActionTask(task, true);
                done.complete(null);
            } catch (IOException | RuntimeException ex) {
                done.completeExceptionally(ex);
            }
        });
        return done;
    }

    @Override
    public SourceElementKind kindOf(Element element) {
        if (element == null) {
            return SourceElementKind.OTHER;
        }
        ElementKind kind = element.getKind();
        return switch (kind) {
            case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> SourceElementKind.TYPE;
            case METHOD, CONSTRUCTOR -> SourceElementKind.METHOD;
            case FIELD, ENUM_CONSTANT, RECORD_COMPONENT -> SourceElementKind.FIELD;
            case PACKAGE -> SourceElementKind.PACKAGE;
            case MODULE -> SourceElementKind.MODULE;
            case LOCAL_VARIABLE, PARAMETER, EXCEPTION_PARAMETER, RESOURCE_VARIABLE,
                    BINDING_VARIABLE -> SourceElementKind.VARIABLE;
            default -> SourceElementKind.OTHER;
        };
    }

    @Override
    public Set<?> modifiersOf(Element element) {
        return element == null ? Set.of() : element.getModifiers();
    }

    public int modifierBits(Element element) {
        return SourceModifiers.toModifierBits(modifiersOf(element));
    }
}
