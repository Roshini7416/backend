package com.pulsevote.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// A poll with its options. Spring converts this object to JSON automatically.
public class Poll {

    private Long id;
    private String question;
    private String category;
    private LocalDateTime createdAt;
    private int totalVotes;
    private List<PollOption> options = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getTotalVotes() { return totalVotes; }
    public void setTotalVotes(int totalVotes) { this.totalVotes = totalVotes; }

    public List<PollOption> getOptions() { return options; }
    public void setOptions(List<PollOption> options) { this.options = options; }
}
