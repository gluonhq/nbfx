package com.gluonhq.netbeans.nbfx.java.usages.query;

import com.gluonhq.netbeans.nbfx.api.usages.SearchTarget;
import com.gluonhq.netbeans.nbfx.api.usages.UsageSources;
import com.gluonhq.netbeans.nbfx.api.usages.UsagesProvider;
import java.io.IOException;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = UsagesProvider.class)
public final class JavaUsagesProvider implements UsagesProvider {

    @Override
    public boolean handles(FileObject file) {
        return file != null && "java".equalsIgnoreCase(file.getExt());
    }

    @Override
    public SearchTarget targetAt(FileObject file, int offset, UsageSources sources) throws IOException {
        return UsagesTarget.at(file, offset, javaSources(sources));
    }

    @Override
    public SearchTarget topLevelTarget(FileObject file, UsageSources sources) throws IOException {
        return UsagesTarget.topLevelTypeOf(file, javaSources(sources));
    }

    private static SourceSet javaSources(UsageSources sources) {
        if (!(sources instanceof SourceSet javaSources)) {
            throw new IllegalArgumentException("Java usages need a Java source set");
        }
        return javaSources;
    }
}
