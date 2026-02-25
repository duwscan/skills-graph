package com.sk.skillsgraph.util;

public final class AppExceptions {

    private AppExceptions() {
    }

    public abstract static class AppException extends RuntimeException {

        private final int statusCode;

        protected AppException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    public static class SkillNotFoundException extends AppException {
        public SkillNotFoundException(String skillId) {
            super("Skill not found: " + skillId, 404);
        }
    }

    public static class DuplicateSkillException extends AppException {
        public DuplicateSkillException(String skillId) {
            super("Duplicate skill: " + skillId, 409);
        }
    }

    public static class ValidationException extends AppException {
        public ValidationException(String message) {
            super(message, 400);
        }

        public ValidationException(String message, int statusCode) {
            super(message, statusCode);
        }
    }

    public static class CycleDetectedException extends AppException {
        public CycleDetectedException(String message) {
            super(message, 422);
        }
    }

    public static class EdgeNotFoundException extends AppException {
        public EdgeNotFoundException(String edgeId) {
            super("Edge not found: " + edgeId, 404);
        }
    }

    public static class ExtractionBusyException extends AppException {
        private final int retryAfterSeconds;

        public ExtractionBusyException(String message, int retryAfterSeconds) {
            super(message, 429);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
