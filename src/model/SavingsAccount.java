package model;

/**
 * SavingsAccount — Extends Account (Inheritance).
 *
 * OOP Concepts:
 *   - Inheritance    : extends Account, reuses all fields and getters
 *   - super keyword  : calls parent constructor to initialize common fields
 *   - Polymorphism   : overrides toString() with savings-specific info
 *   - final constant : MIN_BALANCE is fixed for all savings accounts
 */
public class SavingsAccount extends Account {

    // Savings-specific constant — cannot be changed
    public static final double MIN_BALANCE   = 1000.00;
    public static final double INTEREST_RATE = 3.5;     // Annual %

    // ── CONSTRUCTOR ────────────────────────────────────────────────────────
    // 'super(...)' must be the FIRST statement — calls Account's constructor
    // to initialize: fullName, accountNumber, username, password, balance
    public SavingsAccount(String fullName,
                          String accountNumber,
                          String username,
                          String password,
                          double balance) {

        super(fullName, accountNumber, username, password, balance, "SAVINGS");
        // 'accountType' is set to "SAVINGS" via super — no duplication
    }

    // ── Savings-Specific Method ────────────────────────────────────────────
    public double calculateMonthlyInterest() {
        // Simple interest for one month
        return (getBalance() * INTEREST_RATE) / (100 * 12);
    }

    // ── Method Overriding — Runtime Polymorphism ───────────────────────────
    // @Override tells compiler: this intentionally replaces Account.toString()
    // Java decides at RUNTIME which toString() to call based on actual type
    @Override
    public String toString() {
        return super.toString() +   // calls Account.toString() first
               String.format(
                   "\n║  Interest: %.1f%% p.a.%14s║" +
                   "\n║  Min Bal : ₹%-21.2f║" +
                   "\n╚══════════════════════════════════╝",
                   INTEREST_RATE, "",
                   MIN_BALANCE
               );
    }
}