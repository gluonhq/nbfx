package com.gluonhq.netbeans.nbfx.api.usages;

import java.io.IOException;
import java.util.Set;
import org.openide.filesystems.FileObject;

/** The files a Find Usages run searches, and the text of each. */
public interface UsageSources {

    Set<FileObject> roots();

    Set<FileObject> files();

    boolean accepts(FileObject file);

    FileObject rootOf(FileObject file);

    String packageNameOf(FileObject file);

    String textOf(FileObject file) throws IOException;
}
