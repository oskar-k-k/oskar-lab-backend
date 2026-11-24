package dev.oskar_lab.backend;

import dev.oskar_lab.backend.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AssetManifestTests {
    @TempDir Path directory;
    @Test void separatesPublicPathsFromPrivateAssetsAndRejectsArbitraryIds() throws Exception {
        var path = directory.resolve("assets.json");
        String record = "{\"fileId\":\"" + UUID.randomUUID() + "\",\"sha256\":\"" + "a".repeat(64) + "\",\"size\":12,\"mediaType\":\"image/png\"}";
        Files.writeString(path, "{\"publicAssets\":{\"oskar-lab:/catalog/example.png\":" + record + "},\"privateAssets\":{\"thailand:/ticket.pdf\":" + record + "}}");
        var manifest = new AssetManifest(path.toString());
        assertEquals(12, manifest.get("oskar-lab", "/catalog/example.png", false).size());
        assertEquals(404, assertThrows(StorageFailure.class, () -> manifest.get("thailand", "/ticket.pdf", false)).status);
        assertEquals(404, assertThrows(StorageFailure.class, () -> manifest.get("oskar-lab", "/../ticket.pdf", false)).status);
        assertEquals(404, assertThrows(StorageFailure.class, () -> manifest.get("oskar-lab", "/" + UUID.randomUUID(), false)).status);
        assertEquals(12, manifest.get("thailand", "/ticket.pdf", true).size());
    }
}
