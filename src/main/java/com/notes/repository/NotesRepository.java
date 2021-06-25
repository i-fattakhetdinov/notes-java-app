package com.notes.repository;

import com.notes.Pair;
import com.notes.model.Note;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.sql.Date;
import java.util.*;

@Repository
public class NotesRepository {
    private final String url = "jdbc:postgresql://localhost/postgres";
    private final String user = "postgres";
    private final String password = "admin";

    public NotesRepository() {
        try (Statement stmt = createStmt()
        ) {
            createTableIfItDoesntExist(stmt);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public Map<Long, Note> getAllNotes() {
        Map<Long, Note> allNotes = new HashMap<>();

        // Найдем все id заметок
        List<Long> allNoteIds = new ArrayList<>();
        String sqlSelectAllNotes = "SELECT id " +
                "FROM notes ";
        try (Statement stmt = createStmt()) {
            ResultSet rs = stmt.executeQuery(sqlSelectAllNotes);
            while (rs.next()) {
                allNoteIds.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        // По id найдем все заметки
        for (long id : allNoteIds) {
            allNotes.put(id, getNodeById(id));
        }
        return allNotes;
    }

    public Pair<Long, Note> addNote(Note note) {
        long noteId = -1;
        String sqlInsertInNotes = "INSERT INTO notes(title, text, created_at)" +
                "VALUES(?,?,?)";
        try (PreparedStatement pstmt = createPstmt(sqlInsertInNotes)
        ) {
            pstmt.setString(1, note.getTitle());
            pstmt.setString(2, note.getText());
            pstmt.setDate(3, note.getCreatedAt());
            noteId = getIdFromPstmtUpdateOperation(pstmt);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        insertTagsWithRelation(noteId, note.getHashTags());
        return new Pair<>(noteId, note);
    }

    public Note getNodeById(long id) {
        //Найдем сначала теги для данной заметки
        Set<String> gottenHashTags = new HashSet<>();
        String sqlSelectTags = "SELECT tag_name " +
                "FROM tags INNER JOIN notes_tags_relations " +
                "ON tags.id = notes_tags_relations.tag_id " +
                "WHERE notes_tags_relations.note_id = ?";
        try (PreparedStatement pstmt = createPstmt(sqlSelectTags)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                gottenHashTags.add(rs.getString(1));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        Note gottenNote = null;
        String sqlSelectNote = "SELECT title, text, created_at " +
                "FROM notes " +
                "WHERE id = ?";
        try (PreparedStatement pstmt = createPstmt(sqlSelectNote)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String title = rs.getString(1);
                String text = rs.getString(2);
                Date createdAt = rs.getDate(3);
                gottenNote = new Note(title, text, createdAt, gottenHashTags);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return gottenNote;
    }

    public void deleteNote(long id) {
        String sqlDeleteNote = "DELETE FROM notes " +
                "WHERE id = ?";
        try (PreparedStatement pstmt = createPstmt(sqlDeleteNote)
        ) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        deleteUnusedTags();
    }

    public Pair<Long, Note> updateNote(Long id, Note updatedNote) {
        Note oldNote = getNodeById(id);
        Set<String> oldHashTags = oldNote.getHashTags();
        Set<String> updatedHashTags = updatedNote.getHashTags();

        // Обновим запись в таблице
        String sqlUpdateOldRecord = "UPDATE notes " +
                "SET title = ?, text = ?" +
                "WHERE id = ?";
        try (PreparedStatement pstmt = createPstmt(sqlUpdateOldRecord)) {
            pstmt.setString(1, updatedNote.getTitle());
            pstmt.setString(2, updatedNote.getText());
            pstmt.setLong(3, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        // Удалим теги, которые больше не относятся к заметке из Relations
        Set<String> hashTagsToDelete = new HashSet<>();
        for (String oldHashTag : oldHashTags) {
            if (!updatedHashTags.contains(oldHashTag)) {
                hashTagsToDelete.add(oldHashTag);
            }
        }

        String sqlDeleteHashTagsFromRelation = "DELETE FROM notes_tags_relations " +
                "USING tags " +
                "WHERE notes_tags_relations.tag_id = tags.id  AND tags.tag_name = ?";
        for (String hashTagToDelete : hashTagsToDelete) {
            try (PreparedStatement pstmt = createPstmt(sqlDeleteHashTagsFromRelation)) {
                pstmt.setString(1, hashTagToDelete);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }

        //Теперь нужно почистить теги, которые больше нигде не используются
        deleteUnusedTags();

        //Добавим теги которых не было в таблицы tags (если требуется) и в Relation
        Set<String> addedHashTags = new HashSet<>();
        for (String updatedHashTag : updatedHashTags) {
            if (!oldHashTags.contains(updatedHashTag)) {
                addedHashTags.add(updatedHashTag);
            }
        }

        insertTagsWithRelation(id, addedHashTags);
        return new Pair<Long, Note>(id, getNodeById(id));
    }

    public Map<Long, Note> filterBy(String type, String value) {
        Map<Long, Note> resultMap = new HashMap<>();
        List<Long> noteIds = new ArrayList<>();
        String sqlSearchNotesIds = null;
        if (type.toLowerCase().equals("date")) {
            sqlSearchNotesIds = "SELECT id " +
                    "FROM notes " +
                    "WHERE created_at = ?";
        } else if (type.toLowerCase().equals("hashtag")) {
            sqlSearchNotesIds = "SELECT notes.id " +
                    "FROM notes " +
                    "INNER JOIN notes_tags_relations " +
                    "ON notes.id = notes_tags_relations.note_id " +
                    "INNER JOIN tags " +
                    "ON notes_tags_relations.tag_id = tags.id " +
                    "WHERE tags.tag_name = ?";
        }
        try (PreparedStatement pstmt = createPstmt(sqlSearchNotesIds)) {
            if (type.toLowerCase().equals("date")) {
                pstmt.setDate(1, Date.valueOf(value));
            } else {
                pstmt.setString(1, value);
            }

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                noteIds.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        for (long id : noteIds) {
            resultMap.put(id, getNodeById(id));
        }
        return resultMap;
    }

    public Map<Long, Note> getNotesWithPhraseInTitleOrText(String phrase) {
        Map<Long, Note> resultNotesMap = new HashMap<>();
        List<Long> resultNotesIds = new ArrayList<>();
        String sqlSearchId = "SELECT id " +
                "FROM notes " +
                "WHERE title LIKE ? OR text LIKE ?";
        try (PreparedStatement pstmt = createPstmt(sqlSearchId)) {
            pstmt.setString(1, "%" + phrase + "%");
            pstmt.setString(2, "%" + phrase + "%");
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                resultNotesIds.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        for (long id : resultNotesIds) {
            resultNotesMap.put(id, getNodeById(id));
        }
        return resultNotesMap;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private PreparedStatement createPstmt(String sql) throws SQLException {
        Connection conn = connect();
        return conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }

    private Statement createStmt() throws SQLException {
        Connection conn = connect();
        return conn.createStatement();
    }

    private long getIdFromPstmtUpdateOperation(PreparedStatement pstmt) throws SQLException {
        long id = -1;
        int affectedRows = pstmt.executeUpdate();
        if (affectedRows > 0) {
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    id = rs.getLong(1);
                    System.out.println("got id " + id);
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
        return id;
    }

    private void createTableIfItDoesntExist(Statement stmt) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS notes (" +
                "id         SERIAL PRIMARY KEY," +
                "title      VARCHAR(50)," +
                "text       VARCHAR(1000) NOT NULL," +
                "created_at TIMESTAMP NOT NULL" +
                ");\n" +
                "CREATE TABLE IF NOT EXISTS tags (" +
                "id       SERIAL PRIMARY KEY," +
                "tag_name VARCHAR(50) UNIQUE NOT NULL" +
                ");\n" +
                "CREATE TABLE IF NOT EXISTS notes_tags_relations (" +
                "note_id INT NOT NULL," +
                "tag_id  INT NOT NULL," +
                "PRIMARY KEY (note_id, tag_id)," +
                "FOREIGN KEY (note_id)" +
                "  REFERENCES notes (id)" +
                "    ON DELETE CASCADE," +
                "FOREIGN KEY (tag_id)" +
                "  REFERENCES tags (id)" +
                "    ON DELETE CASCADE" +
                ");";
        stmt.executeUpdate(sql);
    }

    private void deleteUnusedTags() {
        String sqlCheckAndDeleteTags = "DELETE FROM tags " +
                "WHERE id NOT IN (SELECT tag_id FROM notes_tags_relations)";
        try (Statement stmt = createStmt()) {
            stmt.executeUpdate(sqlCheckAndDeleteTags);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private void insertTagsWithRelation(long noteId, Set<String> hashTags) {
        List<Long> hashTagsIds = new ArrayList<>();
        String sqlInsertInTags = "INSERT INTO tags(tag_name)" +
                "VALUES(?)";
        // Добавим только те теги в таблицу tags, которых в ней не было
        for (String hashTag : hashTags) {
            String sqlSelect = "SELECT id FROM tags WHERE tag_name = ?";
            long hashTagIdFromSelect = -1;
            try (PreparedStatement pstmt = createPstmt(sqlSelect)
            ) {
                pstmt.setString(1, hashTag);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    hashTagIdFromSelect = rs.getLong("id");
                }
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
            if (hashTagIdFromSelect != -1) {
                hashTagsIds.add(hashTagIdFromSelect);
                continue;
            }
            try (PreparedStatement pstmt = createPstmt(sqlInsertInTags)
            ) {
                pstmt.setString(1, hashTag);
                hashTagsIds.add(getIdFromPstmtUpdateOperation(pstmt));
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }

        // Связываем теги с заметками
        String sqlInsertInRelation = "INSERT INTO notes_tags_relations(note_id, tag_id)" +
                "VALUES(?,?)";
        for (long hashTagId : hashTagsIds) {
            try (PreparedStatement pstmt = createPstmt(sqlInsertInRelation)
            ) {
                pstmt.setLong(1, noteId);
                pstmt.setLong(2, hashTagId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
