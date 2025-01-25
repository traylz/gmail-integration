![CI](https://github.com/traylz/gmail-integration/actions/workflows/ci.yml/badge.svg)

# Description
This application integrates with Gmail to send simple text message.  
Sending is done via SMTP.  
Receiving done via IMAP - the folder is periodically polled (using UID as a "checkpoint").  
Two endpoints are exposed - to send and to fetch.  
Web server - Javalin. Database interactions - pure JDBC. No DI framework.  
Apart from unit tests there are functional tests that use GreenMail embedded mail server to send/receive mail.  
In general the same code applicable for any SMTP/IMAP integration with user-pass auth.

# How to run
1. Set up your account to provide IMAP/SMTP app_password
   * Application passwords
Create password here:
https://myaccount.google.com/apppasswords
   * Check IMAP enabled here:
https://mail.google.com/mail/u/0/#settings/fwdandpop
2. Put email/password in app\.properties
put in app properties here [gmail-integration-app/src/main/resources/app.properties](./gmail-integration-app/src/main/resources/app.properties)
3. Run from command line
`./gradlew run`  
Here you have it!
4. The database used is in-mem H2, to change to Postgres - do a 


### Run as a package (uber-jar)

### Endpoints
There are two endpoints:


1. `POST /mail`

with request body
Request:
```json
{
  "messageId": "(Optional)Message Id to be used with a prefix as a Message-ID header",
  "to": "email1@somewhere, email2@somewhere",
  "subject" : "hello there",
  "body": "This is body of a message"
}
```
Response
* Status `200` - mail was created
* Status `400` - invalid input
* Status `500` - internal error occurred

2. `GET /mails?from={}&to={}&limit={}`

TBD

### Application Properties


### Database

# Findings/considerations
Below are some findings that might be useful
### IMAP UID

### IMAP performance

### IMAP Testing

### Body type & multipart/alternative

### Idempotency

### app_passwords and security
App passwords while serve a good stating point should not be used in proper production - the


# Next steps
### Oauth 2
To make the application production ready we would need to switch from app_passwords as outdated tech to [Read here]()
### Reply-To

### Idempotency on send + extra model

