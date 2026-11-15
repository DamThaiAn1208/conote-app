package com.conote.server.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.conote.common.enums.NoteType;
import com.conote.common.enums.SharePermission;
import com.conote.common.enums.ShareStatus;
import com.conote.common.model.Note;
import com.conote.common.model.NoteOrder;
import com.conote.common.model.NoteOrderId;
import com.conote.common.model.Share;
import com.conote.common.model.Tag;
import com.conote.common.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Runs only against a dedicated test DB configured in ignored db.local.properties. */
class Stage2DaoIntegrationTest {
    @Test
    void noteSurvivesEntityManagerFactoryRestart() throws Exception {
        Path config = Path.of("db.local.properties");
        assumeTrue(Files.isRegularFile(config), "No dedicated Stage 2 DB configured");
        Properties properties = new Properties();
        try (var input = Files.newInputStream(config)) {
            properties.load(input);
        }
        String url = properties.getProperty("url", "");
        assertTrue(url.matches("(?i)^jdbc:mariadb://[^/]+/conote_stage2_test(?:\\?.*)?$"),
                "Refusing to run integration test outside conote_stage2_test");
        String userName = properties.getProperty("user", "");
        String password = properties.getProperty("password", "");
        applySchema(url, userName, password);

        String noteId = UUID.randomUUID().toString();
        Long ownerId = null;
        try {
            try (EntityManagerFactory factory = newFactory(url, userName, password)) {
                EntityManager em = factory.createEntityManager();
                try {
                    em.getTransaction().begin();
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                    User owner = user("restart", now);
                    em.persist(owner);
                    em.flush();
                    ownerId = owner.getUserId();
                    Note note = new Note();
                    note.setNoteId(noteId);
                    note.setOwner(owner);
                    note.setTitle("Lưu qua restart");
                    note.setContent("Nội dung");
                    note.setSearchText("Nội dung");
                    note.setColor("default");
                    note.setPinned(false);
                    note.setDeleted(false);
                    note.setShareStatus(ShareStatus.PRIVATE);
                    note.setNoteType(NoteType.TEXT);
                    note.setVersion(1L);
                    note.setCreatedAt(now);
                    note.setUpdatedAt(now);
                    new NoteDao().persist(em, note);
                    em.getTransaction().commit();
                } finally {
                    if (em.getTransaction().isActive()) em.getTransaction().rollback();
                    em.close();
                }
            }

            try (EntityManagerFactory restarted = newFactory(url, userName, password)) {
                EntityManager em = restarted.createEntityManager();
                try {
                    assertEquals("Lưu qua restart", new NoteDao()
                            .findOwned(em, ownerId, noteId).orElseThrow().getTitle());
                } finally {
                    em.close();
                }
            }
        } finally {
            if (ownerId != null) {
                try (var connection = DriverManager.getConnection(url, userName, password);
                     var deleteNote = connection.prepareStatement("DELETE FROM notes WHERE note_id = ?");
                     var deleteUser = connection.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                    deleteNote.setString(1, noteId);
                    deleteNote.executeUpdate();
                    deleteUser.setLong(1, ownerId);
                    deleteUser.executeUpdate();
                }
            }
        }
    }

