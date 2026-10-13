package com.pulsevote.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Adds the authentication tables/columns to an EXISTING pulsevote database when the backend starts.
// It never drops, deletes or rewrites any existing poll, option or vote.
// Every step first checks whether it is already done, so running it again is harmless.
// (A fresh database created with database.sql already contains all of this.)
@Component
public class SchemaMigration implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(SchemaMigration.class);

    private final JdbcTemplate jdbc;

    public SchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterPropertiesSet() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS users ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "full_name VARCHAR(100) NOT NULL, "
                + "email VARCHAR(254) NOT NULL, "
                + "password_hash VARCHAR(100) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uq_users_email (email)"
                + ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

        jdbc.execute("CREATE TABLE IF NOT EXISTS user_sessions ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "user_id INT NOT NULL, "
                + "token_hash CHAR(64) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "expires_at DATETIME NOT NULL, "
                + "UNIQUE KEY uq_sessions_token (token_hash), "
                + "INDEX idx_sessions_expires (expires_at), "
                + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                + ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

        // Old votes keep user_id = NULL (they were anonymous). MySQL allows many NULLs in a UNIQUE key,
        // so old data stays valid while every NEW vote must have a user and is unique per poll.
        if (!columnExists("votes", "user_id")) {
            // one statement, so the column and its foreign key are added together or not at all
            jdbc.execute("ALTER TABLE votes ADD COLUMN user_id INT NULL, ADD CONSTRAINT fk_votes_user "
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL");
            logger.info("Migration: added votes.user_id");
        }
        if (!indexExists("votes", "uq_votes_poll_user")) {
            jdbc.execute("ALTER TABLE votes ADD CONSTRAINT uq_votes_poll_user UNIQUE (poll_id, user_id)");
            logger.info("Migration: added UNIQUE (poll_id, user_id) on votes");
        }

        if (!columnExists("polls", "created_by")) {
            jdbc.execute("ALTER TABLE polls ADD COLUMN created_by INT NULL, ADD CONSTRAINT fk_polls_user "
                    + "FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL");
            logger.info("Migration: added polls.created_by");
        }
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        return n != null && n > 0;
    }

    private boolean indexExists(String table, String index) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, index);
        return n != null && n > 0;
    }
}
