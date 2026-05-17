package exceptions;

/**
 * Thrown when a withdrawal or transfer would violate minimum balance rules.
 * Extends Exception (checked) — caller MUST handle or declare it.
 */
public class InsufficientBalanceException extends Exception {

    private final double currentBalance;
    private final double requiredAmount;
    
    public InsufficientBalanceException(String message) {
        super(message);
        this.currentBalance = 0;
        this.requiredAmount = 0;
    }

    public InsufficientBalanceException(String message,
                                        double currentBalance,
                                        double requiredAmount) {
        super(message);
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }
    
    public double getCurrentBalance() { return currentBalance; }
    public double getRequiredAmount() { return requiredAmount; }
}