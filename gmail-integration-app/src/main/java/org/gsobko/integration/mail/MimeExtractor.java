package org.gsobko.integration.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MimeExtractor {
    private static final Logger logger = LoggerFactory.getLogger(MimeExtractor.class);

    public record MessageContent(Optional<String> text, Optional<String> html, List<String> attachmentNames) {

    }

    public static MessageContent extractContent(MimeMessage message) throws MessagingException, IOException {
        if (message.isMimeType("multipart/*")) {
            List<MimeBodyPart> parts = listPartsFlat((MimeMultipart) message.getContent());
            Optional<String> textPlain = findContentInParts(parts, "text/plain");
            Optional<String> html = findContentInParts(parts, "text/html");
            List<String> attachmentFilenames = findAttachments(parts);
            return new MessageContent(textPlain, html, attachmentFilenames);
        }

        if (message.isMimeType("text/html")) {
            return new MessageContent(Optional.empty(), Optional.of(message.getContent().toString()), List.of());
        }

        if (message.isMimeType("text/plain")) {
            return new MessageContent(Optional.of(message.getContent().toString()), Optional.empty(), List.of());
        }

        logger.warn("Unsupported message mime type: {}", message.getContentType());
        return new MessageContent(Optional.empty(), Optional.empty(), List.of());
    }

    private static Optional<String> findContentInParts(List<MimeBodyPart> parts, String contentType) throws MessagingException, IOException {
        for (BodyPart part : parts) {
            if (part.isMimeType(contentType)) {
                return Optional.of(part.getContent().toString());
            }
        }
        return Optional.empty();
    }

    private static List<String> findAttachments(List<MimeBodyPart> parts) throws MessagingException {
        List<String> attachments = new ArrayList<>();
        for (MimeBodyPart part : parts) {
            String fileName = part.getFileName();
            if (fileName != null) {
                attachments.add(fileName);
            }
        }
        return attachments;
    }

    private static List<MimeBodyPart> listPartsFlat(MimeMultipart mimeMultipart) throws MessagingException, IOException {
        List<MimeBodyPart> bodyParts = new ArrayList<>();
        for (int i = 0; i < mimeMultipart.getCount(); i++) {
            MimeBodyPart bodyPart = (MimeBodyPart) mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("multipart/*")) {
                bodyParts.addAll(listPartsFlat((MimeMultipart) bodyPart.getContent()));
            } else {
                bodyParts.add(bodyPart);
            }
        }
        return bodyParts;
    }
}
