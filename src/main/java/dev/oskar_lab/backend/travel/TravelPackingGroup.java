package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.util.*;

/** Ordered category belonging to exactly one person's packing checklist. */
@Entity @Table(name = "travel_packing_groups")
public class TravelPackingGroup {
    @Id public UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "person_id") public TravelPackingPerson person;
    @Column(nullable = false, length = 100) public String name;
    @Column(nullable = false) public int position;
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC") public List<TravelPackingItem> items = new ArrayList<>();
}
