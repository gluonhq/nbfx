package com.gluonhq.netbeans.nbfx.api.usages;

import java.io.IOException;
import org.openide.filesystems.FileObject;

/** Resolves a language-specific Find Usages target. */
public interface UsagesProvider {

    boolean handles(FileObject file);

    SearchTarget targetAt(FileObject file, int offset, UsageSources sources) throws IOException;

    SearchTarget topLevelTarget(FileObject file, UsageSources sources) throws IOException;
}
