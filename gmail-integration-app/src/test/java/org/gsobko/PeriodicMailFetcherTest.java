package org.gsobko;

import org.gsobko.integration.mail.FetchedEmail;
import org.gsobko.integration.mail.ImapFetcher;
import org.gsobko.repo.MailRepo;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class PeriodicMailFetcherTest {

    long POLL_PERIOD_SECONDS = 10;
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    MailRepo repo = mock(MailRepo.class);
    ImapFetcher imapFetcher = mock(ImapFetcher.class);
    PeriodicMailFetcher fetcher = new PeriodicMailFetcher(repo, imapFetcher, POLL_PERIOD_SECONDS, scheduler);


    @Test
    void should_schedule_periodic_task_with_fixed_delay_on_start() {
        // when
        fetcher.start();

        // then
        verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(POLL_PERIOD_SECONDS), eq(TimeUnit.SECONDS));
    }

    @Test
    void should_read_max_uid_and_call_fetcher_when_scheduled_task_is_run() {
        // given
        given(repo.maxImapUid()).willReturn(OptionalLong.of(2L));
        fetcher.start();

        // when
        scheduledTasksAreRun(scheduler);

        // then
        verify(imapFetcher).fetchEmailsSinceUid(eq(OptionalLong.of(2)), any());
    }

    @Test
    void should_save_fetched_email_to_repo() {
        // given
        given(repo.maxImapUid()).willReturn(OptionalLong.of(2L));
        Instant sentDate = Instant.now();
        givenEmailsInInbox(new FetchedEmail(
                "messageId123",
                4L,
                "from@aaa",
                "to@bbb, vvv@aaa",
                "",
                "subj",
                Optional.of("Body123"),
                Optional.of("html"),
                List.of("attachment1.pdf", "attachment2.pdf"),
                sentDate
        ));
        fetcher.start();

        // when
        scheduledTasksAreRun(scheduler);

        // then
        verify(repo).save(argThat(savedMessage ->
                savedMessage.imapUid() == 4
                        && savedMessage.messageId().equals("messageId123")
                        && savedMessage.from().equals("from@aaa")
                        && savedMessage.to().equals("to@bbb, vvv@aaa")
                        && savedMessage.cc().isEmpty()
                        && savedMessage.subject().equals("subj")
                        && savedMessage.text().equals("Body123")
                        && savedMessage.html().equals("html")
                        && savedMessage.attachments().equals(List.of("attachment1.pdf", "attachment2.pdf"))
                        && savedMessage.sentDate().equals(sentDate))
        );
    }

    @Test
    void should_still_save_second_email_if_repo_throws_on_first() {
        // given
        givenEmailsInInbox(
                someEmailWithUid(3),
                someEmailWithUid(4));
        doThrow(IllegalStateException.class)
                .when(repo)
                .save(argThat(m -> m.imapUid() == 3L));
        fetcher.start();

        // when
        scheduledTasksAreRun(scheduler);

        // then
        verify(repo, times(1)).save(argThat(saved -> saved.imapUid() == 3));
        verify(repo, times(1)).save(argThat(saved -> saved.imapUid() == 4));
    }

    private static FetchedEmail someEmailWithUid(long uid) {
        return new FetchedEmail(
                "messageId123" + uid,
                uid,
                "from@aaa",
                "to@bbb, vvv@aaa",
                "",
                "subj" + uid,
                Optional.of("Body123"),
                Optional.of("html"),
                List.of("attachment1.pdf", "attachment2.pdf"),
                Instant.now()
        );
    }

    private void givenEmailsInInbox(FetchedEmail... t) {
        doAnswer(i -> {
            Consumer consumer = i.getArgument(1, Consumer.class);
            Stream.of(t).forEach(consumer::accept);
            return null;
        }).when(imapFetcher).fetchEmailsSinceUid(any(), any());
    }

    private void scheduledTasksAreRun(ScheduledExecutorService scheduler) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleWithFixedDelay(captor.capture(), anyLong(), anyLong(), any(TimeUnit.class));
        captor.getValue().run();
    }
}