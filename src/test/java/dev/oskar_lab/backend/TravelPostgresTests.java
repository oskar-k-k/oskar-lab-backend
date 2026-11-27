package dev.oskar_lab.backend;

import dev.oskar_lab.backend.auth.*;
import dev.oskar_lab.backend.storage.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import dev.oskar_lab.backend.access.*;
import dev.oskar_lab.backend.travel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static dev.oskar_lab.backend.travel.TravelDtos.*;
import static dev.oskar_lab.backend.travel.TravelTypes.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in migration/bytea check against a disposable PostgreSQL database, never production. */
@EnabledIfEnvironmentVariable(named = "TRAVEL_TEST_DATABASE_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/traveltest")
@SpringBootTest
class TravelPostgresTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TRAVEL_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> "traveltest");
        registry.add("spring.datasource.password", () -> "synthetic-travel-database-password");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
    }

    @Autowired TravelService travel;
    @Autowired PackingService packing;
    @Autowired TravelAttachmentService attachments;
    @Autowired ResourceAccessService access;
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
    @Autowired JdbcTemplate jdbc;

    @Test void migratesValidatesAndDeletesPrivateContentOnPostgres() {
        configureCloud();
        Account owner = new Account();
        owner.email = UUID.randomUUID() + "@example.invalid";
        owner.username = UUID.randomUUID().toString().substring(0, 30); owner.usernameKey = owner.username;
        owner.termsAcceptedAt = Instant.now(); accounts.saveAndFlush(owner);
        var trip = travel.create(owner.id, new SaveTrip("Synthetic journey", "", LocalDate.of(2030, 12, 1), LocalDate.of(2030, 12, 10), 0L));
        var point = new Point(PointRole.START, LocalDate.of(2030, 12, 2), null, "Asia/Bangkok", null, TimeMeaning.UNSPECIFIED, "Example place", "", "https://www.google.com/maps/embed?pb=!1m18!synthetic");
        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns where table_name='travel_points' and column_name='timeline_time'", Integer.class));
        var entry = travel.createEntry(owner.id, trip.id(), new SaveEntry(EntryType.ACTIVITY, "Example activity", "", "", List.of(point), 0L, "Booking.com"));
        assertNull(travel.getEntry(owner.id, trip.id(), entry.id()).points().getFirst().time());
        assertEquals("Booking.com", travel.getEntry(owner.id, trip.id(), entry.id()).bookingSource());
        assertEquals(point.mapEmbedUrl(), travel.getEntry(owner.id, trip.id(), entry.id()).points().getFirst().mapEmbedUrl());
        byte[] bytes = "%PDF-1.7 synthetic fixture".getBytes(StandardCharsets.UTF_8);
        var attachment = attachments.upload(owner.id, trip.id(), entry.id(), PointRole.START, "example.pdf", "application/pdf", bytes);
        assertArrayEquals(bytes, attachments.download(owner.id, trip.id(), entry.id(), attachment.id()).bytes());
        access.invite(owner.id, TravelService.RESOURCE_TYPE, trip.id(), "viewer@example.invalid");
        var unrelated = access.create(owner.id, "example.document");
        assertEquals(404, assertThrows(AuthFailure.class, () -> travel.get(owner.id, unrelated.id)).status);
        var packingItem = new PackingDtos.PackingItem(UUID.randomUUID(), "Synthetic item", true);
        var packingGroup = new PackingDtos.PackingGroup(UUID.randomUUID(), "Synthetic group", List.of(packingItem));
        var packingPerson = new PackingDtos.PackingPerson(UUID.randomUUID(), "Synthetic traveler", List.of(packingGroup));
        var savedPacking = packing.save(owner.id, trip.id(), new PackingDtos.PackingList(0, List.of(packingPerson)));
        assertEquals(1, savedPacking.version());
        assertTrue(packing.get(owner.id, trip.id()).people().getFirst().groups().getFirst().items().getFirst().checked());
        travel.delete(owner.id, trip.id(), trip.version());
        assertEquals(0, jdbc.queryForObject("select count(*) from travel_packing_items where id = ?", Integer.class, packingItem.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from travel_packing_people where id = ?", Integer.class, packingPerson.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from travel_attachment_contents where id = ?", Integer.class, attachment.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from platform_resource_viewers where resource_id = ?", Integer.class, trip.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from travel_points where entry_id = ?", Integer.class, entry.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from platform_resources where id = ?", Integer.class, trip.id()));
        var secondTrip = travel.create(owner.id, new SaveTrip("Account deletion fixture", "", LocalDate.of(2030, 12, 1), LocalDate.of(2030, 12, 10), 0L));
        var secondEntry = travel.createEntry(owner.id, secondTrip.id(), new SaveEntry(EntryType.ACTIVITY, "Example activity", "", "", List.of(point), 0L, "Booking.com"));
        var secondAttachment = attachments.upload(owner.id, secondTrip.id(), secondEntry.id(), null, "example.pdf", "application/pdf", bytes);
        assertEquals("traveltime", jdbc.queryForObject("select storage_project from travel_attachments where id = ?", String.class, secondAttachment.id()));
        UUID cloudId = jdbc.queryForObject("select orbit_file_id from travel_attachments where id = ?", UUID.class, secondAttachment.id());
        var accountPackingItem = new PackingDtos.PackingItem(UUID.randomUUID(), "Account deletion packing fixture", false);
        packing.save(owner.id, secondTrip.id(), new PackingDtos.PackingList(0, List.of(new PackingDtos.PackingPerson(
            UUID.randomUUID(), "Example", List.of(new PackingDtos.PackingGroup(UUID.randomUUID(), "Example", List.of(accountPackingItem)))))));
        accounts.deleteById(owner.id);
        assertEquals(0, jdbc.queryForObject("select count(*) from travel_packing_items where id = ?", Integer.class, accountPackingItem.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from platform_resources where id = ?", Integer.class, unrelated.id));
        assertEquals(1, jdbc.queryForObject("select count(*) from platform_file_deletions where orbit_file_id = ? and project = 'traveltime'", Integer.class, cloudId));
    }
}
