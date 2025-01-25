package org.gsobko.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;

public record EmailMessage(
        UUID id,
        Long imapUid,
        String messageId,
        String from,
        String to,
        String cc,
        String subject,
        String text,
        String html,
        List<String> attachments,
        Instant sentDate,
        Instant createdDate) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private Long imapUid;
        private String messageId;
        private String from;
        private String to;
        private String cc;
        private String subject;
        private String text;
        private String html;
        private List<String> attachments = emptyList();
        private Instant sentDate;
        private Instant createdDate;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withImapUid(long imapUid) {
            this.imapUid = imapUid;
            return this;
        }

        public Builder withMessageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder withFrom(String from) {
            this.from = from;
            return this;
        }

        public Builder withTo(String to) {
            this.to = to;
            return this;
        }

        public Builder withCc(String cc) {
            this.cc = cc;
            return this;
        }

        public Builder withSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder withText(String text) {
            this.text = text;
            return this;
        }

        public Builder withHtml(String html) {
            this.html = html;
            return this;
        }

        public Builder withAttachments(List<String> attachments) {
            this.attachments = attachments;
            return this;
        }

        public Builder withSentDate(Instant sentDate) {
            this.sentDate = sentDate;
            return this;
        }

        public Builder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public EmailMessage build() {
            return new EmailMessage(
                    id,
                    imapUid,
                    messageId,
                    from,
                    to,
                    cc,
                    subject,
                    text,
                    html,
                    attachments,
                    sentDate,
                    createdDate
            );
        }
    }

}

