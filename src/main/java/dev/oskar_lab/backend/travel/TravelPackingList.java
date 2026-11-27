package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** Trip-scoped aggregate version prevents lost checks and structural edits across devices. */
@Entity @Table(name = "travel_packing_lists")
public class TravelPackingList {
    @Id public UUID tripId;
    @Version public long version;
    @Column(nullable = false) public Instant updatedAt = Instant.now();
    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC") public List<TravelPackingPerson> people = new ArrayList<>();
}
