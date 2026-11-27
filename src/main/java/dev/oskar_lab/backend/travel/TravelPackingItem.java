package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.util.UUID;

/** A single persisted packing checkbox in an ordered category. */
@Entity @Table(name = "travel_packing_items")
public class TravelPackingItem {
    @Id public UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "group_id") public TravelPackingGroup group;
    @Column(nullable = false, length = 200) public String name;
    @Column(nullable = false) public int position;
    @Column(nullable = false) public boolean checked;
}
