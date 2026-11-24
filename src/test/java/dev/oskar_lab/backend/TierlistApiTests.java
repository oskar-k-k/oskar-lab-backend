package dev.oskar_lab.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TierlistApiTests {
    @Value("${local.server.port}") int port;
    final ObjectMapper json = new ObjectMapper();
    HttpResponse<String> call(String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/tierlist/rooms" + path))
            .header("Content-Type", "application/json");
        if (token != null) request.header("X-Tierlist-Token", token);
        request.method(body == null ? "GET" : "POST", body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void roomEndpointsKeepCredentialsAndUnrevealedAnswersPrivate() throws Exception {
        var created = call("", "{\"name\":\"Host\",\"category\":\"food\",\"rows\":[\"S\",\"F\"],\"rounds\":3}", null);
        assertEquals(200, created.statusCode());
        var credentials = json.readTree(created.body()); String code = credentials.get("code").asText(), token = credentials.get("token").asText();
        assertEquals(401, call("/" + code, null, "wrong").statusCode());
        assertEquals(401, call("/" + code, null, null).statusCode());
        assertEquals(400, call("", "not-json", null).statusCode());
        var joined = json.readTree(call("/" + code + "/join", "{\"name\":\"Guest\"}", null).body());
        assertEquals(200, call("/" + code + "/actions", "{\"action\":\"start\"}", token).statusCode());
        assertEquals(200, call("/" + code + "/actions", "{\"action\":\"vote\",\"round\":0,\"row\":1}", token).statusCode());
        var view = call("/" + code, null, joined.get("token").asText());
        assertEquals("no-store", view.headers().firstValue("cache-control").orElse(""));
        assertFalse(view.body().contains(token));
        assertEquals(0, json.readTree(view.body()).get("players").get(0).get("answers").size());
        assertEquals(400, call("/" + code + "/join", "{\"name\":\"Late\"}", null).statusCode());
    }
}
