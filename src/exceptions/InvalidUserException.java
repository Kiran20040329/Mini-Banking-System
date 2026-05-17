package exceptions;

/** Thrown when an account number or username does not exist in the DB. */
public class InvalidUserException extends Exception {

    public InvalidUserException(String message) {
        super(message);
    }
}