package com.pulsevote.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pulsevote.exception.ApiException;
import com.pulsevote.model.CreatePollRequest;
import com.pulsevote.model.Poll;
import com.pulsevote.model.PollOption;
import com.pulsevote.model.VoteRequest;
import com.pulsevote.repository.PollRepository;

// Business rules: validation, percentage calculation, transactions.
@Service
public class PollService {

    // Must match the list in frontend/src/services/categories.js
    private static final List<String> CATEGORIES =
            List.of("Technology", "Education", "Entertainment", "Sports", "Lifestyle", "Other");

    private final PollRepository pollRepository;

    public PollService(PollRepository pollRepository) {
        this.pollRepository = pollRepository;
    }

    public List<Poll> getAllPolls() {
        return pollRepository.findAllPolls();
    }

    // Poll + options (each option also carries its votes and percentage)
    public Poll getPoll(long pollId) {
        Poll poll = pollRepository.findPollById(pollId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Poll not found."));

        List<PollOption> options = pollRepository.findOptionsWithVoteCounts(pollId);

        // Total votes = sum of the votes of all options
        int total = 0;
        for (PollOption option : options) {
            total += option.getVoteCount();
        }

        // Percentage = option votes / total votes * 100, rounded to 1 decimal.
        // If total is 0 we use 0 so we never divide by zero.
        for (PollOption option : options) {
            double percentage = 0;
            if (total > 0) {
                percentage = Math.round(option.getVoteCount() * 1000.0 / total) / 10.0;
            }
            option.setPercentage(percentage);
        }

        poll.setTotalVotes(total);
        poll.setOptions(options);
        return poll;
    }

    public Poll getResults(long pollId) {
        return getPoll(pollId);
    }

    // @Transactional: the poll and ALL its options are saved together.
    // If anything fails, MySQL rolls everything back (no half-saved poll).
    @Transactional
    public Poll createPoll(CreatePollRequest request, long userId) {
        validateNewPoll(request);

        long pollId = pollRepository.insertPoll(request.getQuestion().trim(), request.getCategory(), userId);
        for (String optionText : request.getOptions()) {
            pollRepository.insertOption(pollId, optionText.trim());
        }
        return getPoll(pollId);
    }

    // Saves one vote for a logged-in user, then returns the updated results.
    // Rule: one vote per user per poll (also enforced by the UNIQUE key in MySQL).
    public Poll vote(long pollId, long userId, VoteRequest request) {
        if (request == null || request.getOptionId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please select an option to vote for.");
        }
        long optionId = request.getOptionId();

        if (!pollRepository.pollExists(pollId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Poll not found.");
        }
        if (!pollRepository.optionBelongsToPoll(pollId, optionId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That option does not belong to this poll.");
        }

        if (pollRepository.findVotedOption(pollId, userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "You have already voted in this poll.");
        }

        int rows;
        try {
            rows = pollRepository.insertVote(pollId, optionId, userId);
        } catch (DuplicateKeyException e) { // two requests at once: the database key still stops the second
            throw new ApiException(HttpStatus.CONFLICT, "You have already voted in this poll.");
        }
        if (rows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Your vote could not be saved.");
        }
        return getPoll(pollId);
    }

    // The option the user voted for in this poll, or null
    public Long getMyVote(long pollId, long userId) {
        return pollRepository.findVotedOption(pollId, userId).orElse(null);
    }

    public Map<String, Integer> getStats() {
        return Map.of("totalPolls", pollRepository.countPolls(), "totalVotes", pollRepository.countVotes());
    }

    // ---------- validation ----------
    private void validateNewPoll(CreatePollRequest request) {
        if (request == null) {
            throw badRequest("Request body is missing.");
        }

        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.isEmpty()) {
            throw badRequest("Poll question is required.");
        }
        if (question.length() > 255) {
            throw badRequest("Poll question must be 255 characters or fewer.");
        }

        if (request.getCategory() == null || !CATEGORIES.contains(request.getCategory())) {
            throw badRequest("Please choose a valid category.");
        }

        List<String> options = request.getOptions();
        if (options == null || options.size() < 2 || options.size() > 4) {
            throw badRequest("A poll needs between 2 and 4 options.");
        }

        Set<String> seen = new HashSet<>();
        for (String option : options) {
            String text = option == null ? "" : option.trim();
            if (text.isEmpty()) {
                throw badRequest("Options cannot be empty.");
            }
            if (text.length() > 100) {
                throw badRequest("Each option must be 100 characters or fewer.");
            }
            if (!seen.add(text.toLowerCase())) {
                throw badRequest("Options must be different from each other.");
            }
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
