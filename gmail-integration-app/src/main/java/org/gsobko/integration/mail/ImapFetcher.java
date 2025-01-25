package org.gsobko.integration.mail;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.LongStream;

public class ImapFetcher {
    private static final Logger logger = LoggerFactory.getLogger(ImapFetcher.class);
    private final Properties imapsProperties;
    private final PasswordAuthentication auth;
    private final String folder;
    private final int initialDepthLimit;

    public ImapFetcher(String username, String password, String folder, String hostname,
                       int port, boolean disableSslChecks, int initialDepthLimit) {
        this.initialDepthLimit = initialDepthLimit;
        this.imapsProperties = imapsProperties(hostname, port, disableSslChecks);
        this.auth = new PasswordAuthentication(username, password);
        this.folder = folder;
    }

    private Properties imapsProperties(String hostname, int port, boolean disableSslChecks) {
        Properties properties = new Properties();
        properties.put("mail.imaps.host", hostname);
        properties.put("mail.imaps.port", port);
        properties.put("mail.imaps.ssl.enable", "true");
        if (disableSslChecks) {
            properties.put("mail.imaps.ssl.checkserveridentity", "false");
            properties.put("mail.imaps.ssl.trust", "*");
        }
        return properties;
    }


    public void fetchEmailsSinceUid(OptionalLong lastReadUid, Consumer<FetchedEmail> reader) {
        boolean wasNotFetchedBefore = lastReadUid.isEmpty();
        try (Store store = getStore(); IMAPFolder imapFolder = openFolder(store)) {
            logger.info("Requesting for new UIDs since last UID {}", lastReadUid);
            List<Long> newUuids = getNewUidsSince(imapFolder, lastReadUid);
            List<Long> uidsToFetch = limitMaximumNumber(wasNotFetchedBefore, newUuids);
            if (uidsToFetch.isEmpty()) {
                logger.info("No new mail in folder {}", folder);
                return;
            }
            fetchAll(imapFolder, uidsToFetch, reader);
        } catch (Exception e) {
            logger.error("Error reading emails", e);
            throw new IllegalStateException("Error reading emails from folder %s".formatted(folder), e);
        }
    }

    private List<Long> limitMaximumNumber(boolean wasNotFetchedBefore, List<Long> newUuids) {
        if (wasNotFetchedBefore && newUuids.size() > initialDepthLimit) {
            logger.warn("The initial folder size {} is greater than initial depth limit, will only fetch latest {} messages", newUuids.size(), initialDepthLimit);
            return newUuids.subList(newUuids.size() - initialDepthLimit, newUuids.size());
        }
        return newUuids;
    }

    private Store getStore() throws MessagingException {
        Session instance = Session.getInstance(imapsProperties);
        Store store = instance.getStore("imaps");
        store.connect(auth.getUserName(), auth.getPassword());
        return store;
    }

    private static List<Long> getNewUidsSince(IMAPFolder emailFolder, OptionalLong lastReadUid) throws MessagingException {
        long startSequence = lastReadUid.orElse(0L) + 1;
        long uidNext = emailFolder.getUIDNext();
        logger.info("Fetching UID in range {}:{}", startSequence, uidNext);
        long[] newUids = (long[]) emailFolder.doCommand(p -> p.fetchSequenceNumbers(startSequence, uidNext));
        return LongStream.of(newUids).sorted().boxed().toList();
    }

    private static void fetchAll(IMAPFolder emailFolder, List<Long> uidsToFetch, Consumer<FetchedEmail> reader) throws Exception {
        logger.info("About to fetch {} uids", uidsToFetch.size());
        for (int i = 0; i < uidsToFetch.size(); i++) {
            Long uid = uidsToFetch.get(i);
            logger.info("Fetching uid[{}]. {}/{}", uid, i + 1, uidsToFetch.size());
            MimeMessage message = (MimeMessage) emailFolder.getMessageByUID(uid);
            FetchedEmail fetchedEmail = convertToFetchedEmail(uid, message);
            reader.accept(fetchedEmail);
        }
    }


    private static FetchedEmail convertToFetchedEmail(long uid, MimeMessage message) throws Exception {
        String messageId = message.getMessageID();
        String from = message.getFrom()[0].toString();
        String to = Optional.ofNullable(message.getRecipients(Message.RecipientType.TO)).map(InternetAddress::toString).orElse("");
        String cc = Optional.ofNullable(message.getRecipients(Message.RecipientType.CC)).map(InternetAddress::toString).orElse("");
        MimeExtractor.MessageContent content = MimeExtractor.extractContent(message);
        String subject = message.getSubject();
        logger.info("Message uid {} and subject {}: content is: text={}; html={}; attachemnts={}", uid, subject, content.text().isPresent(), content.html().isPresent(), content.attachmentNames());
        return new FetchedEmail(
                messageId, uid,
                from, to, cc,
                subject, content.text(), content.html(), content.attachmentNames(),
                message.getSentDate().toInstant()
        );
    }

    private IMAPFolder openFolder(Store store) throws MessagingException {
        IMAPFolder emailFolder = (IMAPFolder) store.getFolder(folder);
        emailFolder.open(Folder.READ_ONLY);
        return emailFolder;
    }


}