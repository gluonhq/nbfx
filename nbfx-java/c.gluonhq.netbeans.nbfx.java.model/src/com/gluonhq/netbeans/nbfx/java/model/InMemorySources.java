package com.gluonhq.netbeans.nbfx.java.model;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.netbeans.api.java.classpath.ClassPath;
import org.openide.filesystems.FileObject;
import com.gluonhq.netbeans.nbfx.api.editor.InMemorySourceFactory;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

/**
 * In-memory copies of source files carrying the text of an open editor instead of the content on
 * disk, so javac analyses see unsaved edits. Centralised here so the three former copies stay in sync.
 */
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = InMemorySourceFactory.class)
public final class InMemorySources implements com.gluonhq.netbeans.nbfx.api.editor.InMemorySourceFactory {

    public InMemorySources() {
    }

    /**
     * A memory {@link FileObject} named and placed like {@code original} under a fresh memory file
     * system, holding {@code text}. Mirrors the file's place under its source root (package folders,
     * and the root's {@code module-info.java} for module context).
     */
    public static FileObject copyOf(FileObject original, String text) throws IOException {
        FileSystem memory = FileUtil.createMemoryFileSystem();
        FileObject root = memory.getRoot();
        if ("module-info".equals(original.getName())) {
            return write(root.createData("module-info.java"), text);
        }
        ClassPath sourcePath = ClassPath.getClassPath(original, ClassPath.SOURCE);
        FileObject sourceRoot = sourcePath == null ? null : sourcePath.findOwnerRoot(original);
        if (sourceRoot != null) {
            FileObject moduleInfo = sourceRoot.getFileObject("module-info.java");
            if (moduleInfo != null && !moduleInfo.equals(original)) {
                write(root.createData("module-info.java"), moduleInfo.asText());
            }
        }
        String relative = sourceRoot == null ? null : FileUtil.getRelativePath(sourceRoot, original);
        FileObject folder = root;
        if (relative != null && relative.contains("/")) {
            folder = FileUtil.createFolder(root, relative.substring(0, relative.lastIndexOf('/')));
        }
        return write(folder.createData(original.getNameExt()), text);
    }

    /**
     * Creates an in-memory {@link FileObject} for the given {@code source} that mirrors the
     * project's source-root layout around {@code fileObject}, including the module-info descriptor.
     */
    public static FileObject createFileObject(FileObject fileObject, String source) throws IOException {
        FileSystem memoryFileSystem = FileUtil.createMemoryFileSystem();
        FileObject memoryRoot = memoryFileSystem.getRoot();
        if ("module-info".equals(fileObject.getName())) {
            FileObject tempFileObject = memoryRoot.createData("module-info.java");
            write(tempFileObject, source);
            return tempFileObject;
        }

        ClassPath srcPath = ClassPath.getClassPath(fileObject, ClassPath.SOURCE);
        FileObject srcRoot = srcPath != null ? srcPath.findOwnerRoot(fileObject) : null;
        if (srcRoot != null) {
            FileObject realModuleInfo = srcRoot.getFileObject("module-info.java");
            if (realModuleInfo != null) {
                FileObject tempFileObject = memoryRoot.createData("module-info.java");
                write(tempFileObject, realModuleInfo.asText());
            }
        }
        FileObject parentDir = resolvePackageDir(memoryRoot, srcRoot, fileObject);
        FileObject tempFo = parentDir.createData(fileObject.getNameExt());
        write(tempFo, source);
        return tempFo;
    }

    private static FileObject resolvePackageDir(FileObject memRoot, FileObject srcRoot, FileObject fileObject)
            throws IOException {
        String relPath = srcRoot != null ? FileUtil.getRelativePath(srcRoot, fileObject) : fileObject.getNameExt();
        if (relPath != null && relPath.contains("/")) {
            String dir = relPath.substring(0, relPath.lastIndexOf('/'));
            return FileUtil.createFolder(memRoot, dir);
        }
        return memRoot;
    }

    private static FileObject write(FileObject file, String text) throws IOException {
        try (OutputStream os = file.getOutputStream()) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
    @Override
    public FileObject copy(FileObject original, String text) throws IOException {
        return InMemorySources.copyOf(original, text);
    }

    @Override
    public FileObject create(FileObject original, String text) throws IOException {
        return InMemorySources.createFileObject(original, text);
    }

}
