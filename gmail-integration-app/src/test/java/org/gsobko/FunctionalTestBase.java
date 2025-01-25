package org.gsobko;


import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.UUID;

public abstract class FunctionalTestBase {
    public static final String EMAIL = "aaa@bbb";
    public static final String PASS = "pass";
    protected final int serverPort = someFreePort();
    protected final String baseUrl = "http://localhost:" + serverPort;
    protected GreenMail greenMail = new GreenMail(ServerSetup.dynamicPort(new ServerSetup[]{ServerSetup.IMAPS, ServerSetup.SMTP}));
    protected GreenMailUser user;
    protected GmailIntegrationApp app;

    @BeforeEach
    protected void start() {
        user = greenMail.setUser(EMAIL, PASS);
        greenMail.start();
        Properties appProps = createTestProperties();
        app = new GmailIntegrationApp(appProps);
        app.start();
    }

    @AfterEach
    protected void stop() {
        app.stop();
        greenMail.stop();
    }

    private Properties createTestProperties() {
        Properties properties = new Properties();
        properties.put("server.port", "%d".formatted(serverPort));
        properties.put("db.url", "jdbc:h2:mem:testdb_%s;DB_CLOSE_DELAY=-1".formatted(UUID.randomUUID()));
        properties.put("db.username", "user");
        properties.put("db.password", "password");
        properties.put("db.pool.size", "5");
        properties.put("gmail.folder", "INBOX");
        properties.put("gmail.initial_max_depth", "10");
        properties.put("gmail.email", EMAIL);
        properties.put("gmail.app_password", PASS);
        properties.put("gmail.imap.host", "localhost");
        properties.put("gmail.imap.port", Integer.toString(greenMail.getImaps().getPort()));
        properties.put("gmail.imap.disable_ssl_checks", "true");
        properties.put("gmail.smtp.host", "localhost");
        properties.put("gmail.smtp.port", Integer.toString(greenMail.getSmtp().getPort()));
        return properties;
    }


    private static int someFreePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}