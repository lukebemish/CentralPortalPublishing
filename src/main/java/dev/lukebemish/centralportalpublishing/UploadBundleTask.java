package dev.lukebemish.centralportalpublishing;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@UntrackedTask(because = "This task uploads to a remote repository and thus should not be marked up-to-date.")
public abstract class UploadBundleTask extends DefaultTask {
    @Internal
    public abstract ConfigurableFileCollection getBundleDependencies();

    @Internal
    public abstract RegularFileProperty getBundleFile();

    @Inject
    public UploadBundleTask() {
        this.dependsOn(getBundleDependencies());
    }

    @TaskAction
    public void execute() {
        var bundleDependencies = getBundleDependencies().getFiles();

        // Bundle the deps to a jar

        var bundlePath = getBundleFile().get().getAsFile().toPath();
        try {
            if (Files.exists(bundlePath)) {
                Files.delete(bundlePath);
            }
            Files.createDirectories(bundlePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete existing bundle file", e);
        }


        try (var zis = new ZipOutputStream(Files.newOutputStream(bundlePath))) {
            for (var file : bundleDependencies) {
                if (!file.exists()) {
                    continue;
                }
                try (var contained = Files.walk(file.toPath())) {
                    var files = new HashSet<>();
                    contained.forEach(p -> {
                        try {
                            var relative = file.toPath().relativize(p).toString().replace('\\', '/');
                            if (!Files.isDirectory(p)) {
                                // Skip directories outright
                                var fileName = p.getFileName().toString();
                                if (fileName.startsWith("maven-metadata.xml.") || fileName.equals("maven-metadata.xml")) {
                                    return;
                                }
                                if (files.add(relative)) {
                                    zis.putNextEntry(new ZipEntry(relative));
                                    Files.copy(p, zis);
                                } else {
                                    throw new RuntimeException("Duplicate file in bundle: " + relative);
                                }
                            } else {
                                // Just skip it
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
