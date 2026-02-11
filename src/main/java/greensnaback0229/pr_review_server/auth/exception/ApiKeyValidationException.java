package greensnaback0229.pr_review_server.auth.exception;

public class ApiKeyValidationException extends RuntimeException {

    public ApiKeyValidationException(String message) {
        super(message);
    }
}
