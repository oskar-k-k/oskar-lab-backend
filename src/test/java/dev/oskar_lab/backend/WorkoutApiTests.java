package dev.oskar_lab.backend;

import dev.oskar_lab.backend.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.bridge-secret=workout-test-bridge-secret-at-least-32-characters")
@Sql(statements = {"DELETE FROM workout_log_sets", "DELETE FROM workout_logs", "DELETE FROM workout_plan_exercises", "DELETE FROM workout_plans", "DELETE FROM workout_exercises"})
@Sql(scripts = "/db/migration/V5__seed_workout_sessions.sql")
class WorkoutApiTests {
    @Value("${local.server.port}") int port;
    @Autowired AccountRepository accounts;
    private final ObjectMapper json = new ObjectMapper();
    private UUID owner;

    @BeforeEach void account() {
        Account account = new Account();
        account.email = UUID.randomUUID() + "@example.com";
        account.username = "workout";
        account.usernameKey = UUID.randomUUID().toString().substring(0, 30);
        // Unique username is required by the entity schema.
        account.username = account.usernameKey;
        account.termsAcceptedAt = Instant.now();
        owner = accounts.saveAndFlush(account).id;
    }

    private HttpResponse<String> call(String method, String path, String body, UUID user, boolean bridge) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/workout/" + path))
                .header("Content-Type", "application/json");
        if (bridge) builder.header("X-Auth-Bridge", "workout-test-bridge-secret-at-least-32-characters");
        if (user != null) builder.header("X-User-Id", user.toString());
        return HttpClient.newHttpClient().send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private tools.jackson.databind.node.ObjectNode draft() throws Exception {
        var result = call("GET", "templates", null, null, true);
        assertEquals(200, result.statusCode(), result.body());
        var plan = (tools.jackson.databind.node.ObjectNode) json.readTree(result.body()).get(0);
        plan.put("id", UUID.randomUUID().toString());
        plan.remove("template");
        return plan;
    }

    @Test void seedsExactSourceRangesAndSupersets() throws Exception {
        var templates = json.readTree(call("GET", "templates", null, null, true).body());
        assertEquals(3, templates.size());
        for (var plan : templates) assertEquals(11, plan.get("exercises").size());
        assertEquals(27, json.readTree(call("GET", "exercises", null, null, true).body()).size());
        var a = templates.get(0).get("exercises");
        assertEquals(600, a.get(0).get("targetMin").asInt());
        assertEquals(20, a.get(1).get("targetMax").asInt());
        assertEquals("unspecified", a.get(3).get("mode").asText());
        assertTrue(a.get(3).get("targetMin").isNull());
        assertEquals(a.get(6).get("superset"), a.get(7).get("superset"));
        assertEquals(2, a.get(10).get("setsMin").asInt());
        assertEquals(3, a.get(10).get("setsMax").asInt());
        var c = templates.get(2).get("exercises");
        assertEquals(4, c.get(2).get("setsMin").asInt());
        assertEquals(5, c.get(2).get("setsMax").asInt());
        assertEquals(3, c.get(4).get("setsMin").asInt());
        assertEquals(3, c.get(5).get("setsMin").asInt());
    }

    @Test void persistsEditsAndRejectsStaleAndCrossAccountAccess() throws Exception {
        var draft = draft();
        String id = draft.get("id").asText();
        var created = call("POST", "plans", draft.toString(), owner, true);
        assertEquals(201, created.statusCode(), created.body());
        assertEquals(201, call("POST", "plans", draft.toString(), owner, true).statusCode());
        var list = json.readTree(call("GET", "plans", null, owner, true).body());
        assertEquals(1, list.get("plans").size());
        var first = (tools.jackson.databind.node.ObjectNode) draft.get("exercises").get(0);
        first.put("notes", "updated child only");
        var edited = call("PUT", "plans/" + id, draft.toString(), owner, true);
        assertEquals(200, edited.statusCode(), edited.body());
        assertTrue(json.readTree(edited.body()).get("version").asLong() > 0);
        assertEquals(409, call("PUT", "plans/" + id, draft.toString(), owner, true).statusCode());
        var loaded = json.readTree(call("GET", "plans/" + id, null, owner, true).body());
        assertEquals("updated child only", loaded.get("exercises").get(0).get("notes").asText());
        account();
        assertEquals(404, call("GET", "plans/" + id, null, owner, true).statusCode());
        assertEquals(404, call("PUT", "plans/" + id, draft.toString(), owner, true).statusCode());
        assertEquals(0, json.readTree(call("GET", "plans", null, owner, true).body()).get("plans").size());
    }

