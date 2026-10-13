package com.pulsevote.model;

import java.util.List;

// JSON sent by React when creating a poll:
// { "question": "...", "category": "Technology", "options": ["A", "B"] }
public class CreatePollRequest {

    private String question;
    private String category;
    private List<String> options;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
}
