package dev.oskar_lab.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Exports the real HTTP contract using an isolated in-memory database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"springdoc.api-docs.enabled=true", "springdoc.api-docs.version=OPENAPI_3_0"})
class OpenApiTests {
    @Value("${local.server.port}") int port;

    @Test
    void exportsBackendContract() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("/internal/workout/plans"));
        assertTrue(response.body().contains("/internal/auth/{action}"));
        assertTrue(response.body().contains("PageAppEntity"));
        var contract = new ObjectMapper().readTree(response.body());
        var paths = contract.path("paths");
        for (var path : new String[]{"/apps", "/apps/{id}", "/internal/workout/exercises", "/internal/workout/templates",
                "/internal/workout/plans", "/internal/workout/plans/{id}", "/internal/workout/logs", "/internal/workout/history/{exerciseId}"}) {
            assertTrue(paths.has(path), "Missing endpoint: " + path);
        }
        assertTrue(paths.path("/internal/workout/plans").path("post").path("responses").has("201"));
        assertTrue(paths.path("/internal/workout/plans").path("get").path("security").get(0).has("bridge"));
        assertTrue(paths.has("/internal/travel/trips"));
        assertTrue(paths.has("/internal/travel/trips/{tripId}/entries/{entryId}/attachments/{attachmentId}"));
        assertTrue(paths.path("/internal/travel/trips").path("get").path("security").get(0).has("bridge"));
        assertTrue(contract.path("components").path("schemas").path("TravelEntry").path("required").toString().contains("points"));
        assertTrue(contract.path("components").path("schemas").path("Entry").path("properties").path("targetMin").path("nullable").asBoolean());
        var denied = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/internal/workout/templates")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, denied.statusCode(), "Documentation must not bypass bridge authorization");
        Files.createDirectories(Path.of("build/openapi"));
        Files.writeString(Path.of("build/openapi/backend.json"), response.body());
    }
}
