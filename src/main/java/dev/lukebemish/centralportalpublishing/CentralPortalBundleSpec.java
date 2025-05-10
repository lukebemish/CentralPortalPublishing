package dev.lukebemish.centralportalpublishing;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

import javax.inject.Inject;

public abstract class CentralPortalBundleSpec {
    @Internal
    public abstract Property<String> getUsername();

    @Internal
    public abstract Property<String> getPassword();

    @Input
    public abstract Property<String> getPortalUrl();

    @Input
    public abstract Property<String> getPublishingType();

    @Inject
    public CentralPortalBundleSpec() {
        getPortalUrl().convention("https://central.sonatype.com/");
        getVerificationTimeoutSeconds().convention(10L*60);
        getPublishingType().convention("USER_MANAGED");
    }

    @Internal
    public abstract Property<Long> getVerificationTimeoutSeconds();
}
