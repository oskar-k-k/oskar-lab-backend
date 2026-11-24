package dev.oskar_lab.backend;

import com.sun.net.httpserver.HttpServer;
import dev.oskar_lab.backend.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OrbitStorageClientTests {
    @TempDir Path directory;
    private HttpServer server;
    private OrbitStorageClient client;
    private final UUID project = UUID.randomUUID();
    private final UUID file = UUID.randomUUID();
    private final byte[] bytes = "synthetic private bytes".getBytes(StandardCharsets.UTF_8);
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status;
    private volatile boolean wrongProject;

    @BeforeEach void setup() throws Exception {
        status = 200; wrongProject = false;
        var credentials = directory.resolve("projects.json");
        Files.writeString(credentials, "{\"thailand\":{\"projectId\":\"" + project + "\",\"key\":\"synthetic-project-key-at-least-20-characters\"}}");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/files", exchange -> {
            requests.incrementAndGet();
            assertEquals("Bearer synthetic-project-key-at-least-20-characters", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = bytes;
            int code = status;
            if (exchange.getRequestMethod().equals("POST")) {
                assertEquals(String.valueOf(bytes.length), exchange.getRequestHeaders().getFirst("Content-Length"));
                assertEquals("example.pdf", exchange.getRequestHeaders().getFirst("X-Filename"));
                assertArrayEquals(bytes, exchange.getRequestBody().readAllBytes());
                response = ("{\"id\":\"" + file + "\",\"projectId\":\"" + (wrongProject ? UUID.randomUUID() : project) + "\",\"actualSize\":" + bytes.length + ",\"contentType\":\"application/pdf\",\"status\":\"READY\"}").getBytes(StandardCharsets.UTF_8);
                if (status == 200) code = 201;
            }
            exchange.sendResponseHeaders(code, code == 204 ? -1 : response.length);
            if (code != 204) exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new OrbitStorageClient("http://127.0.0.1:" + server.getAddress().getPort(), credentials.toString());
    }
    @AfterEach void shutdown() { server.stop(0); }

    @Test void streamsKnownLengthUploadsToTheConfiguredProjectAndChecksDownloads() {
        var uploaded = client.upload("thailand", "example.pdf", "application/pdf", bytes);
        assertEquals(file, uploaded.id());
        assertArrayEquals(bytes, client.download("thailand", file, bytes.length, OrbitStorageClient.hash(bytes)));
    }
    @Test void rejectsCrossProjectResponsesAndUnknownProjectConfiguration() {
        wrongProject = true;
        assertEquals("storageInvalidResponse", assertThrows(StorageFailure.class, () -> client.upload("thailand", "example.pdf", "application/pdf", bytes)).getMessage());
        int before = requests.get();
        assertThrows(StorageFailure.class, () -> client.download("other-app", file, bytes.length, OrbitStorageClient.hash(bytes)));
        assertEquals(before, requests.get());
    }
    @Test void rejectsCorruptionAndTruncationInsteadOfReturningSuccessfulFiles() {
        assertEquals("storageIntegrityFailed", assertThrows(StorageFailure.class,
            () -> client.download("thailand", file, bytes.length, "0".repeat(64))).getMessage());
        assertThrows(StorageFailure.class, () -> client.download("thailand", file, bytes.length + 1, OrbitStorageClient.hash(bytes)));
        assertThrows(StorageFailure.class, () -> client.download("thailand", file, bytes.length - 1, OrbitStorageClient.hash(bytes)));
    }
    @Test void preservesQuotaAndRateFailuresWithoutExposingUpstreamBodiesOrRetryingUploads() {
        status = 429;
        var error = assertThrows(StorageFailure.class, () -> client.upload("thailand", "example.pdf", "application/pdf", bytes));
        assertEquals(429, error.status); assertEquals("storageRateLimited", error.getMessage());
        assertEquals(1, requests.get());
        status = 409;
        assertEquals(409, assertThrows(StorageFailure.class, () -> client.upload("thailand", "example.pdf", "application/pdf", bytes)).status);
    }
    @Test void neverFollowsRedirectsAndRetriesAnAlreadyDeletedObjectSafely() {
        status = 302;
        assertEquals(503, assertThrows(StorageFailure.class, () -> client.download("thailand", file, bytes.length, OrbitStorageClient.hash(bytes))).status);
        status = 404;
        assertDoesNotThrow(() -> client.delete("thailand", file));
    }
}
