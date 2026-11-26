package dev.oskar_lab.backend;

import dev.oskar_lab.backend.auth.*;
import dev.oskar_lab.backend.storage.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"auth.bridge-secret=synthetic-travel-test-bridge-at-least-32-characters",
        "spring.servlet.multipart.max-file-size=5MB", "spring.servlet.multipart.max-request-size=6MB", "server.tomcat.max-swallow-size=6MB"})
class TravelApiTests {
    private static final String BRIDGE = "synthetic-travel-test-bridge-at-least-32-characters";
    @Value("${local.server.port}") int port;
    @MockitoBean OrbitStorageClient orbit;
    private final Map<UUID, byte[]> cloudFiles = new java.util.concurrent.ConcurrentHashMap<>();
    private void configureCloud() {
        when(orbit.upload(anyString(), anyString(), anyString(), any(byte[].class))).thenAnswer(call -> {
            UUID id = UUID.randomUUID(); byte[] bytes = call.getArgument(3); cloudFiles.put(id, bytes.clone());
            return new OrbitStorageClient.StoredFile(id, call.getArgument(2), bytes.length);
        });
        when(orbit.download(anyString(), any(UUID.class), anyLong(), anyString())).thenAnswer(call -> cloudFiles.get(call.getArgument(1)));
    }
    @Autowired AccountRepository accounts;
    @Autowired AccountService accountService;
    private final ObjectMapper json = new ObjectMapper();
    private UUID owner;

    @BeforeEach void setup() { configureCloud(); owner = account(false).id; }

    private Account account(boolean verified) {
        Account account = new Account();
        account.username = UUID.randomUUID().toString().substring(0, 30);
        account.usernameKey = account.username;
        account.email = account.username + "@example.invalid";
        account.termsAcceptedAt = Instant.now();
        if (verified) account.verifiedEmail = account.email;
        return accounts.saveAndFlush(account);
    }

    private HttpResponse<String> call(String method, String path, String body, UUID user) throws Exception {
        return call(method, path, body, user, true);
    }

    private HttpResponse<String> call(String method, String path, String body, UUID user, boolean trusted) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/travel/trips" + path))
            .header("Content-Type", "application/json");
        if (trusted) builder.header("X-Auth-Bridge", BRIDGE);
        if (user != null) builder.header("X-User-Id", user.toString());
        return HttpClient.newHttpClient().send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private ObjectNode tripInput() {
        var input = json.createObjectNode();
        input.put("title", "Synthetic journey"); input.put("startDate", "2030-12-01"); input.put("endDate", "2030-12-10"); input.put("version", 0);
        return input;
    }

    private String createTrip() throws Exception {
        var response = call("POST", "", tripInput().toString(), owner);
        assertEquals(201, response.statusCode(), response.body());
        return "/" + json.readTree(response.body()).get("id").asText();
    }

    private ObjectNode entryInput(String type) {
        var input = json.createObjectNode(); input.put("type", type); input.put("title", "Synthetic booking"); input.put("version", 0);
        var points = input.putArray("points");
        String[] roles = switch(type) {
            case "FLIGHT", "BOAT", "TRAIN", "BUS", "TRANSFER" -> new String[]{"DEPARTURE", "ARRIVAL"};
            case "HOTEL", "APARTMENT" -> new String[]{"CHECK_IN", "CHECK_OUT"};
            default -> new String[]{"START"};
        };
        for (int i = 0; i < roles.length; i++) {
            var point = points.addObject(); point.put("role", roles[i]); point.put("date", i == 0 ? "2030-12-02" : "2030-12-05");
            point.put("time", "10:00:00"); point.put("timeZone", "Asia/Bangkok"); point.put("timeMeaning", "EXACT");
            point.put("placeName", "Synthetic place"); point.put("address", "Synthetic address");
        }
        return input;
    }

    private String createEntry(String trip, String type) throws Exception {
        var response = call("POST", trip + "/entries", entryInput(type).toString(), owner);
        assertEquals(201, response.statusCode(), response.body());
        return trip + "/entries/" + json.readTree(response.body()).get("id").asText();
    }

