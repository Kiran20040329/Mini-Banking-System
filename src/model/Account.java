package model;

/**
 * Account — Base model class representing a bank account.
 *
 * OOP Concepts:
 *   - Encapsulation  : all fields are private
 *   - Constructors   : default + parameterized
 *   - this keyword   : resolves field vs parameter name conflicts
 *   - toString()     : overrides Object.toString() for display
 */
public class Account {

    // ── PRIVATE FIELDS ─────────────────────────────────────────────────────
    // Encapsulation: no outside class can directly access these
    // They can only be read/changed through getters and setters below

    private int    id;              // DB primary key
    private String fullName;        // Account holder's full name
    private String accountNumber;   // Bank-assigned number e.g. ACC100001
    private String username;        // Login username
    private String password;        // Sensitive — never print this directly
    private double balance;         // Current balance — CONTROLLED access only
    private String accountType;     // "SAVINGS" or "CURRENT"
    private boolean isActive;       // true = active, false = frozen

    // ── DEFAULT CONSTRUCTOR ────────────────────────────────────────────────
    // Used by JDBC ResultSet mapping — creates empty object, then sets fields
    public Account() {
        this.isActive = true; // New accounts are active by default
    }

    // ── PARAMETERIZED CONSTRUCTOR ──────────────────────────────────────────
    // 'this.fieldName = paramName' resolves the naming conflict
    // without 'this', Java can't tell the field from the parameter
    public Account(String fullName,
                   String accountNumber,
                   String username,
                   String password,
                   double balance,
                   String accountType) {

        this.fullName      = fullName;       // this.fullName = class field
        this.accountNumber = accountNumber;  // fullName      = parameter
        this.username      = username;
        this.password      = password;
        this.balance       = balance;
        this.accountType   = accountType;
        this.isActive      = true;
    }

    // ── GETTERS ────────────────────────────────────────────────────────────
    // Read-only access to private fields from outside the class

    public int     getId()            { return id; }
    public String  getFullName()      { return fullName; }
    public String  getAccountNumber() { return accountNumber; }
    public String  getUsername()      { return username; }
    public String  getPassword()      { return password; }
    public double  getBalance()       { return balance; }
    public String  getAccountType()   { return accountType; }
    public boolean isActive()         { return isActive; }

    // ── SETTERS ────────────────────────────────────────────────────────────
    // Controlled write access — can add validation here

    public void setId(int id) {
        this.id = id;
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }
        this.fullName = fullName.trim();
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public void setUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        this.username = username.trim().toLowerCase();
    }

    public void setPassword(String password) {
        // In production: store BCrypt hash, never plain text
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException(
                "Password must be at least 6 characters.");
        }
        this.password = password;
    }

    public void setBalance(double balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Balance cannot be negative.");
        }
        this.balance = balance;
    }

    public void setAccountType(String accountType) {
        if (!accountType.equals("SAVINGS") && !accountType.equals("CURRENT")) {
            throw new IllegalArgumentException(
                "Account type must be SAVINGS or CURRENT.");
        }
        this.accountType = accountType;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    // ── toString() ─────────────────────────────────────────────────────────
    // Overrides Object.toString() — called automatically when you print
    // an Account object. Note: password is intentionally NOT shown.
    @Override
    public String toString() {
        return String.format(
            "\n╔══════════════════════════════════╗" +
            "\n║        ACCOUNT DETAILS           ║" +
            "\n╠══════════════════════════════════╣" +
            "\n║  Name    : %-22s║" +
            "\n║  Acc No  : %-22s║" +
            "\n║  Type    : %-22s║" +
            "\n║  Balance : ₹%-21.2f║" +
            "\n║  Status  : %-22s║" +
            "\n╚══════════════════════════════════╝",
            fullName,
            accountNumber,
            accountType,
            balance,
            isActive ? "ACTIVE" : "FROZEN"
        );
    }
}