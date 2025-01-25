package org.gsobko.repo;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.gsobko.model.EmailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MailRepoTest {

    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    MailRepo mailRepo;

    @BeforeEach
    void setUp() {
        HikariDataSource dataSource = createH2DataSource();
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:/migrations")
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();

        mailRepo = new MailRepo(dataSource);
    }

    @Test
    void should_fetch_single_email_that_is_in_boundaries() {
        // given
        EmailMessage message = someEmail()
                .withImapUid(123L)
                .withCreatedDate(now)
                .build();
        mailRepo.save(message);

        // when
        List<EmailMessage> emailMessages = mailRepo.fetchAllInInterval(Instant.EPOCH, now, 100);

        // then
        assertThat(emailMessages).containsExactly(message);
    }

    @Test
    void should_notfetch_email_that_is_out_of_boundaries() {
        // given
        EmailMessage oldMessage = someEmail()
                .withImapUid(123L)
                .withCreatedDate(now.minusSeconds(10000))
                .build();
        EmailMessage newMessage = someEmail()
                .withImapUid(124L)
                .withCreatedDate(now)
                .build();
        mailRepo.save(oldMessage);
        mailRepo.save(newMessage);

        // when
        List<EmailMessage> emailMessages = mailRepo.fetchAllInInterval(now.minusSeconds(100), now, 100);

        // then
        assertThat(emailMessages).containsExactly(newMessage);
    }

    @Test
    void should_throw_duplicate_exception_when_trying_to_insert_messages_with_same_imap_uid() {
        // given
        mailRepo.save(someEmail()
                .withImapUid(123L)
                .build());

        // expect
        DuplicateModelException duplicateModelException = assertThrows(DuplicateModelException.class,
                () -> mailRepo.save(someEmail()
                        .withImapUid(123L).build()));
        assertThat(duplicateModelException.constraint()).isEqualTo(MailRepo.EMAILS_IMAP_UID_CONSTRAINT);

    }

    @Test
    void should_get_empty_max_imap_uid_on_empty_repo() {
        // when
        OptionalLong maxImapUid = mailRepo.maxImapUid();

        // then
        assertThat(maxImapUid).isEmpty();
    }

    @Test
    void should_get_max_imap_uid_from_multiple_messages() {
        // given
        mailRepo.save(someEmail().withImapUid(1).build());
        mailRepo.save(someEmail().withImapUid(9).build());
        mailRepo.save(someEmail().withImapUid(3).build());

        // when
        OptionalLong maxImapUid = mailRepo.maxImapUid();

        // then
        assertThat(maxImapUid).hasValue(9L);
    }

    private EmailMessage.Builder someEmail() {
        return EmailMessage.builder()
                .withId(UUID.randomUUID())
                .withImapUid(123L)
                .withMessageId("MsgId123")
                .withFrom("HelloThere")
                .withText("Text")
                .withHtml("Text")
                .withAttachments(List.of("attachment.pdf"))
                .withTo("TO?")
                .withCc("cc1;cc2")
                .withSubject("Subj")
                .withSentDate(now.minus(10, ChronoUnit.MINUTES))
                .withCreatedDate(now);
    }


    private HikariDataSource createH2DataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        String randomId = UUID.randomUUID().toString();
        dataSource.setJdbcUrl("jdbc:h2:mem:testdb%s;DB_CLOSE_DELAY=-1".formatted(randomId));
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(2);
        return dataSource;
    }
}