package dev.lukebemish.centralportalpublishing;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.publish.PublishingExtension;

import javax.inject.Inject;
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

    public void bundle(String name) {
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
        getProject().getTasks().register("publish"+StringUtils.capitalize(name)+"CentralPortalBundle", UploadBundleTask.class, task -> {
            task.getBundleDependencies().from(configuration.map(config -> config.getIncoming().artifactView(view -> {
                view.setLenient(true); // So that we act as expected with projects that do not use this bundle
                view.withVariantReselection();
                view.getAttributes().attribute(CentralPortalPublishingPlugin.UPLOADS_BUNDLE, attrValue);
            }).getArtifacts().getArtifactFiles()));
            task.getBundleFile().set(getProject().getLayout().getBuildDirectory().file("centralPortalPublishing/bundles/"+name+".jar"));;
        });
    }
}
