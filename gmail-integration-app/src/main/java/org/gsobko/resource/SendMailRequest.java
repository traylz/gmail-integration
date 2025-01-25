package org.gsobko.resource;

public record SendMailRequest(String requestId,
                              String to,
                              String subject,
                              String body) {
}
