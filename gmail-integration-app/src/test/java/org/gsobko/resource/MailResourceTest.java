package org.gsobko.resource;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.gsobko.FunctionalTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Instant;

import static com.icegreen.greenmail.util.GreenMailUtil.createTextEmail;
import static java.util.concurrent.TimeUnit.SECONDS;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MailResourceTest extends FunctionalTestBase {

    public static final String ISO_DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{0,9})?Z";
    HttpClient client = HttpClient.newHttpClient();

    @Test
    void should_return_an_email_fetched_from_imap_server() throws URISyntaxException, IOException, InterruptedException {
        // given
        user.deliver(createTextEmail("aaa@bbb", EMAIL, "Subj1", "Body test", greenMail.getImaps().getServerSetup()));


        await().atMost(3, SECONDS).untilAsserted(() -> {

            // when
            Instant from = Instant.now().minusSeconds(100);
            Instant to = Instant.now();
            HttpResponse<String> response = get(URI.create(baseUrl + "/mails?from=%s&to=%s".formatted(from, to)));

            //then
            assertThat(response.statusCode()).isEqualTo(200);
            assertThatJson(response.body()).isArray().hasSize(1);
            assertThatJson(response.body())
                    .whenIgnoringPaths("[*].messageId", "[*].sentDate", "[*].createdDate", "[*].id", "[*].imapUid")
                    .isEqualTo("""
                            [{
                              "from": "aaa@bbb",
                              "to": "aaa@bbb",
                              "cc": "",
                              "subject": "Subj1",
                              "text": "Body test",
                              "html": "",
                              "attachments": []
                            }]
                            """);
            assertThatJson(response.body()).inPath("[0].messageId").isPresent();
            assertThatJson(response.body()).inPath("[0].sentDate").isString().matches(ISO_DATE_PATTERN);
            assertThatJson(response.body()).inPath("[0].createdDate").isString().matches(ISO_DATE_PATTERN);
            assertThatJson(response.body()).inPath("[0].imapUid").isIntegralNumber();
        });
    }


    @Test
    void should_post_email_to_smtp() throws URISyntaxException, IOException, InterruptedException, MessagingException {
        // given
        String emailRequest = """
                {
                   "messageId":"msg123",
                   "to": "bbb@ddd,ccc@ddd",
                   "subject":"Subj1",
                   "body":"Hello, world!"
                }
                """;

        // when
        URI postMailUri = URI.create(baseUrl + "/mail");
        HttpResponse<String> response = post(postMailUri, emailRequest);

        // then
        assertEquals(200, response.statusCode());

        greenMail.waitForIncomingEmail(1000, 1);
        MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertThat(receivedMessages).hasSize(2);

        MimeMessage message = receivedMessages[0];
        assertThat(message.getContent().toString()).isEqualTo("Hello, world!");
        assertThat(message.getFrom()).isEqualTo(InternetAddress.parse(EMAIL));
        assertThat(message.getSubject()).isEqualTo("Subj1");
    }


    @Test
    void should_return_400_bad_request_if_request_wo_from_date() throws Exception {
        // when
        HttpResponse<String> response = get(URI.create(baseUrl + "/mails?&to=%s".formatted(Instant.now())));

        //then
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("from");
    }

    @Test
    void should_return_400_bad_request_if_request_wo_to_date() throws Exception {
        // when
        HttpResponse<String> response = get(URI.create(baseUrl + "/mails?&from=%s".formatted(Instant.now())));

        //then
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("to");
    }

    private HttpResponse<String> get(URI url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(url)
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(URI url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(url)
                .POST(BodyPublishers.ofString(body))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @AfterEach
    void tearDown() {
        client.close();
    }
}