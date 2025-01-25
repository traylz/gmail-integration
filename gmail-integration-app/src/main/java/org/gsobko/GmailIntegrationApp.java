package org.gsobko;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import org.flywaydb.core.Flyway;
import org.gsobko.integration.mail.ImapFetcher;
import org.gsobko.integration.mail.SmtpSender;
import org.gsobko.repo.MailRepo;
import org.gsobko.resource.MailResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

public class GmailIntegrationApp {

    private static final Logger logger = LoggerFactory.getLogger(GmailIntegrationApp.class);

    private final Properties properties;
    private final List<Closeable> cleanupOnStop = new ArrayList<>();

    public GmailIntegrationApp(Properties properties) {
        this.properties = properties;
    }

    public void start() {
        ImapFetcher fetcher = createFetcher();
        SmtpSender sender = createMailSender();
        HikariDataSource dataSource = createDbConnectionPool();
        MailRepo repo = new MailRepo(dataSource);

        migrate(dataSource);

        Javalin javalin = bootstrapWebServer(repo, sender);

        PeriodicMailFetcher periodicMailFetcher = new PeriodicMailFetcher(repo, fetcher, 5);
        periodicMailFetcher.start();

        cleanupOnStop.add(javalin::stop);
        cleanupOnStop.add(periodicMailFetcher::stop);
        cleanupOnStop.add(dataSource);
    }

    private Javalin bootstrapWebServer(MailRepo repo, SmtpSender sender) {
        Javalin javalin = createJavalin();


        MailResource mailResource = new MailResource(repo, sender);
        javalin.get("/mails", mailResource::fetchEmails);
        javalin.post("/mail", mailResource::sendEmail);
        javalin.start(parseInt(requiredProperty("server.port")));
        return javalin;
    }

    private static Javalin createJavalin() {
        ObjectMapper objectMapper = setupObjectMapper();
        Javalin javalin = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson(objectMapper, false));
            cfg.requestLogger.http(((ctx, executionTimeMs) ->
                    logger.info("Request {} {} handled in {}ms, response status:{}", ctx.method(), ctx.path(), executionTimeMs, ctx.status())));
        });

        javalin.before(ctx -> logger.info("Received request to {} {}", ctx.method(), ctx.path()));

        javalin.exception(Exception.class, (e, ctx) -> {
            logger.error("Error handling {} {}", ctx.method(), ctx.path(), e);
            if (e instanceof IllegalArgumentException) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.result(e.getMessage());
            }
        });

        return javalin;
    }

    private static ObjectMapper setupObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }


    private ImapFetcher createFetcher() {
        return new ImapFetcher(
                requiredProperty("gmail.email"),
                requiredProperty("gmail.app_password"),
                requiredProperty("gmail.folder"),
                requiredProperty("gmail.imap.host"),
                requireIntProperty("gmail.imap.port"),
                requireBooleanProperty("gmail.imap.disable_ssl_checks"),
                requireIntProperty("gmail.initial_max_depth"));
    }


    private HikariDataSource createDbConnectionPool() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(requiredProperty("db.url"));
        dataSource.setUsername(requiredProperty("db.username"));
        dataSource.setPassword(requiredProperty("db.password"));
        dataSource.setMaximumPoolSize(intProperty("db.pool.size", 5));
        return dataSource;
    }

    private void migrate(HikariDataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .locations("classpath:/migrations")
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
    }

    protected SmtpSender createMailSender() {
        return new SmtpSender(requiredProperty("gmail.email"),
                requiredProperty("gmail.app_password"),
                requiredProperty("gmail.smtp.host"),
                requireIntProperty("gmail.smtp.port")
        );
    }

    private String requiredProperty(String name) {
        return requireNonNull(properties.getProperty(name), "Property %s not set".formatted(name));
    }

    private boolean requireBooleanProperty(String name) {
        return Boolean.parseBoolean(requiredProperty(name));
    }

    private Integer requireIntProperty(String name) {
        return Integer.parseInt(requiredProperty(name));
    }

    private Integer intProperty(String name, int defaultVal) {
        if (properties.containsKey(name)) {
            return Integer.parseInt(requiredProperty(name));
        }
        return defaultVal;
    }

    public void stop() {
        cleanupOnStop.forEach(closeable -> {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.info("Could not stop resource during graceful termination", e);
            }
        });
    }


}