    private String invite(String trip, String email) throws Exception {
        var input = json.createObjectNode().put("email", email);
        var response = call("POST", trip + "/viewers", input.toString(), owner);
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body()).get("id").asText();
    }

    @Test void isolatesTripsAndRejectsMissingOrForgedCallers() throws Exception {
        var trip = createTrip();
        UUID other = account(false).id;
        assertEquals(404, call("GET", trip, null, other).statusCode());
        assertEquals(0, json.readTree(call("GET", "", null, other).body()).get("trips").size());
        assertEquals(403, call("GET", trip, null, owner, false).statusCode());
        assertEquals(400, call("GET", trip, null, null).statusCode());
        assertEquals(401, call("GET", trip, null, UUID.randomUUID()).statusCode());
        assertEquals(400, call("GET", "/not-a-uuid", null, owner).statusCode());
        assertEquals(400, call("POST", "", "{", owner).statusCode());
        assertEquals(400, call("GET", "?page=-1", null, owner).statusCode());
        var loaded = call("GET", trip, null, owner);
        assertEquals("private, no-store", loaded.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(owner.toString(), json.readTree(loaded.body()).get("ownerId").asText());
    }

    @Test void sharesReadOnlyByVerifiedEmailAndRevokesEveryRead() throws Exception {
        var trip = createTrip();
        var entry = createEntry(trip, "FLIGHT");
        Account viewer = account(true);
        String grant = invite(trip, "  " + viewer.email.toUpperCase(Locale.ROOT) + "  ");
        assertEquals(409, call("POST", trip + "/viewers", json.createObjectNode().put("email", viewer.email).toString(), owner).statusCode());
        assertEquals(200, call("GET", entry, null, viewer.id).statusCode());
        assertFalse(json.readTree(call("GET", trip, null, viewer.id).body()).get("canEdit").asBoolean());
        assertEquals(1, json.readTree(call("GET", "", null, viewer.id).body()).get("trips").size());
        assertEquals(403, call("PUT", trip, tripInput().toString(), viewer.id).statusCode());
        assertEquals(403, call("POST", trip + "/entries", entryInput("ACTIVITY").toString(), viewer.id).statusCode());
        assertEquals(403, call("GET", trip + "/viewers", null, viewer.id).statusCode());
        assertEquals(403, call("DELETE", trip + "/viewers/" + grant, null, viewer.id).statusCode());
        assertEquals(403, call("DELETE", entry + "?version=0", null, viewer.id).statusCode());
        assertEquals(204, call("DELETE", trip + "/viewers/" + grant, null, owner).statusCode());
        assertEquals(404, call("GET", entry, null, viewer.id).statusCode());
        assertEquals(0, json.readTree(call("GET", "", null, viewer.id).body()).get("trips").size());
    }

    @Test void cannotClaimAnInvitationByRegisteringAnUnverifiedAddress() throws Exception {
        var trip = createTrip();
        Account unverified = account(false);
        invite(trip, unverified.email);
        assertEquals(404, call("GET", trip, null, unverified.id).statusCode());
        assertEquals(0, json.readTree(call("GET", "", null, unverified.id).body()).get("trips").size());
    }

    @Test void acceptsInvitationsCreatedBeforeTheAccountExistsAfterVerifiedOnboarding() throws Exception {
        var trip = createTrip();
        String address = UUID.randomUUID() + "@example.invalid";
        invite(trip, address);
        var login = accountService.google(Map.of("subject", UUID.randomUUID().toString(), "email", address, "emailVerified", true));
        var identity = accountService.complete((String) login.get("token"), Map.of("username", "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
            "acceptTerms", true, "termsVersion", AccountService.TERMS_VERSION));
        assertEquals(200, call("GET", trip, null, UUID.fromString((String)identity.get("id"))).statusCode());
    }

    @Test void rejectsStaleTripAndPointOnlyEditsAndKeepsOtherActivitiesIndependent() throws Exception {
        var trip = createTrip();
        var input = tripInput(); input.put("title", "Updated journey");
        assertEquals(200, call("PUT", trip, input.toString(), owner).statusCode());
        assertEquals(409, call("PUT", trip, input.toString(), owner).statusCode());
        assertEquals(409, call("DELETE", trip + "?version=0", null, owner).statusCode());
        var hotel = createEntry(trip, "HOTEL");
        var activity = entryInput("ACTIVITY"); ((ObjectNode)activity.get("points").get(0)).put("date", "2030-12-03");
        assertEquals(201, call("POST", trip + "/entries", activity.toString(), owner).statusCode());
        var hotelInput = entryInput("HOTEL"); ((ObjectNode)hotelInput.get("points").get(0)).put("time", "15:00:00");
        assertEquals(200, call("PUT", hotel, hotelInput.toString(), owner).statusCode());
        assertEquals(409, call("PUT", hotel, hotelInput.toString(), owner).statusCode());
        var loaded = json.readTree(call("GET", hotel, null, owner).body());
        assertEquals("15:00:00", loaded.get("points").get(0).get("time").asText());
        assertEquals(2, json.readTree(call("GET", trip + "/entries", null, owner).body()).get("entries").size());
        var otherTrip = createTrip();
        assertEquals(404, call("GET", otherTrip + "/entries/" + loaded.get("id").asText(), null, owner).statusCode());
    }

    @Test void validatesEveryBookingKindAndNestedInputs() throws Exception {
        var trip = createTrip();
        for (var type : List.of("FLIGHT", "BOAT", "TRAIN", "BUS", "TRANSFER", "HOTEL", "APARTMENT", "ACTIVITY", "RESTAURANT", "TOUR")) createEntry(trip, type);
        var invalid = entryInput("FLIGHT"); invalid.withArray("points").remove(1);
        assertEquals(400, call("POST", trip + "/entries", invalid.toString(), owner).statusCode());
        invalid = entryInput("FLIGHT"); invalid.withArray("points").set(1, json.nullNode());
        assertEquals(400, call("POST", trip + "/entries", invalid.toString(), owner).statusCode());
        invalid = entryInput("ACTIVITY"); ((ObjectNode)invalid.get("points").get(0)).put("timeZone", "not-a-zone");
        assertEquals(400, call("POST", trip + "/entries", invalid.toString(), owner).statusCode());
        var dates = tripInput(); dates.put("endDate", "2029-01-01");
        assertEquals(400, call("POST", "", dates.toString(), owner).statusCode());
        assertEquals(400, call("POST", trip + "/viewers", "{\"email\":\"invalid\"}", owner).statusCode());
    }

    @Test void paginatesWithoutExposingOtherTrips() throws Exception {
        for (int i = 0; i < 21; i++) createTrip();
        var first = json.readTree(call("GET", "", null, owner).body());
        assertEquals(20, first.get("trips").size()); assertTrue(first.get("hasMore").asBoolean());
        assertEquals(1, json.readTree(call("GET", "?page=1", null, owner).body()).get("trips").size());
        assertEquals(0, json.readTree(call("GET", "?page=1", null, account(false).id).body()).get("trips").size());
    }

    private HttpResponse<String> upload(String entry, UUID user, String filename, String type, byte[] bytes, String query) throws Exception {
        String boundary = "synthetic-travel-boundary";
        byte[] header = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        var body = new java.io.ByteArrayOutputStream(); body.write(header); body.write(bytes); body.write(footer);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/travel/trips" + entry + "/attachments" + query))
            .header("X-Auth-Bridge", BRIDGE).header("X-User-Id", user.toString()).header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test void protectsUploadsDownloadsAndDeletionAndRemovesAllTripData() throws Exception {
        var trip = createTrip(); var entry = createEntry(trip, "FLIGHT");
        byte[] pdf = "%PDF-1.7 synthetic fixture".getBytes(StandardCharsets.UTF_8);
        var created = upload(entry, owner, "ticket.pdf", "application/pdf", pdf, "?pointRole=DEPARTURE");
        assertEquals(201, created.statusCode(), created.body());
        var attachment = entry + "/attachments/" + json.readTree(created.body()).get("id").asText();
        Account viewer = account(true); String grant = invite(trip, viewer.email);
        var response = call("GET", attachment, null, viewer.id);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(new String(pdf, StandardCharsets.UTF_8), response.body());
        assertTrue(response.headers().firstValue("Content-Disposition").orElseThrow().startsWith("attachment;"));
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals("sandbox", response.headers().firstValue("Content-Security-Policy").orElseThrow());
        assertEquals(403, upload(entry, viewer.id, "ticket.pdf", "application/pdf", pdf, "").statusCode());
        assertEquals(403, call("DELETE", attachment, null, viewer.id).statusCode());
        assertEquals(404, call("GET", attachment, null, account(false).id).statusCode());
        call("DELETE", trip + "/viewers/" + grant, null, owner);
        assertEquals(404, call("GET", attachment, null, viewer.id).statusCode());
        assertEquals(204, call("DELETE", trip + "?version=0", null, owner).statusCode());
        assertEquals(404, call("GET", attachment, null, owner).statusCode());
        assertEquals(404, call("GET", trip, null, owner).statusCode());
    }

    @Test void rejectsUnsafeFilesAndCrossEntryDocumentIds() throws Exception {
        var trip = createTrip(); var entry = createEntry(trip, "FLIGHT");
        byte[] pdf = "%PDF-1.7 synthetic fixture".getBytes(StandardCharsets.UTF_8);
        assertEquals(415, upload(entry, owner, "fake.pdf", "application/pdf", "<script>no</script>".getBytes(StandardCharsets.UTF_8), "").statusCode());
        assertEquals(415, upload(entry, owner, "file.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8), "").statusCode());
        assertEquals(400, upload(entry, owner, "../ticket.pdf", "application/pdf", pdf, "").statusCode());
        assertEquals(400, upload(entry, owner, "ticket.pdf", "application/pdf", pdf, "?pointRole=CHECK_IN").statusCode());
        assertEquals(413, upload(entry, owner, "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1], "").statusCode());
        var uploaded = upload(entry, owner, "ticket.pdf", "application/pdf", pdf, "");
        String id = json.readTree(uploaded.body()).get("id").asText();
        var other = createEntry(trip, "ACTIVITY");
        assertEquals(404, call("GET", other + "/attachments/" + id, null, owner).statusCode());
        assertEquals(204, call("DELETE", entry + "/attachments/" + id, null, owner).statusCode());
        assertEquals(0, json.readTree(call("GET", entry + "/attachments", null, owner).body()).size());
    }
    @Test void storesPrivateBookingSourcesAndPreservesThemForOlderClients() throws Exception {
        String trip = createTrip();
        ObjectNode input = entryInput("HOTEL");
        input.put("bookingSource", " Booking.com ");
        var created = call("POST", trip + "/entries", input.toString(), owner);
        assertEquals(201, created.statusCode());
        var body = json.readTree(created.body());
        String entry = trip + "/entries/" + body.get("id").asText();
        assertEquals("Booking.com", body.get("bookingSource").asText());
        assertEquals(404, call("GET", entry, null, account(false).id).statusCode());
        input.remove("bookingSource");
        input.put("version", body.get("version").asLong());
        var updated = call("PUT", entry, input.toString(), owner);
        assertEquals(200, updated.statusCode());
        assertEquals("Booking.com", json.readTree(updated.body()).get("bookingSource").asText());
        input.put("version", json.readTree(updated.body()).get("version").asLong());
        input.put("bookingSource", "");
        assertEquals("", json.readTree(call("PUT", entry, input.toString(), owner).body()).get("bookingSource").asText());
        input.put("bookingSource", "x".repeat(161));
        assertEquals(400, call("POST", trip + "/entries", input.toString(), owner).statusCode());
    }

    @Test void storesStationEmbedLinksAndRejectsUnsafeSources() throws Exception {
        String trip = createTrip();
        ObjectNode input = entryInput("ACTIVITY");
        String embed = "https://www.google.com/maps/embed?pb=!1m18!synthetic";
        ((ObjectNode) input.withArray("points").get(0)).put("mapEmbedUrl", embed);
        var created = call("POST", trip + "/entries", input.toString(), owner);
        assertEquals(201, created.statusCode(), created.body());
        var body = json.readTree(created.body());
        assertEquals(embed, body.get("points").get(0).get("mapEmbedUrl").asText());
        String entry = trip + "/entries/" + body.get("id").asText();
        assertEquals(404, call("GET", entry, null, account(false).id).statusCode());
        var read = json.readTree(call("GET", entry, null, owner).body());
        assertEquals(embed, read.get("points").get(0).get("mapEmbedUrl").asText());
        ((ObjectNode) input.withArray("points").get(0)).put("mapEmbedUrl", "https://evil.invalid/maps/embed?pb=x");
        assertEquals(400, call("POST", trip + "/entries", input.toString(), owner).statusCode());
    }
}
