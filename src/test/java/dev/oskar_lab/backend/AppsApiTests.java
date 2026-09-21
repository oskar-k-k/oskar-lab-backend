package dev.oskar_lab.backend;

import dev.oskar_lab.backend.apps.catalog.AppEntity;
import dev.oskar_lab.backend.apps.catalog.AppRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppsApiTests {
    @Value("${local.server.port}") int port;
    @Autowired AppRepository repository;

    @Test
    void servesPersistedAppsThroughNewAndLegacyEndpoints() throws Exception {
        var app = new AppEntity();
        app.setPath("catalog-test");
        app.setTitle("Catalog test");
        var saved = repository.save(app);
        try {
            for (var endpoint : new String[]{"/apps", "/projects"}) {
                var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"page\":0,\"pageSize\":50}"))
                        .build();
                var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("catalog-test"));
            }
        } finally {
            repository.deleteById(saved.getId());
        }
    }
}
