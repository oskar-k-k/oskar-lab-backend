package dev.oskar_lab.backend.storage;

import dev.oskar_lab.backend.auth.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Serves migrated static assets; private legacy files require both the server bridge and their account ACL. */
@RestController
public class AssetController {
    private final AssetManifest manifest;
    private final FileStorageService files;
    private final AccountRepository accounts;
    private final String bridge;
    private static final Set<String> LEGACY_EMAIL_HASHES = Set.of(
        "d431115189e62eef918543f6dbe89fbb71ddb606f367a5cc2e585fc31b6f7be8",
        "f61b797003b620af569e7fe9e74192ab5ff78bbf212df55c9c8d130d1df0e32c");

    public AssetController(AssetManifest manifest, FileStorageService files, AccountRepository accounts, @Value("${auth.bridge-secret:}") String bridge) {
        this.manifest = manifest; this.files = files; this.accounts = accounts; this.bridge = bridge;
    }

    /** Public access is limited to reviewed public paths, never arbitrary UUIDs or private documents. */
    @GetMapping("/public-assets/{project}/{*path}")
    public ResponseEntity<byte[]> publicAsset(@PathVariable String project, @PathVariable String path) {
        var asset = manifest.get(project, path, false);
        return response(project, asset).header("Cache-Control", "public, max-age=300")
            .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
            .body(files.download(project, asset.fileId(), asset.size(), asset.sha256()));
    }

    /** Keeps the original Thailand Google-account allowlist until its data are imported into owned trips. */
    @GetMapping("/internal/storage/legacy/thailand/{*path}")
    public ResponseEntity<byte[]> privateThailand(@PathVariable String path, @RequestHeader("X-Auth-Bridge") String key,
                                                 @RequestHeader("X-User-Id") UUID user) {
        authorize(key);
        var account = accounts.findById(user).orElseThrow(() -> new StorageFailure(404, "notFound"));
        if (account.username == null || account.termsAcceptedAt == null || account.verifiedEmail == null ||
            !account.verifiedEmail.equals(account.email) || !LEGACY_EMAIL_HASHES.contains(OrbitStorageClient.hash(account.verifiedEmail.getBytes(StandardCharsets.UTF_8))))
            throw new StorageFailure(404, "notFound");
        var asset = manifest.get("thailand", path, true);
        return response("thailand", asset).header("Cache-Control", "private, no-store").header("X-Robots-Tag", "noindex, noarchive, nosnippet")
            .header("Content-Security-Policy", "sandbox").body(files.download("thailand", asset.fileId(), asset.size(), asset.sha256()));
    }

    /** Reports index presence without returning any private content; server-to-server only. */
    @GetMapping("/internal/storage/legacy/thailand-ready")
    public ResponseEntity<Map<String, Boolean>> ready(@RequestHeader("X-Auth-Bridge") String key) {
        authorize(key);
        try { manifest.get("thailand", "/trip.json", true); return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("ready", true)); }
        catch (StorageFailure error) { return ResponseEntity.status(error.status == 404 ? 503 : error.status).cacheControl(CacheControl.noStore()).body(Map.of("ready", false)); }
    }

    private ResponseEntity.BodyBuilder response(String project, AssetManifest.Asset asset) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.mediaType())).header("X-Content-Type-Options", "nosniff");
    }
    private void authorize(String key) {
        if (bridge.length() < 32 || !MessageDigest.isEqual(bridge.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) throw new StorageFailure(403, "forbidden");
    }

    @ExceptionHandler({org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().header("Cache-Control", "private, no-store").body(Map.of("code", "invalidFileRequest"));
    }
}
