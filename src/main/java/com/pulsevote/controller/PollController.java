package com.pulsevote.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import com.pulsevote.auth.AuthInterceptor;
import com.pulsevote.auth.AuthUser;
import com.pulsevote.model.CreatePollRequest;
import com.pulsevote.model.Poll;
import com.pulsevote.model.VoteRequest;
import com.pulsevote.service.PollService;

// REST endpoints. Receives HTTP requests from React and returns JSON.
@RestController
@RequestMapping("/api")
public class PollController {

    private final PollService pollService;

    public PollController(PollService pollService) {
        this.pollService = pollService;
    }

    // GET /api/polls  -> list all polls
    @GetMapping("/polls")
    public List<Poll> getAllPolls() {
        return pollService.getAllPolls();
    }

    // GET /api/polls/5  -> one poll with its options
    @GetMapping("/polls/{id}")
    public Poll getPoll(@PathVariable long id) {
        return pollService.getPoll(id);
    }

    // POST /api/polls  -> create a poll (201 Created). Login required.
    @PostMapping("/polls")
    public ResponseEntity<Poll> createPoll(@RequestBody CreatePollRequest request, HttpServletRequest http) {
        AuthUser user = AuthInterceptor.requireUser(http);
        Poll createdPoll = pollService.createPoll(request, user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPoll);
    }

    // POST /api/polls/5/vote  -> save a vote (201 Created). Login required, one vote per user per poll.
    @PostMapping("/polls/{id}/vote")
    public ResponseEntity<Map<String, Object>> vote(@PathVariable long id, @RequestBody VoteRequest request,
                                                    HttpServletRequest http) {
        AuthUser user = AuthInterceptor.requireUser(http);
        Poll results = pollService.vote(id, user.id(), request);
        Map<String, Object> body = Map.of("message", "Thank you! Your vote has been recorded.", "results", results);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // GET /api/polls/5/results  -> vote counts and percentages
    @GetMapping("/polls/{id}/results")
    public Poll getResults(@PathVariable long id) {
        return pollService.getResults(id);
    }

    // GET /api/polls/5/my-vote  -> { "optionId": 3 } or { "optionId": null }. Public: not logged in = null.
    @GetMapping("/polls/{id}/my-vote")
    public Map<String, Object> getMyVote(@PathVariable long id, HttpServletRequest http) {
        AuthUser user = AuthInterceptor.currentUser(http);
        Map<String, Object> body = new HashMap<>();
        body.put("optionId", user == null ? null : pollService.getMyVote(id, user.id()));
        return body;
    }

    // GET /api/stats  -> { "totalPolls": 5, "totalVotes": 30 }
    @GetMapping("/stats")
    public Map<String, Integer> getStats() {
        return pollService.getStats();
    }
}
