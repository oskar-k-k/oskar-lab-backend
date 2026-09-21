package dev.oskar_lab.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.bridge-secret=synthetic-test-bridge-secret-at-least-32-characters")
class AuthBoundaryTests {
    @Value("${local.server.port}") int port;

    private HttpResponse<String> request(String key, String action, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/auth/" + action))
                .header("Content-Type", "application/json").header("X-Auth-Bridge", key)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void rejectsPublicProviderImpersonationAndMissingSessions() throws Exception {
        assertEquals(403, request("", "google", "{\"subject\":\"forged\",\"email\":\"victim@example.com\",\"emailVerified\":true}").statusCode());
        assertEquals(401, request("synthetic-test-bridge-secret-at-least-32-characters", "current", "{}").statusCode());
    }

    @Test void limitsRepeatedLoginAttemptsWithoutExposingCredentials() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            var response = request("synthetic-test-bridge-secret-at-least-32-characters", "login", "{\"identifier\":\"rate-limit-test\",\"password\":\"synthetic-password\"}");
            assertEquals(401, response.statusCode());
            assertFalse(response.body().contains("synthetic-password"));
        }
        assertEquals(429, request("synthetic-test-bridge-secret-at-least-32-characters", "login", "{\"identifier\":\"rate-limit-test\",\"password\":\"synthetic-password\"}").statusCode());
    }
}
