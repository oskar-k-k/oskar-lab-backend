package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.util.*;

/** Named tab within one trip's packing list; no account or email association is inferred. */
@Entity @Table(name = "travel_packing_people")
public class TravelPackingPerson {
    @Id public UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "trip_id") public TravelPackingList list;
    @Column(nullable = false, length = 80) public String name;
    @Column(nullable = false) public int position;
    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC") public List<TravelPackingGroup> groups = new ArrayList<>();
}
