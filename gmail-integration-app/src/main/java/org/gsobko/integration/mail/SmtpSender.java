package org.gsobko.integration.mail;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Properties;
import java.util.function.Supplier;

public class SmtpSender {
    public static final String MESSAGE_ID_PREFIX = "GmailIntegrationApp";
    private static final Logger logger = LoggerFactory.getLogger(SmtpSender.class);

    private final String senderEmail;
    private final Supplier<Session> sessionProvider;

    public SmtpSender(String senderEmail, String password, String hostname, int port) {
        this(senderEmail,
                new SmtpSessionProvider(
                        createSmtpProperties(hostname, port),
                        new PasswordAuthentication(senderEmail, password)));
    }

    public SmtpSender(String senderEmail, Supplier<Session> sessionProvider) {
        this.senderEmail = senderEmail;
        this.sessionProvider = sessionProvider;
    }

    public void sendEmail(String messageId, String toAddress, String subject, String body) {
        Session session = sessionProvider.get();
        try {
            Message message = createMessage(messageId, toAddress, subject, body, session);
            Transport.send(message);
            logger.info("Email {} sent successfully to {}", messageId, toAddress);
        } catch (MessagingException e) {
            logger.error("Error sending email", e);
            throw new IllegalStateException(e);
        }
    }


    private Message createMessage(String messageId, String toAddresses, String subject, String body, Session session) throws MessagingException {
        Message message = new MimeMessage(session);
        String messageIdFullyQualified = "%s:%s".formatted(MESSAGE_ID_PREFIX, messageId);
        message.addHeader("Message-Id", messageIdFullyQualified);
        message.setFrom(new InternetAddress(senderEmail));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddresses));
        message.setSubject(subject);
        message.setText(body);
        message.setSentDate(new Date()); // XXX clock !
        return message;
    }


    private static Properties createSmtpProperties(String hostname, int port) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", hostname);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        return properties;
    }

    private static class SmtpSessionProvider implements Supplier<Session> {
        private final Properties properties;
        private final PasswordAuthentication auth;

        public SmtpSessionProvider(Properties properties, PasswordAuthentication auth) {
            this.properties = properties;
            this.auth = auth;
        }

        @Override
        public Session get() {
            return Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return auth;
                }
            });
        }
    }

}
