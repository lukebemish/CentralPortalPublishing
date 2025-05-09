package dev.lukebemish.centralportalpublishing;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.initialization.Settings;

public abstract class CentralPortalPublishingPlugin implements Plugin<Project> {
    static final Attribute<String> UPLOADS_BUNDLE = Attribute.of("dev.lukebemish.central-portal-publishing.internal.uploads-bundle", String.class);

    @Override
    public void apply(Project project) {
        project.getDependencies().getAttributesSchema().attribute(UPLOADS_BUNDLE, schema -> {
            schema.getCompatibilityRules().add(BundleCompatibilityRule.class);
        });
        project.getExtensions().create("centralPortalPublishing", CentralPortalProjectExtension.class);
    }

    public static abstract class BundleCompatibilityRule implements AttributeCompatibilityRule<String> {
        @Override
        public void execute(CompatibilityCheckDetails<String> result) {
            if (result.getConsumerValue() == null || result.getProducerValue() == null || result.getConsumerValue().isEmpty() || result.getProducerValue().isEmpty() || result.getProducerValue().equals(result.getConsumerValue())) {
                result.compatible();
            } else {
                result.incompatible();
            }
        }
    }
}
