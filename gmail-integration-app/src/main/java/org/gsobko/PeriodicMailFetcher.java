package org.gsobko;

import org.gsobko.integration.mail.FetchedEmail;
import org.gsobko.integration.mail.ImapFetcher;
import org.gsobko.model.EmailMessage;
import org.gsobko.repo.MailRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PeriodicMailFetcher {
    private final Logger logger = LoggerFactory.getLogger(PeriodicMailFetcher.class);
    private final MailRepo repo;
    private final ImapFetcher imapFetcher;
    private final long pollPeriodSeconds;
    private final ScheduledExecutorService executor;


    public PeriodicMailFetcher(MailRepo repo, ImapFetcher imapFetcher, long pollPeriodSeconds) {
        this(repo, imapFetcher, pollPeriodSeconds, createScheduledService());
    }

    PeriodicMailFetcher(MailRepo repo, ImapFetcher imapFetcher, long pollPeriodSeconds, ScheduledExecutorService executor) {
        this.repo = repo;
        this.imapFetcher = imapFetcher;
        this.pollPeriodSeconds = pollPeriodSeconds;
        this.executor = executor;
    }

    private static ScheduledExecutorService createScheduledService() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("periodic-email-fetcher");
            return thread;
        });
    }


    public void start() {
        executor.scheduleWithFixedDelay(this::downloadNewMail, 0, pollPeriodSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void downloadNewMail() {
        try {
            OptionalLong maxImapUid = repo.maxImapUid();
            imapFetcher.fetchEmailsSinceUid(maxImapUid, email -> {
                try {
                    repo.save(toEmailModel(email));
                } catch (Exception e) {
                    logger.error("Could not save the fetched email {}", email.imapUid());
                }
            });
        } catch (Exception e) {
            logger.error("Could not fetch new emails", e);
        }
    }

    private EmailMessage toEmailModel(FetchedEmail email) {
        return EmailMessage.builder()
                .withId(UUID.randomUUID())
                .withImapUid(email.imapUid())
                .withMessageId(email.messageId())
                .withText(email.text().orElse(""))
                .withHtml(email.html().orElse(""))
                .withAttachments(email.attachments())
                .withSubject(email.subject())
                .withFrom(email.from())
                .withTo(email.to())
                .withCc(email.cc())
                .withSentDate(email.date())
                .withCreatedDate(Instant.now())
                .build();
    }
}
