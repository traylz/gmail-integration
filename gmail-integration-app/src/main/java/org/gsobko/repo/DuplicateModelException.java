package org.gsobko.repo;

public class DuplicateModelException extends RuntimeException {
    private final String constraint;

    public DuplicateModelException(String constraint, Throwable cause) {
        super(cause);
        this.constraint = constraint;
    }

    public String constraint() {
        return constraint;
    }
}
