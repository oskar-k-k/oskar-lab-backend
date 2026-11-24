package dev.oskar_lab.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.io.*;

/** Server-only client using per-app project keys; OrbitStorage exclusively owns file bytes. */
@Component
public class OrbitStorageClient {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private final String baseUrl;
    private final String keysFile;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();

    public OrbitStorageClient(@Value("${orbit.base-url:}") String baseUrl, @Value("${orbit.project-keys-file:}") String keysFile) {
        this.baseUrl = baseUrl; this.keysFile = keysFile;
    }

    /** Uploads raw known-length bytes once. Non-idempotent uploads are never automatically retried. */
    public StoredFile upload(String project, String filename, String mediaType, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new StorageFailure(413, "fileTooLarge");
        try {
            var response = http.send(request(project, "/api/v1/files").header("X-Filename", filename).header("Content-Type", mediaType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 201) throw rejection(response.statusCode());
                JsonNode metadata = json.readTree(limited(body, 65536));
                StoredFile stored = metadata(project, metadata);
                if (stored.size() != bytes.length || !stored.mediaType().equals(mediaType)) throw new StorageFailure(503, "storageInvalidResponse");
                return stored;
            }
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new StorageFailure(503, "storageUnavailable"); }
        catch (IOException | IllegalArgumentException error) { throw new StorageFailure(503, "storageUnavailable"); }
    }

    /** Reads metadata only within the configured project's namespace. */
    public StoredFile metadata(String project, UUID id) {
        try {
            var response = http.send(request(project, "/api/v1/files/" + id + "/metadata").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) throw rejection(response.statusCode());
                StoredFile stored = metadata(project, json.readTree(limited(body, 65536)));
                if (!stored.id().equals(id)) throw new StorageFailure(503, "storageInvalidResponse");
                return stored;
            }
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new StorageFailure(503, "storageUnavailable"); }
        catch (IOException | IllegalArgumentException error) { throw new StorageFailure(503, "storageUnavailable"); }
    }

    /** Checks length and SHA-256 before returning bytes; upstream errors never become a successful file. */
    public byte[] download(String project, UUID id, long size, String sha256) {
        if (size < 1 || size > MAX_BYTES) throw new StorageFailure(503, "storageInvalidResponse");
        try {
            var response = http.send(request(project, "/api/v1/files/" + id).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 200) throw rejection(response.statusCode());
                byte[] bytes = limited(body, (int)size);
                if (bytes.length != size || !hash(bytes).equals(sha256)) throw new StorageFailure(503, "storageIntegrityFailed");
                return bytes;
            }
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new StorageFailure(503, "storageUnavailable"); }
        catch (IOException error) { throw new StorageFailure(503, "storageUnavailable"); }
    }

    /** Treats an already absent object as deleted so durable cleanup can retry safely. */
    public void delete(String project, UUID id) {
        try {
            var response = http.send(request(project, "/api/v1/files/" + id).DELETE().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() != 204 && response.statusCode() != 404) throw rejection(response.statusCode());
            }
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new StorageFailure(503, "storageUnavailable"); }
        catch (IOException error) { throw new StorageFailure(503, "storageUnavailable"); }
    }

    /** Computes a content digest without encoding or logging the file. */
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    /** Opaque file reference returned by OrbitStorage; credentials never enter domain DTOs. */
    public record StoredFile(UUID id, String mediaType, long size) {}

    private HttpRequest.Builder request(String project, String path) {
        try {
            URI base = URI.create(baseUrl);
            if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null ||
                base.getQuery() != null || base.getFragment() != null || !(base.getPath().isEmpty() || base.getPath().equals("/")))
                throw new IllegalArgumentException();
            return HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + credentials(project).path("key").asText());
        } catch (IllegalArgumentException error) { throw new StorageFailure(503, "storageUnconfigured"); }
    }

    private JsonNode credentials(String project) {
        if (!project.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new StorageFailure(400, "invalidStorageProject");
        try {
            JsonNode key = json.readTree(Files.readString(Path.of(keysFile))).path(project);
            UUID.fromString(key.path("projectId").asText());
            if (key.path("key").asText().length() < 20) throw new IllegalArgumentException();
            return key;
        } catch (IOException | IllegalArgumentException error) { throw new StorageFailure(503, "storageUnconfigured"); }
    }

    private StoredFile metadata(String project, JsonNode node) {
        try {
            if (!node.path("projectId").asText().equals(credentials(project).path("projectId").asText()) ||
                !node.path("status").asText().equals("READY") || !node.path("actualSize").isIntegralNumber() ||
                node.path("actualSize").asLong() < 1 || node.path("actualSize").asLong() > MAX_BYTES || !node.path("contentType").isTextual())
                throw new IllegalArgumentException();
            return new StoredFile(UUID.fromString(node.path("id").asText()), node.path("contentType").asText(), node.path("actualSize").asLong());
        } catch (IllegalArgumentException error) { throw new StorageFailure(503, "storageInvalidResponse"); }
    }
    private byte[] limited(InputStream stream, int maximum) throws IOException {
        byte[] bytes = stream.readNBytes(maximum + 1);
        if (bytes.length > maximum) throw new StorageFailure(503, "storageInvalidResponse");
        return bytes;
    }
    private StorageFailure rejection(int status) {
        return switch (status) {
            case 413 -> new StorageFailure(413, "fileTooLarge");
            case 415 -> new StorageFailure(415, "unsupportedAttachment");
            case 429 -> new StorageFailure(429, "storageRateLimited");
            case 409 -> new StorageFailure(409, "storageQuotaOrStateConflict");
            default -> new StorageFailure(503, "storageUnavailable");
        };
    }
}
