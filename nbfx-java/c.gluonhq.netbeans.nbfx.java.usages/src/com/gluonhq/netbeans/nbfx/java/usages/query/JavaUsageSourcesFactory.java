package com.gluonhq.netbeans.nbfx.java.usages.query;

import com.gluonhq.netbeans.nbfx.api.usages.QueryOptions;
import com.gluonhq.netbeans.nbfx.api.usages.UsageSources;
import com.gluonhq.netbeans.nbfx.api.usages.UsageSourcesFactory;
import org.openide.filesystems.FileObject;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = UsageSourcesFactory.class)
public final class JavaUsageSourcesFactory implements UsageSourcesFactory {

    @Override
    public UsageSources forScope(QueryOptions.Scope scope, FileObject file) {
        return SourceSet.forScope(scope, file);
    }
}
