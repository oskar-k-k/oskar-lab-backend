package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.util.UUID;
import static dev.oskar_lab.backend.travel.TravelTypes.*;

/** Metadata is separate from bytes so listing bookings never loads document contents. */
@Entity
@Table(name = "travel_attachments")
public class TravelAttachment {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false) public UUID entryId;
    @Enumerated(EnumType.STRING) @Column(length = 24) public PointRole pointRole;
    @Column(nullable = false, length = 200) public String filename;
    @Column(nullable = false, length = 40) public String mediaType;
    @Column(nullable = false) public long size;
    public UUID orbitFileId;
    @Column(nullable = false, length = 64) public String storageProject = "traveltime";
    @Column(length = 64) public String sha256;
}
