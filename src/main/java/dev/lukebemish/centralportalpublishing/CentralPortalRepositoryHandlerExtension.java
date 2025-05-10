package dev.lukebemish.centralportalpublishing;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.ProjectLayout;

import javax.inject.Inject;

public abstract class CentralPortalRepositoryHandlerExtension {
    private final RepositoryHandler delegate;
    static final String UPLOADS_BUNDLE_CONFIGURATION = "_centralPortalPublishingUploadsBundle";

    @Inject
    public CentralPortalRepositoryHandlerExtension(RepositoryHandler delegate) {
        this.delegate = delegate;
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract Project getProject();

    public MavenArtifactRepository centralSnapshots() {
        return centralSnapshots(r -> {});
    }

    public MavenArtifactRepository centralSnapshots(Action<? super MavenArtifactRepository> action) {
        var repo = delegate.maven(r -> {
            r.setUrl("https://central.sonatype.com/repository/maven-snapshots/ ");
        });
        action.execute(repo);
        return repo;
    }

    public void portalBundle(String path, String name) {
        var fullName = CentralPortalProjectExtension.attrValue(path, name);
        var repoDirectory = getProjectLayout().getBuildDirectory().dir("centralPortalPublishing/repositories/"+fullName);

        var repo = delegate.maven(r -> {
            r.setName("centralPortal"+StringUtils.capitalize(fullName));
            r.setUrl(repoDirectory.get().getAsFile().toURI());
        });

        var publishTask = getProject().getTasks().named("publishAllPublicationsToCentralPortal" + StringUtils.capitalize(fullName) +"Repository");
        var outgoing = getProject().getConfigurations().consumable(UPLOADS_BUNDLE_CONFIGURATION + StringUtils.capitalize(fullName), config -> {
            config.getAttributes().attribute(CentralPortalPublishingPlugin.UPLOADS_BUNDLE, fullName);
        });
        getProject().getArtifacts().add(outgoing.getName(), repoDirectory, artifact -> {
            artifact.builtBy(publishTask);
        });
    }
}
