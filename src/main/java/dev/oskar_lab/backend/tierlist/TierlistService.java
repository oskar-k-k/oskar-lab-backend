package dev.oskar_lab.backend.tierlist;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** Single-instance, temporary guest rooms. Answers are released only after everyone submits. */
@Service
public class TierlistService {
    private final Map<String, Room> rooms = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private static final Map<String, List<String>> WORDS = Map.of(
        "animals", List.of("Hund", "Katze", "Maus", "Hamster", "Kaninchen", "Fuchs", "Bär", "Panda", "Koala", "Tiger", "Löwe", "Kuh", "Schwein", "Frosch", "Affe", "Huhn", "Pinguin", "Eule", "Ente", "Adler", "Wolf", "Pferd", "Biene", "Schmetterling", "Schnecke", "Schildkröte", "Delfin", "Elefant", "Giraffe", "Krokodil"),
        "intimacy", List.of("Missionarsstellung", "Reiterstellung", "Löffelchen", "Doggy Style", "Umgekehrte Reiterstellung", "Im Stehen", "Im Sitzen", "Seitlich", "Lotus", "Schere", "Schmetterling", "Brücke", "Wiege", "Brezel", "Amazonenstellung", "Wiener Auster", "69", "Seitliche 69", "Umgekehrtes Löffelchen", "Elefantenstellung", "Froschstellung", "Sphinx", "Katzenstellung", "T-Kreuz", "X-Stellung", "L-Stellung", "V-Stellung", "Wasserfall", "Schubkarre", "Tanzende Spinne"),
        "food", List.of("Pizza", "Sushi", "Pommes", "Pasta", "Döner", "Salat", "Pfannkuchen", "Eis", "Burger", "Burrito"),
        "dates", List.of("Kino", "Spaziergang", "Kochen", "Museum", "Picknick", "Konzert", "Brettspiele", "Wandern", "Café", "Bowling"));
    private static class Player {
        String id = UUID.randomUUID().toString(), token = UUID.randomUUID().toString(), name;
        List<Integer> answers = new ArrayList<>();
        Player(String name) { this.name = name; }
    }
    private static class Room {
        String code, category, host; List<String> rows, words;
        List<Player> players = new ArrayList<>(); String phase = "lobby"; int round;
        Instant touched = Instant.now();
    }
    private void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private String name(String value) {
        require(value != null && !value.isBlank() && value.strip().length() <= 24, "Name muss 1–24 Zeichen haben.");
        return value.strip();
    }
    private Room room(String code) {
        rooms.values().removeIf(r -> r.touched.isBefore(Instant.now().minusSeconds(7200)));
        Room r = rooms.get(code == null ? "" : code.toUpperCase(Locale.ROOT));
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby nicht gefunden oder abgelaufen.");
        r.touched = Instant.now(); return r;
    }
    private Player player(Room r, String token) {
        return r.players.stream().filter(p -> p.token.equals(token)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte der Lobby beitreten."));
    }
    public synchronized Map<String, Object> create(String name, String category, List<String> rows, List<String> extraWords, Integer rounds) {
        rooms.values().removeIf(r -> r.touched.isBefore(Instant.now().minusSeconds(7200)));
        require(rooms.size() < 500, "Zurzeit sind zu viele Räume geöffnet.");
        String selectedCategory = category == null ? "" : category;
        require(selectedCategory.isEmpty() || WORDS.containsKey(selectedCategory), "Unbekannte Kategorie.");
        require(extraWords == null || extraWords.size() <= 200, "Maximal 200 eigene Begriffe sind erlaubt.");
        Map<String, String> pool = new LinkedHashMap<>();
        for (String word : WORDS.getOrDefault(selectedCategory, List.of())) pool.put(word.toLowerCase(Locale.ROOT), word);
        if (extraWords != null) for (String word : extraWords) {
            require(word != null && !word.isBlank() && word.strip().length() <= 60, "Eigene Begriffe müssen 1–60 Zeichen haben.");
            pool.putIfAbsent(word.strip().toLowerCase(Locale.ROOT), word.strip());
        }
        require(!pool.isEmpty(), "Wähle eine Kategorie oder füge eigene Begriffe hinzu.");
        require(rounds != null && rounds >= 1 && rounds <= 100 && rounds <= pool.size(), "Wähle 1–100 Runden, höchstens so viele wie unterschiedliche Begriffe vorhanden sind.");
        require(rows != null && rows.size() >= 2 && rows.size() <= 8, "Wähle 2–8 Zeilen.");
        for (String row : rows) require(row != null && !row.isBlank() && row.strip().length() <= 32, "Zeilentitel müssen 1–32 Zeichen haben.");
        Room r = new Room();
        do { r.code = String.format("%06d", random.nextInt(1000000)); } while (rooms.containsKey(r.code));
        r.category = selectedCategory; r.rows = rows.stream().map(String::strip).toList();
        List<String> shuffled = new ArrayList<>(pool.values()); Collections.shuffle(shuffled, random);
        r.words = new ArrayList<>(shuffled.subList(0, rounds));
        Player p = new Player(name(name)); r.players.add(p); r.host = p.id; rooms.put(r.code, r);
        return Map.of("code", r.code, "token", p.token);
    }
    public synchronized Map<String, Object> join(String code, String name) {
        Room r = room(code); require(r.phase.equals("lobby"), "Die Partie hat bereits begonnen.");
        require(r.players.size() < 8, "Die Lobby ist voll (maximal 8 Spieler).");
        String clean = name(name); require(r.players.stream().noneMatch(p -> p.name.equalsIgnoreCase(clean)), "Dieser Name ist bereits vergeben.");
        Player p = new Player(clean); r.players.add(p); return Map.of("code", r.code, "token", p.token);
    }
    public synchronized Map<String, Object> view(String code, String token) {
        Room r = room(code); Player viewer = player(r, token);
        int revealed = r.phase.equals("reveal") || r.phase.equals("finished") ? r.round + 1 : r.round;
        List<Map<String, Object>> people = new ArrayList<>();
        for (Player p : r.players) {
            List<Integer> visible = List.copyOf(p.answers.subList(0, Math.min(revealed, p.answers.size())));
            people.add(Map.of("id", p.id, "name", p.name, "answers", visible, "submitted", p.answers.size() > r.round));
        }
        return Map.ofEntries(Map.entry("code", r.code), Map.entry("category", r.category), Map.entry("rows", r.rows),
            Map.entry("phase", r.phase), Map.entry("round", r.round), Map.entry("total", r.words.size()),
            Map.entry("word", r.phase.equals("lobby") ? "" : r.words.get(r.round)),
            Map.entry("words", List.copyOf(r.words.subList(0, revealed))), Map.entry("players", people),
            Map.entry("you", viewer.id), Map.entry("host", r.host),
            Map.entry("ownChoice", viewer.answers.size() > r.round ? viewer.answers.get(r.round) : -1));
    }
    public synchronized void action(String code, String token, String action, Integer round, Integer row) {
        Room r = room(code); Player p = player(r, token);
        if ("start".equals(action)) {
            require(p.id.equals(r.host) && r.phase.equals("lobby"), "Nur der Host kann die Partie starten.");
            require(r.players.size() >= 2, "Mindestens zwei Spieler werden benötigt."); r.phase = "voting";
        } else if ("vote".equals(action)) {
            require(r.phase.equals("voting") && round != null && round == r.round, "Diese Runde ist nicht mehr offen.");
            require(row != null && row >= 0 && row < r.rows.size(), "Ungültige Zeile.");
            require(p.answers.size() == r.round, "Du hast bereits abgegeben."); p.answers.add(row);
            if (r.players.stream().allMatch(person -> person.answers.size() > r.round)) r.phase = "reveal";
        } else if ("next".equals(action)) {
            require(p.id.equals(r.host) && r.phase.equals("reveal") && round != null && round == r.round, "Nur der Host kann nach der Auflösung fortfahren.");
            if (r.round + 1 == r.words.size()) r.phase = "finished";
            else { r.round++; r.phase = "voting"; }
        } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannte Aktion.");
    }
}
