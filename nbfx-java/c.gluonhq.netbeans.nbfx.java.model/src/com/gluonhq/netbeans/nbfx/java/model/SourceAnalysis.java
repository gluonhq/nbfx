package com.gluonhq.netbeans.nbfx.java.model;

import com.gluonhq.netbeans.nbfx.api.elements.SourceElementKind;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.lang.model.element.Element;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.openide.filesystems.FileObject;

/** The Java semantic bridge: classpath cache, in-memory sources and task packaging. */
public interface SourceAnalysis {

    /** A memory copy of {@code original} holding {@code text}. */
    FileObject inMemoryFile(FileObject original, String text) throws IOException;

    /** The classpath of {@code owner}, cached unless {@code refresh}. */
    ClasspathInfo classpath(FileObject owner, boolean refresh);

    /** Runs {@code body} over {@code file}, reading {@code text} when non-null. */
    <R> R read(FileObject file, String text, Function<CompilationInfo, R> body) throws IOException;

    /** Runs {@code task} over {@code file} asynchronously. */
    CompletableFuture<Void> analyze(FileObject file, String text,
            CancellableTask<CompilationController> task);

    SourceElementKind kindOf(Element element);

    Set<?> modifiersOf(Element element);
}
