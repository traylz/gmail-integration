package org.gsobko.resource;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import jakarta.mail.MessagingException;
import org.gsobko.integration.mail.SmtpSender;
import org.gsobko.model.EmailMessage;
import org.gsobko.repo.MailRepo;

import java.time.Instant;
import java.util.List;

public class MailResource {
    public static final int DEFAULT_LIMIT = 100;
    private final MailRepo mailRepo;
    private final SmtpSender sender;

    public MailResource(MailRepo mailRepo, SmtpSender sender) {
        this.mailRepo = mailRepo;
        this.sender = sender;
    }

    public void fetchEmails(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(DEFAULT_LIMIT);
        Instant start = getQueryParameterInstant(ctx, "start");
        Instant end = getQueryParameterInstant(ctx, "end");
        List<EmailMessage> result = mailRepo.fetchAllInInterval(start, end, limit);
        ctx.json(result);
    }

    private static Instant getQueryParameterInstant(Context ctx, String param) {
        String paramStr = require(ctx.queryParam(param), param);
        return Instant.parse(paramStr);
    }

    public void sendEmail(Context ctx) {
        SendMailRequest sendMailRequest = ctx.bodyAsClass(SendMailRequest.class);
        String messageId = require(sendMailRequest.messageId(), "messageId");
        String toAddress = require(sendMailRequest.to(), "toAddress");
        String subject = require(sendMailRequest.subject(), "subject");
        String body = require(sendMailRequest.body(), "body");
        sender.sendEmail(messageId, toAddress, subject, body);
        ctx.status(HttpStatus.OK);
    }

    private static <T> T require(T value, String param) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter %s missing".formatted(param));
        }
        return value;
    }
}
