package dev.lukebemish.centralportalpublishing;

import groovy.json.JsonSlurper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@UntrackedTask(because = "This task uploads to a remote repository and thus should not be marked up-to-date.")
public abstract class UploadBundleTask extends DefaultTask {
    @Input
    public abstract RegularFileProperty getBundleFile();

    @Nested
    public abstract CentralPortalBundleSpec getBundleSpec();

    @Inject
    public UploadBundleTask() {}

    @TaskAction
    public void execute() {
        var bundlePath = getBundleFile().get().getAsFile().toPath();

        try {
            uploadBundle(bundlePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void uploadBundle(Path bundlePath) throws IOException {
        var body = new MultipartBody.Builder()
            .addFormDataPart(
                "bundle",
                bundlePath.getFileName().toString(),
                RequestBody.create(bundlePath.toFile(), MediaType.get("application/zip"))
            )
            .build();

        var publishingType = getBundleSpec().getPublishingType().get();

        var url = getBundleSpec().getPortalUrl().get() + "api/v1/publisher/upload?publishingType=" + publishingType;
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("URL must use HTTPS");
        }

        var client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(60))
            .build();

        String deployment;
        try (var result = client.newCall(new Request.Builder()
            .header("Authorization", "Bearer "+authBase64())
            .post(body)
            .url(url)
            .build()
        ).execute()) {
            if (!result.isSuccessful()) {
                throw new IOException("Failed to upload bundle (status " + result.code() + "):  " + result.message());
            }
            deployment = Objects.requireNonNull(result.body()).string();
        }

        var timeoutSeconds = Duration.ofSeconds(getBundleSpec().getVerificationTimeoutSeconds().get());
        var betweenRequests = Duration.ofSeconds(2);
        if (timeoutSeconds.isPositive()) {
            final var start = Instant.now();
            while (true) {
                if (Instant.now().isAfter(start.plus(timeoutSeconds))) {
                    throw new IOException("Timed out waiting for bundle to be verified. You may need to check the deployment on the Central Portal UI ("+getBundleSpec().getPortalUrl().get()+").");
                }
                try {
                    try (var result = client.newCall(new Request.Builder()
                        .header("Authorization", "Bearer " + authBase64())
                        .get()
                        .url(getBundleSpec().getPortalUrl().get() + "api/v1/publisher/status?id=" + deployment)
                        .build()
                    ).execute()) {
                        if (!result.isSuccessful()) {
                            throw new IOException("Failed to verify bundle (status " + result.code() + "):  " + result.message());
                        }
                        var response = Objects.requireNonNull(result.body()).string();
                        var element = new JsonSlurper().parseText(response);
                        if (!(element instanceof Map<?, ?> map) || !(map.get("deploymentState") instanceof String deploymentState)) {
                            throw new IOException("Failed to parse verification response: " + response);
                        }
                        switch (deploymentState) {
                            case "PENDING", "VALIDATING", "PUBLISHING" -> {
                                betweenRequests = delay(betweenRequests);
                                continue;
                            }
                            case "VALIDATED" -> getLogger().lifecycle("Deployment passed validation and ready to manually deploy.");
                            case "PUBLISHED" -> getLogger().lifecycle("Deployment was successfully published.");
                            case "FAILED" -> throw new IOException("Deployment failed. Check the Central Portal UI ("+getBundleSpec().getPortalUrl().get()+") for more details.");
                            default -> throw new IOException("Unknown deployment state: " + deploymentState);
                        }
                        break;
                    } catch (SocketTimeoutException ignored) {
                        // Could just be central being slow -- give it till the main timeout
                        betweenRequests = delay(betweenRequests);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static Duration delay(Duration betweenRequests) throws InterruptedException {
        Thread.sleep(betweenRequests.toMillis());
        betweenRequests = betweenRequests.multipliedBy(2);
        if (betweenRequests.getSeconds() > 64) {
            betweenRequests = Duration.ofSeconds(64);
        }
        return betweenRequests;
    }

    private String authBase64() {
        var tokenUnEncoded = getBundleSpec().getUsername().get() + ":" + getBundleSpec().getPassword().get();
        return Base64.getEncoder().encodeToString(tokenUnEncoded.getBytes(StandardCharsets.UTF_8));
    }
}
