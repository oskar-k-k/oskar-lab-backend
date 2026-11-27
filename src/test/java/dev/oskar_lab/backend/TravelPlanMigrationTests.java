package dev.oskar_lab.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies a real V16-to-V17 upgrade in an isolated schema containing only synthetic booking data. */
@EnabledIfEnvironmentVariable(named = "TRAVEL_TEST_DATABASE_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/traveltest")
class TravelPlanMigrationTests {
    @Test void preservesExistingHotelPlansAndBookedInstants() throws Exception {
        String url = System.getenv("TRAVEL_TEST_DATABASE_URL");
        String schema = "plan_migration_" + UUID.randomUUID().toString().replace("-", "");
        var configuration = Flyway.configure().dataSource(url, "traveltest", "synthetic-travel-database-password").schemas(schema).defaultSchema(schema);
        try {
            configuration.target("16").load().migrate();
            try (var connection = DriverManager.getConnection(url, "traveltest", "synthetic-travel-database-password")) {
                connection.setSchema(schema);
                try (var sql = connection.createStatement()) {
                    sql.execute("""
                        INSERT INTO platform_accounts(id, username, username_key, email, terms_accepted_at, created_at)
                        VALUES ('11111111-1111-4111-8111-111111111111', 'example', 'example', 'example@example.invalid', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                        INSERT INTO platform_resources(id, resource_type, owner_id, created_at)
                        VALUES ('22222222-2222-4222-8222-222222222222', 'travel.trip', '11111111-1111-4111-8111-111111111111', CURRENT_TIMESTAMP);
                        INSERT INTO travel_trips(id, title, start_date, end_date) VALUES ('22222222-2222-4222-8222-222222222222', 'Example', '2030-12-01', '2030-12-04');
                        INSERT INTO travel_entries(id, trip_id, type, title, sort_date, sort_time, updated_at, version)
                        VALUES ('33333333-3333-4333-8333-333333333333', '22222222-2222-4222-8222-222222222222', 'HOTEL', 'Example stay', '2030-12-02', '17:00', CURRENT_TIMESTAMP, 4);
                        INSERT INTO travel_points(id, entry_id, position, role, local_date, local_time, time_zone, utc_offset, time_meaning, place_name, timeline_time, timeline_utc_offset)
                        VALUES ('44444444-4444-4444-8444-444444444444', '33333333-3333-4333-8333-333333333333', 0, 'CHECK_IN', '2030-12-02', '14:00', 'Asia/Bangkok', '+07:00', 'EXACT', 'Example hotel', '17:00', '+07:00');
                        """);
                }
            }
            Flyway.configure().dataSource(url, "traveltest", "synthetic-travel-database-password").schemas(schema).defaultSchema(schema).load().migrate();
            try (var connection = DriverManager.getConnection(url, "traveltest", "synthetic-travel-database-password")) {
                connection.setSchema(schema);
                try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT local_date, local_time, timeline_date, timeline_time, timeline_utc_offset, time_meaning FROM travel_points")) {
                    assertTrue(rows.next());
                    assertEquals("2030-12-02", rows.getString(1)); assertEquals("14:00:00", rows.getString(2));
                    assertEquals("2030-12-02", rows.getString(3)); assertEquals("17:00:00", rows.getString(4));
                    assertEquals("+07:00", rows.getString(5)); assertEquals("NOT_BEFORE", rows.getString(6));
                }
                try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT version FROM travel_entries")) {
                    assertTrue(rows.next()); assertEquals(4, rows.getLong(1));
                }
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, "traveltest", "synthetic-travel-database-password"); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
