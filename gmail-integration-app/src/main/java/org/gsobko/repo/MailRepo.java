package org.gsobko.repo;

import org.gsobko.model.EmailMessage;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gsobko.model.EmailMessage.builder;

public class MailRepo {
    public static final String EMAILS_IMAP_UID_CONSTRAINT = "EMAILS_IMAP_UID";

    private static final String INSERT_SQL = """
            INSERT INTO emails (id, imap_uid, message_id, mail_from, mail_to, mail_cc, subject, body_text, body_html, attachments, sent_date, created_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT * FROM emails
            WHERE created_date BETWEEN ? AND ?
            ORDER BY created_date DESC LIMIT ?
            """;

    private static final String MAX_UID_SQL = "SELECT MAX(imap_uid) FROM emails";
    public static final String ATTACHMENTS_SEPARATOR = ";";

    private final DataSource dataSource;

    public MailRepo(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void save(EmailMessage email) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setObject(1, email.id());
            ps.setLong(2, email.imapUid());
            ps.setString(3, email.messageId());
            ps.setString(4, email.from());
            ps.setString(5, email.to());
            ps.setString(6, email.cc());
            ps.setString(7, email.subject());
            ps.setString(8, email.text());
            ps.setString(9, email.html());
            ps.setString(10, joinAttachmentList(email.attachments()));
            ps.setTimestamp(11, Timestamp.from(email.sentDate()));
            ps.setTimestamp(12, Timestamp.from(email.createdDate()));

            ps.executeUpdate();
        } catch (SQLException e) {
            if (e instanceof SQLIntegrityConstraintViolationException && e.getMessage().toUpperCase().contains(EMAILS_IMAP_UID_CONSTRAINT)) {
                throw new DuplicateModelException(EMAILS_IMAP_UID_CONSTRAINT, e);
            }
            throw new IllegalStateException("Failed to insert email", e);
        }

    }


    public List<EmailMessage> fetchAllInInterval(Instant from, Instant to, int limit) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {

            ps.setTimestamp(1, Timestamp.from(from));
            ps.setTimestamp(2, Timestamp.from(to));
            ps.setInt(3, limit);

            List<EmailMessage> emails = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    emails.add(mapToEmail(rs));
                }
                return emails;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch emails in interval", e);
        }
    }

    public OptionalLong maxImapUid() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(MAX_UID_SQL)) {
            try (ResultSet resultSet = ps.executeQuery()) {
                if (resultSet.first()) {
                    long value = resultSet.getLong(1);
                    if (!resultSet.wasNull()) {
                        return OptionalLong.of(value);
                    }
                }
                return OptionalLong.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get max imap uid", e);
        }
    }

    private static EmailMessage mapToEmail(ResultSet rs) throws SQLException {
        return builder()
                .withId(UUID.fromString(rs.getString("id")))
                .withImapUid(rs.getLong("imap_uid"))
                .withMessageId(rs.getString("message_id"))
                .withFrom(rs.getString("mail_from"))
                .withTo(rs.getString("mail_to"))
                .withCc(rs.getString("mail_cc"))
                .withSubject(rs.getString("subject"))
                .withText(rs.getString("body_text"))
                .withHtml(rs.getString("body_html"))
                .withAttachments(splitAttachmentList(rs.getString("attachments")))
                .withSentDate(rs.getTimestamp("sent_date").toInstant())
                .withCreatedDate(rs.getTimestamp("created_date").toInstant())
                .build();
    }


    private static String joinAttachmentList(List<String> attachments) {
        return String.join(ATTACHMENTS_SEPARATOR, attachments);
    }

    private static List<String> splitAttachmentList(String attachments) {
        return Stream.of(attachments.split(ATTACHMENTS_SEPARATOR))
                .filter(str -> !str.isEmpty()).toList();
    }
}
