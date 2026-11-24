package dev.oskar_lab.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Exact-path migration allowlist; possession of an Orbit file ID never makes it publicly readable. */
@Component
public class AssetManifest {
    private final String manifest;
    private final ObjectMapper json = new ObjectMapper();
    public AssetManifest(@Value("${orbit.asset-manifest:}") String manifest) { this.manifest = manifest; }

    /** Resolves only previously migrated public assets or an explicitly requested private asset. */
    public Asset get(String project, String path, boolean privateAsset) {
        if (!project.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || !path.startsWith("/") || path.contains("..") || path.contains("\\"))
            throw new StorageFailure(404, "notFound");
        try {
            var node = json.readTree(Files.readString(Path.of(manifest))).path(privateAsset ? "privateAssets" : "publicAssets").path(project + ":" + path);
            if (node.isMissingNode()) throw new StorageFailure(404, "notFound");
            String hash = node.path("sha256").asText();
            if (!hash.matches("[0-9a-f]{64}") || node.path("size").asLong() < 1 || node.path("size").asLong() > OrbitStorageClient.MAX_BYTES)
                throw new IllegalArgumentException();
            return new Asset(UUID.fromString(node.path("fileId").asText()), node.path("mediaType").asText(), node.path("size").asLong(), hash);
        } catch (java.io.IOException | IllegalArgumentException error) { throw new StorageFailure(503, "storageUnconfigured"); }
    }

    /** Public metadata contains no credential or source filename. */
    public record Asset(UUID fileId, String mediaType, long size, String sha256) {}
}
