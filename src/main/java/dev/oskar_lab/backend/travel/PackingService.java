package dev.oskar_lab.backend.travel;

import dev.oskar_lab.backend.access.ResourceAccessService;
import dev.oskar_lab.backend.auth.AuthFailure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static dev.oskar_lab.backend.travel.PackingDtos.*;

/** Authorizes and saves the bounded packing aggregate atomically, including all checkbox states. */
@Service @Transactional
public class PackingService {
    private final ResourceAccessService access;
    private final TravelPackingRepository lists;
    public PackingService(ResourceAccessService access, TravelPackingRepository lists) {
        this.access = access; this.lists = lists;
    }

    /** Returns only an owner's or verified viewer's trip-scoped checklist. */
    @Transactional(readOnly = true)
    public PackingList get(UUID user, UUID tripId) {
        access.requireRead(user, TravelService.RESOURCE_TYPE, tripId);
        return dto(requireList(tripId));
    }

    /** Owner-only, last-read-version write; stale edits and checks fail instead of overwriting changes. */
    public PackingList save(UUID user, UUID tripId, PackingList input) {
        access.requireOwner(user, TravelService.RESOURCE_TYPE, tripId);
        TravelPackingList list = requireList(tripId);
        if (list.version != input.version()) throw new AuthFailure(409, "conflict");
        validateIdsAndLimits(input);
        list.people.removeIf(person -> input.people().stream().noneMatch(p -> p.id().equals(person.id)));
        for (int index = 0; index < input.people().size(); index++) {
            var value = input.people().get(index);
            var person = list.people.stream().filter(p -> p.id.equals(value.id())).findFirst().orElseGet(() -> {
                var added = new TravelPackingPerson(); added.id = value.id(); added.list = list; list.people.add(added); return added;
            });
            person.name = value.name().strip(); person.position = index;
            applyGroups(person, value.groups());
        }
        list.updatedAt = Instant.now();
        lists.flush();
        return dto(list);
    }

    private void applyGroups(TravelPackingPerson person, List<PackingGroup> values) {
        person.groups.removeIf(group -> values.stream().noneMatch(g -> g.id().equals(group.id)));
        for (int index = 0; index < values.size(); index++) {
            var value = values.get(index);
            var group = person.groups.stream().filter(g -> g.id.equals(value.id())).findFirst().orElseGet(() -> {
                var added = new TravelPackingGroup(); added.id = value.id(); added.person = person; person.groups.add(added); return added;
            });
            group.name = value.name().strip(); group.position = index;
            applyItems(group, value.items());
        }
    }

    private void applyItems(TravelPackingGroup group, List<PackingItem> values) {
        group.items.removeIf(item -> values.stream().noneMatch(i -> i.id().equals(item.id)));
        for (int index = 0; index < values.size(); index++) {
            var value = values.get(index);
            var item = group.items.stream().filter(i -> i.id.equals(value.id())).findFirst().orElseGet(() -> {
                var added = new TravelPackingItem(); added.id = value.id(); added.group = group; group.items.add(added); return added;
            });
            item.name = value.name().strip(); item.checked = value.checked(); item.position = index;
        }
    }

    private void validateIdsAndLimits(PackingList value) {
        var ids = new HashSet<UUID>();
        int groupCount = 0, itemCount = 0;
        for (var person : value.people()) {
            unique(ids, person.id());
            groupCount += person.groups().size();
            for (var group : person.groups()) {
                unique(ids, group.id()); itemCount += group.items().size();
                for (var item : group.items()) unique(ids, item.id());
            }
        }
        if (groupCount > 100 || itemCount > 500) throw new AuthFailure(400, "packingLimit");
    }
    private void unique(Set<UUID> ids, UUID id) {
        if (!ids.add(id)) throw new AuthFailure(400, "invalidPacking");
    }
    private TravelPackingList requireList(UUID tripId) {
        return lists.findById(tripId).orElseThrow(() -> new AuthFailure(404, "notFound"));
    }
    private PackingList dto(TravelPackingList list) {
        return new PackingList(list.version, list.people.stream().sorted(Comparator.comparingInt(p -> p.position))
            .map(p -> new PackingPerson(p.id, p.name, p.groups.stream().sorted(Comparator.comparingInt(g -> g.position))
                .map(g -> new PackingGroup(g.id, g.name, g.items.stream().sorted(Comparator.comparingInt(i -> i.position))
                    .map(i -> new PackingItem(i.id, i.name, i.checked)).toList())).toList())).toList());
    }
}
