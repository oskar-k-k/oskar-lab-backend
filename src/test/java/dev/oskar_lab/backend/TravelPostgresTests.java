package dev.oskar_lab.backend;

import dev.oskar_lab.backend.auth.*;
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
    @Autowired TravelAttachmentService attachments;
    @Autowired ResourceAccessService access;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test void migratesValidatesAndDeletesPrivateContentOnPostgres() {
        Account owner = new Account();
        owner.email = UUID.randomUUID() + "@example.invalid";
        owner.username = UUID.randomUUID().toString().substring(0, 30); owner.usernameKey = owner.username;
        owner.termsAcceptedAt = Instant.now(); accounts.saveAndFlush(owner);
        var trip = travel.create(owner.id, new SaveTrip("Synthetic journey", "", LocalDate.of(2030, 12, 1), LocalDate.of(2030, 12, 10), 0L));
        var point = new Point(PointRole.START, LocalDate.of(2030, 12, 2), null, "Asia/Bangkok", null, TimeMeaning.UNSPECIFIED, "Example place", "");
        var entry = travel.createEntry(owner.id, trip.id(), new SaveEntry(EntryType.ACTIVITY, "Example activity", "", "", List.of(point), 0L));
        assertNull(travel.getEntry(owner.id, trip.id(), entry.id()).points().getFirst().time());
        byte[] bytes = "%PDF-1.7 synthetic fixture".getBytes(StandardCharsets.UTF_8);
        var attachment = attachments.upload(owner.id, trip.id(), entry.id(), PointRole.START, "example.pdf", "application/pdf", bytes);
        assertArrayEquals(bytes, attachments.download(owner.id, trip.id(), entry.id(), attachment.id()).bytes());
        access.invite(owner.id, TravelService.RESOURCE_TYPE, trip.id(), "viewer@example.invalid");
        var unrelated = access.create(owner.id, "example.document");
        assertEquals(404, assertThrows(AuthFailure.class, () -> travel.get(owner.id, unrelated.id)).status);
        travel.delete(owner.id, trip.id(), trip.version());
        assertEquals(0, jdbc.queryForObject("select count(*) from travel_attachment_contents where id = ?", Integer.class, attachment.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from platform_resource_viewers where resource_id = ?", Integer.class, trip.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from travel_points where entry_id = ?", Integer.class, entry.id()));
        assertEquals(0, jdbc.queryForObject("select count(*) from platform_resources where id = ?", Integer.class, trip.id()));
        accounts.deleteById(owner.id);
        assertEquals(0, jdbc.queryForObject("select count(*) from platform_resources where id = ?", Integer.class, unrelated.id));
    }
}
