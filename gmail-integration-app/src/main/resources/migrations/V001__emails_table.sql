CREATE TABLE emails
(
    id           UUID PRIMARY KEY,
    imap_uid     INTEGER,
    message_id   VARCHAR,
    mail_from    VARCHAR,
    mail_to      VARCHAR,
    mail_cc      VARCHAR,
    subject      VARCHAR,
    body_text    VARCHAR,
    body_html    VARCHAR,
    attachments  VARCHAR,
    sent_date    TIMESTAMP WITH TIME ZONE,
    created_date TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX emails_imap_uid_unique_idx ON emails (imap_uid);

CREATE INDEX emails_create_date_idx ON emails (created_date);