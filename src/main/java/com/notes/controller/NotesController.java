package com.notes.controller;

import com.notes.Pair;
import com.notes.model.Note;
import com.notes.repository.NotesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class NotesController {

    @Autowired
    NotesRepository notesRepository;

    @GetMapping("/notes")
    public Map<Long, Note> getNotes() {
        return notesRepository.getAllNotes();
    }

    @PostMapping("/notes")
    public Pair<Long, Note> addNote(@RequestBody Note note) {
        return notesRepository.addNote(note);
    }

    @GetMapping("/notes/{id}")
    public Note getNoteById(@PathVariable(value = "id") int id) {
        return notesRepository.getNodeById(id);
    }

    @PutMapping("/notes/{id}")
    public Pair<Long, Note> updateNote(@PathVariable(value = "id") long id, @RequestBody Note updatedNote) {
        return notesRepository.updateNote(id, updatedNote);
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<?> deleteNote(@PathVariable(value = "id") int id) {
        notesRepository.deleteNote(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/notes/filter")
    public Map<Long, Note> filterNotes(@RequestParam Map<String, String> params) {
        String type = params.get("type");
        String value = params.get("value");
        return notesRepository.filterBy(type, value);
    }

    @GetMapping("/notes/search")
    public Map<Long, Note> searchPhrase(@RequestParam(name = "phrase") String phrase) {
        return notesRepository.getNotesWithPhraseInTitleOrText(phrase);
    }
}
