// src/operations/BankOperations.java
package operations;

/**
 * BankOperations — Interface defining the contract for all money operations.
 *
 * Abstraction: Callers (Main.java) use this interface type.
 * They don't know if the implementation is SQL, file-based, or API-based.
 * This makes the code testable and swappable.
 */
public interface BankOperations {

    /**
     * Deposits amount into account.
     * @throws IllegalArgumentException for invalid amount
     * @throws Exception for DB or account errors
     */
    void deposit(String accountNumber, double amount) throws Exception;

    /**
     * Withdraws amount from account.
     * @throws InsufficientBalanceException if balance rule violated
     * @throws Exception for DB or account errors
     */
    void withdraw(String accountNumber, double amount) throws Exception;

    /**
     * Transfers amount from sender to receiver.
     * MUST be ACID-compliant — atomic, consistent, isolated, durable.
     * @throws InsufficientBalanceException if sender has insufficient funds
     * @throws InvalidUserException if either account doesn't exist
     * @throws Exception for DB errors
     */
    void transfer(String senderAccount,
                  String receiverAccount,
                  double amount) throws Exception;

    /**
     * Returns current balance for the account.
     * Always reads live from database.
     */
    double checkBalance(String accountNumber) throws Exception;
}