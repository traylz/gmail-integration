![CI](https://github.com/traylz/gmail-integration/actions/workflows/ci.yml/badge.svg)

# Description
This application integrates with Gmail to send simple text message.  
Sending is done via SMTP.  
Receiving done via IMAP - the folder is periodically polled (using UID as a "checkpoint").  
Two endpoints are exposed - to send and to fetch.  
Web server - Javalin. Database interactions - pure JDBC. No DI framework.  
Apart from unit tests there are [functional tests](./gmail-integration-app/src/test/java/org/gsobko/resource/MailResourceTest.java) that use GreenMail embedded mail server to send/receive mail.  
In general the same code applicable for any SMTP/IMAP integration with user-pass auth.

# How to run
1. Set up your account to provide IMAP/SMTP app_password
   * Application passwords
Create password here:
https://myaccount.google.com/apppasswords  
Password should be a 16 symbol code like `aaaabbbbccccdddd` without spaces
   * Check IMAP enabled here:
https://mail.google.com/mail/u/0/#settings/fwdandpop
2. Put email/password in app\.properties
put in app properties here [gmail-integration-app/src/main/resources/app.properties](./gmail-integration-app/src/main/resources/app.properties)
3. Run from command line
`./gradlew run`  
Here you have it!
4. The database used is in-mem H2, to change to Postgres - change db parameters in app.properties section

## Endpoints
There are two endpoints: Send email and Get emails

### Send email
`POST /mail`

with request body
Request:
```json
{
  "requestId": "Request Id will be used to form a X-Request-ID header for the email",
  "to": "email1@somewhere, email2@somewhere",
  "subject" : "hello there",
  "body": "This is body of a message"
}
```
Response codes
* Status `200` - mail was sent
* Status `400` - invalid input
* Status `500` - internal error occurred

### Fetch emails
* `GET /mails?start={start}&end={end}[&limit=200]`  
Parameters `start` and `end` are required and should be provided in ISO format like `2024-01-21T23:50:41Z`.  
*N.B.!* timestamp here represents created_date (meaning the email was written to database).
Parameter `limit` is optional and defaults to `100`

Response will be a JSON array of mails from database, example
```json
[
   {
      "id": "9f7dd916-fef0-41a6-a058-dc99656d689b",
      "imapUid": 1,
      "messageId": "<215638041.0.1737807108844@127.0.0.1>",
      "from": "aaa@bbb",
      "to": "ccc@bbb",
      "cc": "",
      "subject": "Subj1",
      "text": "Body test",
      "html": "",
      "attachments": ["attachment.pdf"],
      "sentDate": "2025-01-25T12:11:48Z",
      "createdDate": "2025-01-25T12:11:48.928641Z"
   }
]
```

Response codes
* Status `200` - mail was sent
* Status `400` - invalid input
* Status `500` - internal error occurred

### Application Properties
* `server.port` - local port to run server
* `db.url`, `db.username`, `db.password`, `db.pool.size` - db connection parameters
* `gmail.email`, `gmail.app_password` - credentials to use to connect (See "How to run" section)
* `gmail.folder` - folder to sync
* `gmail.initial_max_depth` - as the mailbox might be huge, this limits initial fetch to that number
* `gmail.imap.host`, `gmail.imap.port`, `gmail.smtp.host`, `gmail.smtp.port` - hosts/ports for imap/smtp
* `gmail.imap.disable_ssl_checks` - this should always be false for prod, only used for functional tests to connect to embedded IMAP server.

### Database
Database is migrated using Flyway on application start. Database schema is the following:
```sql
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
)
```

# Findings/considerations
Below are some findings and considerations that might be useful to one doing the integration with mail.

### IMAP UID
To fetch "new" mail you need to somehow have a "checkpoint" from which to consider emails to be new. 
There are several candidates that seem plausible for the purpose,but do not work - seqnum (sequence number changes when you delete an email from the folder), some of the date fields (might be backdated if you move emails between folders).

But, there is an [IMAP UID](https://www.rfc-editor.org/rfc/rfc3501#section-2.3.1.1) for rescue - it is a monotonously increasing sequence that is immutable for a message.

So here I have used it as a "checkpoint" to understand from which email to fetch new emails.
### IMAP performance
If you try to fetch all mails from a folder using `Message[] getMessages()` method - it will hang for really long time, so instead of doing that I am first fetching UIDs to fetch and then go fetching mail-by-mail using UID.

Also, because of that I've added a parameter `gmail.initial_max_depth` to control the max depth that you want to fetch on initial connect.

To improve performance one can look into imap connection pooling and doing requests in parallel.

### IMAP Testing
Existing libs for IMAP integration (i.e. jakarta-mail) are horrendous in terms of testability.

Initially I have tired to mock stuff for unit tests, but that looked so ugly that I switched to embedded IMAP server (GreenMail) for testing.
The same embedded IMAP server is used for Functional tests.

### Body type & multipart/alternative
Turned out that large part of the mails will contain both `text/html` and `text/plain` representations.
In this case we will receive a body with mime type `multipart/alternative` and both `text/html` and `text/plain` representation.

I've decided to save both of them - in corresponding `html` and `text` fields of message, as 

Alternative solution is to save content-type and content. 
But as you still need to handle these content-types differently, two separate fields does not seem a bad idea (discussible. Anyway, the switch should be straightforward if required)


### Idempotency
SMTP does not have idempotency out of the box, I have added stamping a custom header X-Request-ID based on requestId from request. Gmail actually will allow you sending multiple messages with same Message-ID.
The proper approach to idempotency would require fetching sent emails and looking for our X-Request-ID.

See: "Next steps: Idempotency on send + extra model for send requests"

### app_passwords and security
App passwords while serve a good stating point should not be used in proper production.
The proper way is to use OAuth2, and add token to IMAP/SMTP connection properties.

See: "Next steps: OAuth2"

### Parallel run
You can run two nodes in parallel on the same database. This might cause scheduled fetcher to fetch and try to save the same email, this will be handled by unique index on imapUid. I will generate some noise in logs, but will work correctly still.

The proper approach would be to either extract fetching to a separate node or add coordination.

# Next steps
### Oauth 2
To make the application production ready we would need to switch from app_passwords as outdated tech to OAuth2.

Here are some docs how to make it work with IMAP/SMTP https://developers.google.com/gmail/imap/xoauth2-protocol

### Reply-To
To reply to emails we would need to add `In-Reply-To` header and set `Message-ID` to the message Id that we reply to.

Message-ID is already saved in emails table, so in API I would expose it as an extra field `replyToMessageId` field.

### Idempotency on send + extra model for send requests
As mentioned above, SMTP does not have idempotency out of the box, so what we can do:
1. Add an extra model to handle idempotency in simple scenarios (like mail sent successfully and we receive another request to send with same messageId).
2. Set `Message-ID` on the outgoing email based on `messageId` in request.
3. In edge cases when we did not receive a proper response from SMTP transport we should do a FETCH via IMAP to find a message with our Message-ID and based on that we can do;
4. Alternative path to explore - is to look into Gmail API (rest API) to create Draft first and then send it. 

### Multiple accounts
This is a pretty obvious thing to do. Would require adding new model for Account (and probably better to have OAuth2 integrated to avoid storing passwords in db).
One thing to do in such a case - change unique index from imap_uid to (account_id, imap_uid).