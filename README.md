# central-portal-publishing

A Gradle plugin for Maven Central publishing with the new central portal system; this plugin is built specifically to be
configuration-cache-compatible and project-isolation-compatible out of the box.

## Usage

Apply the plugin to every project that will be part of the maven central bundle publication:

```gradle
plugins {
    id 'dev.lukebemish.central-portal-publishing' version '<version>'
}
```

Then, define a bundle in one project:

```gradle
centralPortalPublishing.bundle('bundleName') {
    // These should be the username/password combination of a portal token -- see https://central.sonatype.org/publish/generate-portal-token/
    username = ...
    password = ...

    // (default) a deployment will go through validation and require the user to manually publish it via the Portal UI
    publishingType = "USER_MANAGED"

    // a deployment will go through validation and, if it passes, automatically proceed to publish to Maven Central
    // publishingType = "AUTOMATIC"
}
```

The bundle defines a corresponding publishing task, `publishBundleNameCentralPortalBundle`. To use the bundle as a
publishing target, use the `centralPortalPublishing` extension in `publishing.repositories`, giving it the project the
bundle is defined in as well as the bundle's name:

```gradle
publishing {
    repositories {
        // Publish to a bundle named bundleName, in the root project
        centralPortalPublishing.portalBundle(':', 'bundleName')
    }
}
```
