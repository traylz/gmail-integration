package org.gsobko;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            Properties properties = loadProperties();
            GmailIntegrationApp gmailIntegrationApp = new GmailIntegrationApp(properties);
            gmailIntegrationApp.start();
            Runtime.getRuntime().addShutdownHook(new Thread(gmailIntegrationApp::stop, "terminator"));
        } catch (Exception e) {
            logger.error("Cannot start application", e);
            e.printStackTrace(System.err);
            System.err.flush();
            System.exit(0);
        }
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(Main.class.getResourceAsStream("/app.properties"));
        return properties;
    }
}
