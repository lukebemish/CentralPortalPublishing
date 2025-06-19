package dev.lukebemish.centralportalpublishing;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

@DisableCachingByDefault(because = "Makes no sense to cache")
public abstract class ClearRepositoryTask extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getRepositoryDirectory();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    public ClearRepositoryTask() {
        this.getOutputs().upToDateWhen(t -> false);
    }

    @TaskAction
    public void run() throws IOException {
        var dir = getRepositoryDirectory().get().getAsFile();
        if (dir.exists()) {
            getFileSystemOperations().delete(spec -> spec.delete(dir));
        }
        Files.createDirectories(dir.toPath());
    }
}
