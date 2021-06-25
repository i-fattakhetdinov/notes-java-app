package com.notes.model;

import java.sql.Date;
import java.util.HashSet;
import java.util.Set;

public class Note {
    private String title;
    private String text;
    private Date createdAt;
    private Set<String> hashTags;

    public Note() {
        createdAt = new Date(System.currentTimeMillis());
        hashTags = new HashSet<>();
    }

    public Note(String title, String text) {
        this.title = title;
        this.text = text;
        createdAt = new Date(System.currentTimeMillis());
        hashTags = new HashSet<>();
    }

    public Note(String title, String text, Date createdAt, Set<String> hashTags) {
        this.title = title;
        this.text = text;
        this.createdAt = createdAt;
        this.hashTags = hashTags;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Set<String> getHashTags() {
        return hashTags;
    }

    public void setHashTags(Set<String> hashTags) {
        this.hashTags = hashTags;
    }
}
