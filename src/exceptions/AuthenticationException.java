package exceptions;

/** Thrown when login credentials are incorrect. */
public class AuthenticationException extends Exception {

    private int attemptNumber;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, int attemptNumber) {
        super(message);
        this.attemptNumber = attemptNumber;
    }
    
    public int getAttemptNumber() { return attemptNumber; }
}