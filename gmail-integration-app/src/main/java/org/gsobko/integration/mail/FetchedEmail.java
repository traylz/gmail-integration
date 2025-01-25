package org.gsobko.integration.mail;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record FetchedEmail(String messageId, long imapUid,
                           String from, String to, String cc,
                           String subject,
                           Optional<String> text,
                           Optional<String> html,
                           List<String> attachments,
                           Instant date) {
}
