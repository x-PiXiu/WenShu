package com.example.rag.flashcard;

import com.example.rag.config.DatabasePool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 闪卡数据持久化：SQLite 存储 Deck + Card，支持间隔重复查询
 */
public class FlashcardStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Connection getConnection() throws SQLException {
        return DatabasePool.getConnection();
    }

    public FlashcardStore() {
        initSchema();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fc_deck (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    source_file TEXT,
                    card_count INTEGER NOT NULL DEFAULT 0,
                    mastered_count INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fc_card (
                    id TEXT PRIMARY KEY,
                    deck_id TEXT NOT NULL,
                    front TEXT NOT NULL,
                    back TEXT NOT NULL,
                    tags TEXT,
                    difficulty INTEGER NOT NULL DEFAULT 2,
                    review_count INTEGER NOT NULL DEFAULT 0,
                    correct_count INTEGER NOT NULL DEFAULT 0,
                    interval_days REAL NOT NULL DEFAULT 1.0,
                    next_review_at INTEGER,
                    last_reviewed_at INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (deck_id) REFERENCES fc_deck(id) ON DELETE CASCADE
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fc_card_deck ON fc_card(deck_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_fc_card_next_review ON fc_card(next_review_at)");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init flashcard tables: " + e.getMessage(), e);
        }
    }

    // ===== Deck CRUD =====

    public Deck createDeck(String title, String description, String sourceFile) {
        String id = "deck-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO fc_deck (id, title, description, source_file, card_count, mastered_count, created_at, updated_at) VALUES (?, ?, ?, ?, 0, 0, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, sourceFile);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create deck: " + e.getMessage(), e);
        }
        return new Deck(id, title, description, sourceFile, 0, 0, now, now);
    }

    public Deck getDeck(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM fc_deck WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapDeck(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public List<Deck> listDecks() {
        List<Deck> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM fc_deck ORDER BY updated_at DESC")) {
            while (rs.next()) result.add(mapDeck(rs));
        } catch (SQLException ignored) {}
        return result;
    }

    public void deleteDeck(String id) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement delCards = conn.prepareStatement("DELETE FROM fc_card WHERE deck_id = ?");
                 PreparedStatement delDeck = conn.prepareStatement("DELETE FROM fc_deck WHERE id = ?")) {
                delCards.setString(1, id);
                delCards.executeUpdate();
                delDeck.setString(1, id);
                delDeck.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to delete deck: " + e.getMessage());
        }
    }

    public void updateDeckStats(String deckId) {
        try (Connection conn = getConnection()) {
            int total = 0, mastered = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as total, " +
                    "SUM(CASE WHEN interval_days >= 14 THEN 1 ELSE 0 END) as mastered FROM fc_card WHERE deck_id = ?")) {
                ps.setString(1, deckId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt("total");
                        mastered = rs.getInt("mastered");
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE fc_deck SET card_count = ?, mastered_count = ?, updated_at = ? WHERE id = ?")) {
                ps.setInt(1, total);
                ps.setInt(2, mastered);
                ps.setLong(3, System.currentTimeMillis());
                ps.setString(4, deckId);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    // ===== Card CRUD =====

    public Card createCard(String deckId, String front, String back, List<String> tags, int difficulty) {
        String id = "card-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        String tagsJson = serializeTags(tags);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO fc_card (id, deck_id, front, back, tags, difficulty, review_count, correct_count, " +
                             "interval_days, next_review_at, last_reviewed_at, created_at, updated_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 1.0, NULL, NULL, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, deckId);
            ps.setString(3, front);
            ps.setString(4, back);
            ps.setString(5, tagsJson);
            ps.setInt(6, difficulty);
            ps.setLong(7, now);
            ps.setLong(8, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to create card: " + e.getMessage());
        }
        return new Card(id, deckId, front, back, tags, difficulty, 0, 0, 1.0, null, null, now, now);
    }

    public Card getCard(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM fc_card WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapCard(rs) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public List<Card> listCardsByDeck(String deckId) {
        List<Card> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM fc_card WHERE deck_id = ? ORDER BY created_at ASC")) {
            ps.setString(1, deckId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapCard(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] listCardsByDeck failed: " + e.getMessage());
        }
        return result;
    }

    public Card updateCard(String id, String front, String back, List<String> tags, int difficulty) {
        String tagsJson = serializeTags(tags);
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE fc_card SET front = ?, back = ?, tags = ?, difficulty = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, front);
            ps.setString(2, back);
            ps.setString(3, tagsJson);
            ps.setInt(4, difficulty);
            ps.setLong(5, now);
            ps.setString(6, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[WARN] Failed to update card: " + e.getMessage());
        }
        return getCard(id);
    }

    public void deleteCard(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM fc_card WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ===== Spaced Repetition =====

    /** Get all cards in random order — for "re-study all" mode */
    public List<Card> getAllCardsShuffled(String deckId) {
        List<Card> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM fc_card WHERE deck_id = ? ORDER BY RANDOM()")) {
            ps.setString(1, deckId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapCard(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getAllCardsShuffled failed for deck " + deckId + ": " + e.getMessage());
        }
        return result;
    }

    public List<Card> getDueCards(String deckId) {
        List<Card> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM fc_card WHERE deck_id = ? AND (next_review_at IS NULL OR next_review_at <= ?) ORDER BY RANDOM()")) {
            ps.setString(1, deckId);
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapCard(rs));
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getDueCards failed for deck " + deckId + ": " + e.getMessage());
        }
        return result;
    }

    public Card updateReview(String cardId, String grade) {
        Card card = getCard(cardId);
        if (card == null) return null;

        var result = Sm2Scheduler.nextInterval(card.intervalDays, grade);
        long now = System.currentTimeMillis();
        boolean correct = "remembered".equals(grade);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE fc_card SET interval_days = ?, next_review_at = ?, last_reviewed_at = ?, " +
                             "review_count = review_count + 1, correct_count = correct_count + ?, updated_at = ? WHERE id = ?")) {
            ps.setDouble(1, result.intervalDays());
            ps.setLong(2, result.nextReviewAt());
            ps.setLong(3, now);
            ps.setInt(4, correct ? 1 : 0);
            ps.setLong(5, now);
            ps.setString(6, cardId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}

        // Refresh deck stats
        updateDeckStats(card.deckId);

        return getCard(cardId);
    }

    // ===== Statistics =====

    public DeckStats getDeckStats(String deckId) {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) as total, " +
                             "SUM(CASE WHEN interval_days >= 14 THEN 1 ELSE 0 END) as mastered, " +
                             "SUM(CASE WHEN interval_days < 21 AND review_count > 0 THEN 1 ELSE 0 END) as learning, " +
                             "SUM(CASE WHEN next_review_at IS NULL THEN 1 ELSE 0 END) as new_count, " +
                             "SUM(CASE WHEN next_review_at IS NOT NULL AND next_review_at <= ? THEN 1 ELSE 0 END) as due " +
                             "FROM fc_card WHERE deck_id = ?")) {
            ps.setLong(1, now);
            ps.setString(2, deckId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int mastered = rs.getInt("mastered");
                    int learning = rs.getInt("learning");
                    int newCount = rs.getInt("new_count");
                    int due = rs.getInt("due");
                    return new DeckStats(total, due, mastered, learning, newCount);
                }
            }
        } catch (SQLException ignored) {}
        return new DeckStats(0, 0, 0, 0, 0);
    }

    public DeckStats getOverallStats() {
        long now = System.currentTimeMillis();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) as total, " +
                             "SUM(CASE WHEN interval_days >= 14 THEN 1 ELSE 0 END) as mastered, " +
                             "SUM(CASE WHEN interval_days < 21 AND review_count > 0 THEN 1 ELSE 0 END) as learning, " +
                             "SUM(CASE WHEN next_review_at IS NULL THEN 1 ELSE 0 END) as new_count, " +
                             "SUM(CASE WHEN next_review_at IS NOT NULL AND next_review_at <= " + now + " THEN 1 ELSE 0 END) as due " +
                             "FROM fc_card")) {
            if (rs.next()) {
                return new DeckStats(rs.getInt("total"), rs.getInt("due"),
                        rs.getInt("mastered"), rs.getInt("learning"), rs.getInt("new_count"));
            }
        } catch (SQLException ignored) {}
        return new DeckStats(0, 0, 0, 0, 0);
    }

    // ===== Mapping =====

    private Deck mapDeck(ResultSet rs) throws SQLException {
        return new Deck(
                rs.getString("id"), rs.getString("title"), rs.getString("description"),
                rs.getString("source_file"), rs.getInt("card_count"), rs.getInt("mastered_count"),
                rs.getLong("created_at"), rs.getLong("updated_at")
        );
    }

    private Card mapCard(ResultSet rs) throws SQLException {
        long nextReviewRaw = rs.getLong("next_review_at");
        Long nextReviewAt = rs.wasNull() ? null : nextReviewRaw;
        long lastReviewedRaw = rs.getLong("last_reviewed_at");
        Long lastReviewedAt = rs.wasNull() ? null : lastReviewedRaw;
        return new Card(
                rs.getString("id"), rs.getString("deck_id"),
                rs.getString("front"), rs.getString("back"),
                deserializeTags(rs.getString("tags")),
                rs.getInt("difficulty"), rs.getInt("review_count"), rs.getInt("correct_count"),
                rs.getDouble("interval_days"),
                nextReviewAt, lastReviewedAt,
                rs.getLong("created_at"), rs.getLong("updated_at")
        );
    }

    private String serializeTags(List<String> tags) {
        try {
            return tags != null ? MAPPER.writeValueAsString(tags) : "[]";
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> deserializeTags(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    // ===== Records =====

    public record Deck(String id, String title, String description,
                       String sourceFile, int cardCount, int masteredCount,
                       long createdAt, long updatedAt) {}

    public record Card(String id, String deckId, String front, String back,
                       List<String> tags, int difficulty, int reviewCount,
                       int correctCount, double intervalDays,
                       Long nextReviewAt, Long lastReviewedAt,
                       long createdAt, long updatedAt) {}

    public record DeckStats(int totalCards, int dueToday, int masteredCount,
                            int learningCount, int newCount) {}
}
