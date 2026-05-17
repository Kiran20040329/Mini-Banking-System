package model;

/**
 * CurrentAccount — Extends Account (Inheritance).
 * Has overdraft facility — can go below zero up to a limit.
 */
public class CurrentAccount extends Account {

    public static final double OVERDRAFT_LIMIT = 10000.00;
    public static final double MIN_BALANCE     = 0.00;    // No min for current

    public CurrentAccount(String fullName,
                          String accountNumber,
                          String username,
                          String password,
                          double balance) {

        super(fullName, accountNumber, username, password, balance, "CURRENT");
    }

    // Current accounts allow withdrawing up to overdraft limit
    public boolean canWithdraw(double amount) {
        return (getBalance() - amount) >= -OVERDRAFT_LIMIT;
    }

    @Override
    public String toString() {
        return super.toString() +
               String.format(
                   "\n║  Overdraft: ₹%-20.2f║" +
                   "\n╚══════════════════════════════════╝",
                   OVERDRAFT_LIMIT
               );
    }
}