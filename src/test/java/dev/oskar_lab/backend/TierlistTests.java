package dev.oskar_lab.backend;

import dev.oskar_lab.backend.tierlist.TierlistService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TierlistTests {
    @Test void answersStayHiddenAndHistorySurvivesRounds() {
        var s = new TierlistService(); var a = s.create("A", "food", List.of("Top", "Flop"), List.of(), 10);
        String code = (String)a.get("code"), token = (String)a.get("token");
        var b = s.join(code, "B"); String other = (String)b.get("token");
        assertThrows(ResponseStatusException.class, () -> s.view(code, "fake"));
        assertThrows(ResponseStatusException.class, () -> s.action(code, other, "start", 0, null));
        s.action(code, token, "start", 0, null); s.action(code, token, "vote", 0, 0);
        var players = (List<?>)s.view(code, other).get("players");
        assertEquals(List.of(), ((Map<?, ?>)players.getFirst()).get("answers"));
        assertThrows(ResponseStatusException.class, () -> s.action(code, token, "vote", 0, 1));
        s.action(code, other, "vote", 0, 1); assertEquals("reveal", s.view(code, token).get("phase"));
        s.action(code, token, "next", 0, null);
        assertEquals(1, s.view(code, token).get("round"));
        assertEquals(1, ((List<?>)s.view(code, token).get("words")).size());
        assertThrows(ResponseStatusException.class, () -> s.action(code, token, "vote", 0, 1));
        for (int round = 1; round < 10; round++) {
            s.action(code, token, "vote", round, 0); s.action(code, other, "vote", round, 1);
            s.action(code, token, "next", round, null);
        }
        assertEquals("finished", s.view(code, token).get("phase"));
        assertEquals(10, ((List<?>)s.view(code, token).get("words")).size());
    }
    @Test void validatesCustomWordsAndLobbyBoundaries() {
        var s = new TierlistService();
        assertThrows(ResponseStatusException.class, () -> s.create("A", "", List.of("S", "F"), List.of(), 1));
        assertThrows(ResponseStatusException.class, () -> s.create("A", "food", List.of("S"), List.of(), 1));
        assertThrows(ResponseStatusException.class, () -> s.create("A", "", List.of("S", "F"), List.of("A", "a"), 2));
        var a = s.create("A", "intimacy", List.of("S", "F"), List.of(), 3); String code = (String)a.get("code");
        assertThrows(ResponseStatusException.class, () -> s.join(code, "a"));
        assertThrows(ResponseStatusException.class, () -> s.action(code, (String)a.get("token"), "start", 0, null));
        assertNotNull(s.join(code, "B"));
    }
    @Test void mixesCustomWordsAndHonorsRoundCountWithoutRepetition() {
        var s = new TierlistService();
        var a = s.create("A", "food", List.of("S", "F"), List.of("pizza", "Lasagne", "LASAGNE"), 11);
        String code = (String)a.get("code"), token = (String)a.get("token"), other = (String)s.join(code, "B").get("token");
        s.action(code, token, "start", 0, null);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 11; i++) {
            assertTrue(seen.add((String)s.view(code, token).get("word")));
            s.action(code, token, "vote", i, 0); s.action(code, other, "vote", i, 1); s.action(code, token, "next", i, null);
        }
        assertTrue(seen.contains("Lasagne")); assertEquals("finished", s.view(code, token).get("phase"));
        var custom = s.create("C", "", List.of("S", "F"), List.of("Alpha", "Beta", "Gamma"), 2);
        String c = (String)custom.get("code"), t = (String)custom.get("token");
        s.join(c, "D"); s.action(c, t, "start", 0, null);
        assertEquals(2, s.view(c, t).get("total"));
        assertTrue(List.of("Alpha", "Beta", "Gamma").contains(s.view(c, t).get("word")));
    }
}
