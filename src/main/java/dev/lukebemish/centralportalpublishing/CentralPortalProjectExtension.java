package dev.lukebemish.centralportalpublishing;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.tasks.bundling.Zip;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;

public abstract class CentralPortalProjectExtension {
    static final String CONSUMES_BUNDLE_UPLOAD_DEPENDENCIES = "_centralPortalPublishingConsumesBundleUploadDependencies";
    static final String CONSUMES_BUNDLE_UPLOAD = "_centralPortalPublishingConsumesBundleUpload";

    @Inject
    public CentralPortalProjectExtension() {
        getProject().getPluginManager().withPlugin("publishing", ignored -> {
            var repositories = getProject().getExtensions().getByType(PublishingExtension.class).getRepositories();
            var extensions = (ExtensionAware) repositories;
            extensions.getExtensions().add(CentralPortalRepositoryHandlerExtension.class, "centralPortalPublishing", getObjects().newInstance(CentralPortalRepositoryHandlerExtension.class, repositories));
        });
    }

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract Project getProject();

    static String attrValue(String project, String name) {
        var parts = project.split(":");
        StringBuilder attrValue = new StringBuilder();
        boolean isFirst = true;
        for (var part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (isFirst) {
                isFirst = false;
                attrValue.append(part);
            } else {
                attrValue.append(StringUtils.capitalize(part));
            }
        }
        if (isFirst) {
            attrValue.append(name);
        } else {
            attrValue.append(StringUtils.capitalize(name));
        }
        return attrValue.toString();
    }

    public void bundle(String name, Action<? super CentralPortalBundleSpec> action) {
        String attrValue = attrValue(getProject().getPath(), name);
        var depConfiguration = getProject().getConfigurations().dependencyScope(CONSUMES_BUNDLE_UPLOAD_DEPENDENCIES + StringUtils.capitalize(name));
        var configuration = getProject().getConfigurations().resolvable(CONSUMES_BUNDLE_UPLOAD + StringUtils.capitalize(name), config -> {
            config.getAttributes().attribute(CentralPortalPublishingPlugin.UPLOADS_BUNDLE, attrValue);
            config.extendsFrom(depConfiguration.get());
            config.setTransitive(false);
        });
        getProject().getAllprojects().forEach(p -> {
            var isolated = p.getIsolated();
            getProject().getDependencies().add(depConfiguration.getName(), getProject().getDependencies().project(Map.of(
                "path", isolated.getPath()
            )));
        });
        var makeBundle = getProject().getTasks().register("make"+StringUtils.capitalize(name)+"CentralPortalBundle", Zip.class, task -> {
            task.getDestinationDirectory().set(getProject().getLayout().getBuildDirectory().dir("centralPortalPublishing/bundles"));
            task.getArchiveFileName().set(name+".zip");
            var bundleDependencies = getProject().files();
            var artifacts = configuration.map(config -> config.getIncoming().artifactView(view -> {
                view.setLenient(true); // So that we act as expected with projects that do not use this bundle
                view.withVariantReselection();
                view.getAttributes().attribute(CentralPortalPublishingPlugin.UPLOADS_BUNDLE, attrValue);
            }).getArtifacts());
            bundleDependencies.from(artifacts.flatMap(ArtifactCollection::getResolvedArtifacts).map(set -> {
                var files = new ArrayList<File>();
                for (var resolved : set) {
                    if (attrValue.equals(resolved.getVariant().getAttributes().getAttribute(CentralPortalPublishingPlugin.UPLOADS_BUNDLE))) {
                        files.add(resolved.getFile());
                    }
                }
                return files;
            }));
            bundleDependencies.builtBy(artifacts.map(ArtifactCollection::getArtifactFiles));
            task.dependsOn(bundleDependencies);
            task.from(bundleDependencies.getAsFileTree(), spec -> {
                spec.exclude("**/maven-metadata.xml");
                spec.exclude("**/maven-metadata.xml.*");
            });
        });
        var publishBundle = getProject().getTasks().register("publish"+StringUtils.capitalize(name)+"CentralPortalBundle", UploadBundleTask.class, task -> {
            task.setGroup("publishing");
            action.execute(task.getBundleSpec());
            task.getBundleFile().set(makeBundle.flatMap(Zip::getArchiveFile));
            task.dependsOn(makeBundle);
        });
        getProject().getTasks().named("publish", task -> {
            task.dependsOn(publishBundle);
        });
    }
}
