package com.pulsevote.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.pulsevote.model.Poll;
import com.pulsevote.model.PollOption;

// The ONLY class that talks to MySQL. Every "?" in the SQL is filled safely
// by JdbcTemplate (parameterized query), which prevents SQL injection.
@Repository
public class PollRepository {

    private final JdbcTemplate jdbcTemplate;

    public PollRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Converts one database row into a Poll object
    private final RowMapper<Poll> pollMapper = (rs, rowNum) -> {
        Poll poll = new Poll();
        poll.setId(rs.getLong("id"));
        poll.setQuestion(rs.getString("question"));
        poll.setCategory(rs.getString("category"));
        poll.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        poll.setTotalVotes(rs.getInt("total_votes"));
        return poll;
    };

    // Converts one database row into a PollOption object
    private final RowMapper<PollOption> optionMapper = (rs, rowNum) -> {
        PollOption option = new PollOption();
        option.setId(rs.getLong("id"));
        option.setOptionText(rs.getString("option_text"));
        option.setVoteCount(rs.getInt("vote_count"));
        return option;
    };

    // All polls (newest first) with their total vote count.
    // LEFT JOIN keeps polls that have 0 votes.
    public List<Poll> findAllPolls() {
        String sql = "SELECT p.id, p.question, p.category, p.created_at, COUNT(v.id) AS total_votes "
                + "FROM polls p LEFT JOIN votes v ON v.poll_id = p.id "
                + "GROUP BY p.id, p.question, p.category, p.created_at "
                + "ORDER BY p.created_at DESC, p.id DESC";
        return jdbcTemplate.query(sql, pollMapper);
    }

    // One poll by id (Optional.empty() if it does not exist)
    public Optional<Poll> findPollById(long pollId) {
        String sql = "SELECT p.id, p.question, p.category, p.created_at, COUNT(v.id) AS total_votes "
                + "FROM polls p LEFT JOIN votes v ON v.poll_id = p.id "
                + "WHERE p.id = ? "
                + "GROUP BY p.id, p.question, p.category, p.created_at";
        return jdbcTemplate.query(sql, pollMapper, pollId).stream().findFirst();
    }

    // Options of a poll with the number of votes each option received
    public List<PollOption> findOptionsWithVoteCounts(long pollId) {
        String sql = "SELECT o.id, o.option_text, COUNT(v.id) AS vote_count "
                + "FROM poll_options o LEFT JOIN votes v ON v.option_id = o.id "
                + "WHERE o.poll_id = ? "
                + "GROUP BY o.id, o.option_text "
                + "ORDER BY o.id";
        return jdbcTemplate.query(sql, optionMapper, pollId);
    }

    // Inserts a poll (remembering who created it) and returns the id MySQL generated (AUTO_INCREMENT)
    public long insertPoll(String question, String category, long createdBy) {
        String sql = "INSERT INTO polls (question, category, created_by) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, question);
            ps.setString(2, category);
            ps.setLong(3, createdBy);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void insertOption(long pollId, String optionText) {
        jdbcTemplate.update("INSERT INTO poll_options (poll_id, option_text) VALUES (?, ?)", pollId, optionText);
    }

    // Returns how many rows were inserted (should be 1).
    // The UNIQUE (poll_id, user_id) key in MySQL rejects a second vote by the same user in the same poll
    // (Spring throws DuplicateKeyException), even if two requests arrive at exactly the same time.
    public int insertVote(long pollId, long optionId, long userId) {
        return jdbcTemplate.update("INSERT INTO votes (poll_id, option_id, user_id) VALUES (?, ?, ?)",
                pollId, optionId, userId);
    }

    // The option this user voted for in this poll, if any
    public Optional<Long> findVotedOption(long pollId, long userId) {
        return jdbcTemplate.query("SELECT option_id FROM votes WHERE poll_id = ? AND user_id = ?",
                (rs, i) -> rs.getLong("option_id"), pollId, userId).stream().findFirst();
    }

    public boolean pollExists(long pollId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM polls WHERE id = ?", Integer.class, pollId);
        return count != null && count > 0;
    }

    // Checks the option really belongs to this poll
    public boolean optionBelongsToPoll(long pollId, long optionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM poll_options WHERE id = ? AND poll_id = ?", Integer.class, optionId, pollId);
        return count != null && count > 0;
    }

    public int countPolls() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM polls", Integer.class);
        return count == null ? 0 : count;
    }

    public int countVotes() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM votes", Integer.class);
        return count == null ? 0 : count;
    }
}