    @Test void rejectsInvalidPrescriptionsAndUntrustedCallers() throws Exception {
        assertEquals(403, call("GET", "templates", null, null, false).statusCode());
        assertEquals(400, call("GET", "plans", null, null, true).statusCode());
        assertEquals(401, call("GET", "plans", null, UUID.randomUUID(), true).statusCode());
        assertEquals(400, call("POST", "plans", "{", owner, true).statusCode());
        var draft = draft();
        var first = (tools.jackson.databind.node.ObjectNode) draft.get("exercises").get(0);
        first.put("setsMax", 0);
        assertEquals(400, call("POST", "plans", draft.toString(), owner, true).statusCode());
        first.put("setsMax", 1); first.put("restMin", 90);
        assertEquals(400, call("POST", "plans", draft.toString(), owner, true).statusCode());
        first.putNull("restMin"); first.put("exerciseId", UUID.randomUUID().toString());
        assertEquals(400, call("POST", "plans", draft.toString(), owner, true).statusCode());
        assertEquals(0, json.readTree(call("GET", "plans", null, owner, true).body()).get("plans").size());
        var template = json.readTree(call("GET", "templates", null, null, true).body()).get(0);
        assertEquals(404, call("PUT", "plans/" + template.get("id").asText(), template.toString(), owner, true).statusCode());
    }
    private tools.jackson.databind.node.ObjectNode log(String type) throws Exception {
        var plan = draft();
        var first = (tools.jackson.databind.node.ObjectNode) plan.get("exercises").get(0);
        first.put("trackingMode", type);
        assertEquals(201, call("POST", "plans", plan.toString(), owner, true).statusCode());
        var input = json.createObjectNode();
        input.put("id", UUID.randomUUID().toString()); input.put("planId", plan.get("id").asText());
        input.put("planVersion", 0); input.put("position", 0); input.put("exerciseId", first.get("exerciseId").asText());
        input.put("trackingMode", type);
        var set = input.putArray("sets").addObject(); set.put("setNumber", 1); set.put("value", 12);
        if (type.equals("weighted")) set.put("weight", 12.5); else set.putNull("weight");
        return input;
    }

    @Test void tracksAllUnitsWithSafeRetriesAndPrivateHistory() throws Exception {
        for (String type : List.of("seconds", "reps", "weighted")) {
            var input = log(type);
            String history = "history/" + input.get("exerciseId").asText();
            var response = call("POST", "logs", input.toString(), owner, true);
            assertEquals(201, response.statusCode(), response.body());
            assertEquals(type, json.readTree(response.body()).get("trackingMode").asText());
            assertNotNull(Instant.parse(json.readTree(response.body()).get("recordedAt").asText()));
            assertEquals(response.body(), call("POST", "logs", input.toString(), owner, true).body());
            ((tools.jackson.databind.node.ObjectNode) input.get("sets").get(0)).put("value", 13);
            assertEquals(409, call("POST", "logs", input.toString(), owner, true).statusCode());
            assertTrue(json.readTree(call("GET", history, null, owner, true).body()).get("entries").size() > 0);
            UUID originalOwner = owner; account();
            assertEquals(0, json.readTree(call("GET", history, null, owner, true).body()).get("entries").size());
            input.put("id", UUID.randomUUID().toString());
            assertEquals(404, call("POST", "logs", input.toString(), owner, true).statusCode());
            owner = originalOwner;
        }
    }
    @Test void rejectsIncompleteWeightsInvalidSetsAndStalePlans() throws Exception {
        var input = log("weighted");
        var set = (tools.jackson.databind.node.ObjectNode) input.get("sets").get(0);
        for (String invalid : List.of("null", "-1", "1.123")) {
            set.set("weight", json.readTree(invalid));
            assertEquals(400, call("POST", "logs", input.toString(), owner, true).statusCode());
        }
        set.put("weight", 0); set.put("value", -1);
        assertEquals(400, call("POST", "logs", input.toString(), owner, true).statusCode());
        set.put("value", 0); input.put("planVersion", 5);
        assertEquals(409, call("POST", "logs", input.toString(), owner, true).statusCode());
        input.put("planVersion", 0); input.put("trackingMode", "seconds");
        assertEquals(409, call("POST", "logs", input.toString(), owner, true).statusCode());
        input.put("trackingMode", "weighted");
        ((tools.jackson.databind.node.ArrayNode) input.get("sets")).add(set.deepCopy());
        assertEquals(400, call("POST", "logs", input.toString(), owner, true).statusCode());
        assertEquals(403, call("POST", "logs", input.toString(), owner, false).statusCode());
        assertEquals(401, call("GET", "history/" + input.get("exerciseId").asText(), null, UUID.randomUUID(), true).statusCode());
    }
    @Test void retainsHistoryAfterPlanEditsAndPaginates() throws Exception {
        var input = log("seconds");
        for (int i = 0; i < 21; i++) {
            input.put("id", UUID.randomUUID().toString());
            assertEquals(201, call("POST", "logs", input.toString(), owner, true).statusCode());
        }
        String path = "plans/" + input.get("planId").asText();
        var plan = (tools.jackson.databind.node.ObjectNode) json.readTree(call("GET", path, null, owner, true).body());
        ((tools.jackson.databind.node.ObjectNode) plan.get("exercises").get(0)).put("trackingMode", "weighted");
        assertEquals(200, call("PUT", path, plan.toString(), owner, true).statusCode());
        String history = "history/" + input.get("exerciseId").asText();
        var first = json.readTree(call("GET", history, null, owner, true).body());
        assertEquals(20, first.get("entries").size()); assertTrue(first.get("hasMore").asBoolean());
        assertEquals("seconds", first.get("entries").get(0).get("trackingMode").asText());
        assertEquals(1, json.readTree(call("GET", history + "?page=1", null, owner, true).body()).get("entries").size());
        assertEquals(201, call("POST", "logs", input.toString(), owner, true).statusCode());
    }

}
