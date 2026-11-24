package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.util.UUID;

/** Bounded private attachment bytes participate in the same transaction and backups as their metadata. */
@Entity
@Table(name = "travel_attachment_contents")
public class TravelAttachmentContent {
    @Id public UUID id;
    @JdbcTypeCode(Types.VARBINARY) @Column(nullable = false, columnDefinition = "bytea") public byte[] bytes;
}