    @Test
    void schemaAndDaoPreserveIsolationVersionAndSoftDelete() throws Exception {
        Path config = Path.of("db.local.properties");
        assumeTrue(Files.isRegularFile(config), "No dedicated Stage 2 DB configured");
        Properties properties = new Properties();
        try (var input = Files.newInputStream(config)) {
            properties.load(input);
        }
        String url = properties.getProperty("url", "");
        assertTrue(url.matches("(?i)^jdbc:mariadb://[^/]+/conote_stage2_test(?:\\?.*)?$"),
                "Refusing to run integration test outside conote_stage2_test");
        String user = properties.getProperty("user", "");
        String password = properties.getProperty("password", "");

        applySchema(url, user, password);
        try (EntityManagerFactory factory = newFactory(url, user, password)) {
            EntityManager em = factory.createEntityManager();
            try {
                em.getTransaction().begin();
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                User owner = user("owner", now);
                User recipient = user("recipient", now);
                User outsider = user("outsider", now);
                em.persist(owner);
                em.persist(recipient);
                em.persist(outsider);
                em.flush();

                Note note = new Note();
                note.setNoteId(UUID.randomUUID().toString());
                note.setOwner(owner);
                note.setTitle("Tiếng Việt");
                note.setContent("Nội dung");
                note.setSearchText("Nội dung");
                note.setColor("amber");
                note.setPinned(false);
                note.setDeleted(false);
                note.setShareStatus(ShareStatus.PRIVATE);
                note.setNoteType(NoteType.TEXT);
                note.setVersion(1L);
                note.setCreatedAt(now);
                note.setUpdatedAt(now);
                NoteDao notes = new NoteDao();
                notes.persist(em, note);
                em.flush();

                Tag tag = new Tag();
                tag.setUser(owner);
                tag.setTagName("Học tập");
                tag.setCreatedAt(now);
                new TagDao().persist(em, tag);
                note.getTags().add(tag);
                NoteOrder ownerState = new NoteOrder();
                ownerState.setId(new NoteOrderId(owner.getUserId(), note.getNoteId()));
                ownerState.setUser(owner);
                ownerState.setNote(note);
                ownerState.setSortOrder(7L);
                ownerState.setPinned(true);
                ownerState.setFavorite(true);
                ownerState.setUpdatedAt(now);
                new UserNoteStateDao().persist(em, ownerState);
                em.flush();

                assertTrue(notes.findVisible(em, owner.getUserId(), note.getNoteId()).isPresent());
                assertFalse(notes.findVisible(em, outsider.getUserId(), note.getNoteId()).isPresent());
                assertTrue(new TagDao().findOwned(em, owner.getUserId(), tag.getTagId()).isPresent());
                assertFalse(new TagDao().findOwned(em, outsider.getUserId(), tag.getTagId()).isPresent());
                assertTrue(new UserNoteStateDao().find(em, owner.getUserId(), note.getNoteId())
                        .orElseThrow().getFavorite());
                assertFalse(new UserNoteStateDao().find(em, recipient.getUserId(), note.getNoteId())
                        .isPresent());
                assertEquals(1, notes.updateIfVersion(em, owner.getUserId(), note.getNoteId(), 1,
                        "Đã sửa", "Mới", "blue", "Mới", now.plusSeconds(1)));
                assertEquals(0, notes.updateIfVersion(em, owner.getUserId(), note.getNoteId(), 1,
                        "Ghi đè", "Sai", "red", "Sai", now.plusSeconds(2)));
                assertEquals(0, notes.updateIfVersion(em, outsider.getUserId(), note.getNoteId(), 2,
                        "Lạ", "Sai", "red", "Sai", now.plusSeconds(2)));

                Share share = new Share();
                share.setNote(note);
                share.setSharedBy(owner);
                share.setSharedWith(recipient);
                share.setPermission(SharePermission.VIEW);
                share.setCreatedAt(now);
                new ShareDao().persist(em, share);
                em.flush();
                assertTrue(notes.findVisible(em, recipient.getUserId(), note.getNoteId()).isPresent());
                assertEquals(0, notes.updateIfVersion(em, recipient.getUserId(), note.getNoteId(), 2,
                        "VIEW", "Sai", "red", "Sai", now.plusSeconds(2)));
                assertEquals(1, notes.softDeleteOwnedIfVersion(em, owner.getUserId(), note.getNoteId(), 2,
                        now.plusSeconds(3)));
                em.clear();
                assertFalse(notes.findVisible(em, recipient.getUserId(), note.getNoteId()).isPresent());
            } finally {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                em.close();
            }
        }
    }

    private static User user(String label, LocalDateTime now) {
        User user = new User();
        user.setUserName(label);
        user.setEmail(label + "+" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("$2a$10$test-only-placeholder");
        user.setFullName(label);
        user.setVerified(false);
        user.setActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    private static EntityManagerFactory newFactory(String url, String user, String password) {
        return Persistence.createEntityManagerFactory("conotePU", Map.of(
                "jakarta.persistence.jdbc.driver", "org.mariadb.jdbc.Driver",
                "jakarta.persistence.jdbc.url", url,
                "jakarta.persistence.jdbc.user", user,
                "jakarta.persistence.jdbc.password", password));
    }

    private static void applySchema(String url, String user, String password) throws Exception {
        Path schema = Path.of("src/main/resources/db/schema.sql");
        StringBuilder sql = new StringBuilder();
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            for (String line : Files.readAllLines(schema)) {
                int comment = line.indexOf("--");
                String code = comment < 0 ? line : line.substring(0, comment);
                sql.append(code).append('\n');
                int end;
                while ((end = sql.indexOf(";")) >= 0) {
                    String command = sql.substring(0, end).trim();
                    if (!command.isEmpty()) {
                        statement.execute(command);
                    }
                    sql.delete(0, end + 1);
                }
            }
        }
    }
}
