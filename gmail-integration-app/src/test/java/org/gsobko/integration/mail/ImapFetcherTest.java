package org.gsobko.integration.mail;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.icegreen.greenmail.util.GreenMailUtil.createTextEmail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

class ImapFetcherTest {

    public static final int INITIAL_DEPTH_LIMIT = 10;

    GreenMail greenMail = new GreenMail(ServerSetup.IMAPS.dynamicPort());
    GreenMailUser user;
    ImapFetcher imapFetcher;


    @BeforeEach
    void setUp() throws Exception {
        user = greenMail.setUser("aaa@bbb", "aaa@bbb", "pass");
        greenMail.start();
        imapFetcher = new ImapFetcher("aaa@bbb", "pass", "INBOX", "localhost", greenMail.getImaps().getPort(), true, INITIAL_DEPTH_LIMIT);
    }

    @Test
    void should_fetch_nothing() {
        Consumer<FetchedEmail> consumer = mock(Consumer.class);

        imapFetcher.fetchEmailsSinceUid(OptionalLong.empty(), consumer);

        verify(consumer, never()).accept(any(FetchedEmail.class));
    }

    @Test
    void should_fetch_a_email() {
        // given
        Consumer<FetchedEmail> consumer = mock(Consumer.class);
        user.deliver(createTextEmail("aaa@bbb", "ccc@ddd", "subj", "Hello", greenMail.getImaps().getServerSetup()));

        // when
        imapFetcher.fetchEmailsSinceUid(OptionalLong.empty(), consumer);

        // then
        verify(consumer).accept(argThat(fetchedEmail ->
                fetchedEmail.text().get().equals("Hello")
                        && fetchedEmail.to().equals("aaa@bbb")
                        && fetchedEmail.subject().equals("subj")
                        && fetchedEmail.from().equals("ccc@ddd")
        ));
    }

    @Test
    void should_not_re_fetch_same_email_if_you_provide_its_uid() {
        // given
        Consumer<FetchedEmail> consumer = mock(Consumer.class);
        user.deliver(createTextEmail("aaa@bbb", "ccc@ddd", "subj", "Hello", greenMail.getImaps().getServerSetup()));
        imapFetcher.fetchEmailsSinceUid(OptionalLong.empty(), consumer);
        FetchedEmail firstFetchedEmail = getSingleEmailFetchedBy(consumer);

        // when
        Consumer<FetchedEmail> consumer2 = mock(Consumer.class);
        user.deliver(createTextEmail("aaa@bbb", "ccc@ddd", "subj2", "Hello again", greenMail.getImaps().getServerSetup()));
        imapFetcher.fetchEmailsSinceUid(OptionalLong.of(firstFetchedEmail.imapUid()), consumer2);

        // then
        FetchedEmail fetchedEmail2 = getSingleEmailFetchedBy(consumer2);
        assertThat(fetchedEmail2.subject()).isEqualTo("subj2");
    }


    @Test
    void should_not_fetch_more_emails_initially_than_initial_depth_limit() {
        // given
        Consumer<FetchedEmail> consumer = mock(Consumer.class);
        List<MimeMessage> messages = createNumberOfMessages(100);
        messages.forEach(user::deliver);

        // when
        imapFetcher.fetchEmailsSinceUid(OptionalLong.empty(), consumer);

        // then
        verify(consumer, times(INITIAL_DEPTH_LIMIT)).accept(any(FetchedEmail.class));
    }

    @Test
    void should_fetch_email_with_multiple_to_and_cc() throws Exception {
        // given
        Consumer<FetchedEmail> consumer = mock(Consumer.class);
        MimeMessage textEmail = createTextEmail("aaa@bbb,ccc@bbb", "ccc@ddd", "subj", "body", greenMail.getImaps().getServerSetup());
        textEmail.setRecipients(Message.RecipientType.CC, "eee@bbb,fff@bbb");
        user.deliver(textEmail);

        // when
        imapFetcher.fetchEmailsSinceUid(OptionalLong.empty(), consumer);

        // then
        FetchedEmail fetchedEmail = getSingleEmailFetchedBy(consumer);
        assertThat(fetchedEmail.to()).isEqualTo("aaa@bbb, ccc@bbb");
        assertThat(fetchedEmail.cc()).isEqualTo("eee@bbb, fff@bbb");

    }

    private List<MimeMessage> createNumberOfMessages(int number) {
        return IntStream.range(0, number)
                .mapToObj(i -> createTextEmail("aaa@bbb", "ccc@ddd", "subj%d".formatted(i), "Hello again", greenMail.getImaps().getServerSetup()))
                .toList();
    }


    private static FetchedEmail getSingleEmailFetchedBy(Consumer<FetchedEmail> consumer) {
        ArgumentCaptor<FetchedEmail> captor = ArgumentCaptor.forClass(FetchedEmail.class);
        verify(consumer).accept(captor.capture());
        return captor.getValue();
    }
}