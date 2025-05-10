package dev.lukebemish.centralportalpublishing;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;

public abstract class CentralPortalPublishingPlugin implements Plugin<Project> {
    static final Attribute<String> UPLOADS_BUNDLE = Attribute.of("dev.lukebemish.central-portal-publishing.internal.uploads-bundle", String.class);

    @Override
    public void apply(Project project) {
        project.getDependencies().getAttributesSchema().attribute(UPLOADS_BUNDLE);
        project.getExtensions().create("centralPortalPublishing", CentralPortalProjectExtension.class);
    }
}
