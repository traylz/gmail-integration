package org.gsobko.integration.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.icegreen.greenmail.util.GreenMailUtil.createTextEmail;
import static com.icegreen.greenmail.util.GreenMailUtil.getSession;
import static org.assertj.core.api.Assertions.assertThat;

class MimeExtractorTest {

    GreenMail greenMail;

    @BeforeEach
    void setUp() {
        greenMail = new GreenMail(ServerSetup.SMTP.dynamicPort());
        greenMail.start();
    }

    @Test
    void should_extract_plain_text_email() throws Exception {
        // given
        MimeMessage message = createTextEmail("from@example.com", "to@example.com", "subject", "Plain text body", greenMail.getSmtp().getServerSetup());

        // when
        MimeExtractor.MessageContent content = MimeExtractor.extractContent(message);

        // then
        assertThat(content.text()).isPresent();
        assertThat(content.text().get()).isEqualTo("Plain text body");
        assertThat(content.html()).isEmpty();
        assertThat(content.attachmentNames()).isEmpty();
    }

    @Test
    void should_extract_html_email() throws Exception {
        // given
        MimeMessage message = createMimeMessage();
        message.setContent("<html><body><p>Hello, HTML!</p></body></html>", "text/html");
        message.setHeader("Content-Type", "text/html");

        // when
        MimeExtractor.MessageContent content = MimeExtractor.extractContent(message);

        // then
        assertThat(content.text()).isEmpty();
        assertThat(content.html()).isPresent();
        assertThat(content.html().get()).contains("Hello, HTML!");
        assertThat(content.attachmentNames()).isEmpty();
    }


    @Test
    void should_extract_multipart_email_with_plain_and_html() throws Exception {
        // given

        MimeMessage message = createMimeMessage();
        message.setContent(new MimeMultipart(
                bodyPartWith(part -> part.setText("Plain text content", "UTF-8", "plain")),
                bodyPartWith(part -> {
                    part.setText("<html><body><h1>HTML Content</h1></body></html>", "UTF-8", "html");
                    part.setHeader("Content-Type", "text/html");
                })
        ));
        message.setHeader("Content-Type", "multipart/alternative");

        // when
        MimeExtractor.MessageContent content = MimeExtractor.extractContent(message);

        // then
        assertThat(content.text()).contains("Plain text content");
        assertThat(content.html()).contains("<html><body><h1>HTML Content</h1></body></html>");
        assertThat(content.attachmentNames()).isEmpty();
    }

    @Test
    void should_extract_attachments() throws Exception {
        // given
        MimeMessage message = createMimeMessage();
        message.setContent(new MimeMultipart(
                bodyPartWith(part -> part.setText("Email body")),
                bodyPartWith(part -> {
                    part.setFileName("attachment.txt");
                    part.setContent("Attachment content", "text/plain");
                })
        ));
        message.setHeader("Content-Type", "multipart/mixed");

        // when
        MimeExtractor.MessageContent content = MimeExtractor.extractContent(message);

        // then
        assertThat(content.text()).contains("Email body");
        assertThat(content.html()).isEmpty();
        assertThat(content.attachmentNames()).containsExactly("attachment.txt");
    }


    @Test
    void should_handle_unsupported_mime_type() throws Exception {
        // given
        MimeMessage message = createMimeMessage();
        message.setContent(new byte[]{1, 2, 3}, "application/octet-stream");
        message.setHeader("Content-type", "application/octet-stream");

        // when
        MimeExtractor.MessageContent content = MimeExtractor.extractContent(message);

        // then
        assertThat(content.text()).isEmpty();
        assertThat(content.html()).isEmpty();
        assertThat(content.attachmentNames()).isEmpty();
    }


    @AfterEach
    void tearDown() {
        greenMail.stop();
    }

    private MimeBodyPart bodyPartWith(MimeSetter sett) throws Exception {
        MimeBodyPart bp = new MimeBodyPart();
        sett.setOn(bp);
        return bp;
    }

    private MimeMessage createMimeMessage() {
        return new MimeMessage(getSession(greenMail.getSmtp().getServerSetup()));
    }

    interface MimeSetter {
        void setOn(MimeBodyPart part) throws Exception;
    }
}