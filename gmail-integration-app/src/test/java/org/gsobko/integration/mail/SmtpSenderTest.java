package org.gsobko.integration.mail;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Properties;

import static jakarta.mail.Message.RecipientType.TO;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

class SmtpSenderTest {

    Session session = mock(Session.class);
    SmtpSender sender = new SmtpSender("from@bbb.ccc", () -> session);

    @BeforeEach
    void setUp() {
        when(session.getProperties()).thenReturn(new Properties());
    }

    @Test
    void should_send_email_over_transport_and_populate() throws Exception {
        try(var transportMocked = mockStatic(Transport.class)) {
            sender.sendEmail("msg_123", "to@ddd.eee", "subj", "Body");
            Message message = onlySentMessage(transportMocked);
            assertThat(message.getFrom()).extracting(Address::toString)
                    .containsExactly("from@bbb.ccc");
            assertThat(message.getRecipients(TO)).extracting(Address::toString)
                    .containsExactly("to@ddd.eee");
            assertThat(message.getContent().toString())
                    .isEqualTo("Body");
            assertThat(message.getSubject())
                    .isEqualTo("subj");
        }
    }

    @Test
    void should_send_email_and_populate_message_id_on_it_with_prefix() throws Exception {
        try(var transportMocked = mockStatic(Transport.class)) {
            sender.sendEmail("msg_123", "to@ddd.eee", "subj", "Body");
            Message message = onlySentMessage(transportMocked);
            assertThat(message.getHeader("Message-Id"))
                    .containsExactly("GmailIntegrationApp:msg_123");
        }
    }

    @Test
    void should_send_email_to_multiple_recipients() throws Exception {
        try(var transportMocked = mockStatic(Transport.class)) {
            sender.sendEmail("msg_123", "to1@ddd.eee, to2@ddd.eee", "subj", "Body");
            Message message = onlySentMessage(transportMocked);
            assertThat(message.getRecipients(TO)).extracting(Address::toString)
                    .containsExactly("to1@ddd.eee", "to2@ddd.eee");
        }
    }

    private static Message onlySentMessage(MockedStatic<Transport> transportMocked) {
        var captor = ArgumentCaptor.forClass(Message.class);
        transportMocked.verify(() -> Transport.send(captor.capture()), only());
        return captor.getValue();
    }
}