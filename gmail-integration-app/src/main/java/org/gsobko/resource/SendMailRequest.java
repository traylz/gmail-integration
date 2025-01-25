package org.gsobko.resource;

public record SendMailRequest(String messageId,
                              String to,
                              String subject,
                              String body) {
}
