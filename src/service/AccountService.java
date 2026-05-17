package service;

import database.DBConnection;
import exceptions.AuthenticationException;
import exceptions.InvalidUserException;
import model.Account;
import utility.AccountUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * AccountService — Handles all non-transaction account operations.
 *
 * OOP Concepts Used:
 *   - Encapsulation   : private helper methods hide internal logic
 *   - Abstraction     : callers don't know the SQL details
 *   - Static methods  : for account number generation via AccountUtils
 *
 * JDBC Concepts Used:
 *   - PreparedStatement for ALL SQL (SQL injection prevention)
 *   - ResultSet for reading query results
 *   - SQLException handling in every method
 */
public class AccountService {

    // ── Minimum password length rule ───────────────────────────────────────
    private static final int MIN_PASSWORD_LENGTH = 6;

    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 7 — REGISTER
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Registers a new bank account.
     * Collects user input → validates → inserts into DB.
     *
     * @param sc Scanner object passed from Main.java
     */
    public void register(Scanner sc) {

        AccountUtils.printHeader("New Account Registration");

        try {
            // ── Step 1: Collect Input ──────────────────────────────────────
            System.out.print("  Enter Full Name    : ");
            String fullName = sc.nextLine().trim();

            System.out.print("  Enter Username     : ");
            String username = sc.nextLine().trim().toLowerCase();

            System.out.print("  Enter Password     : ");
            String password = sc.nextLine().trim();

            System.out.print("  Confirm Password   : ");
            String confirmPassword = sc.nextLine().trim();

            System.out.print("  Account Type       : ");
            System.out.println("  (1) Savings  (2) Current");
            System.out.print("  Choose             : ");
            int typeChoice = Integer.parseInt(sc.nextLine().trim());

            System.out.print("  Initial Deposit (₹): ");
            double initialDeposit = Double.parseDouble(sc.nextLine().trim());

            // ── Step 2: Validate All Inputs ───────────────────────────────
            validateRegistrationInput(
                fullName, username, password,
                confirmPassword, initialDeposit, typeChoice
            );

            // ── Step 3: Determine Account Type ────────────────────────────
            String accountType = (typeChoice == 1) ? "SAVINGS" : "CURRENT";

            // Enforce minimum initial deposit per account type
            double minDeposit = accountType.equals("SAVINGS") ? 1000.00 : 5000.00;
            if (initialDeposit < minDeposit) {
                System.out.printf(
                    "  ✗ Minimum initial deposit for %s account: ₹%.2f%n",
                    accountType, minDeposit
                );
                return;
            }

            // ── Step 4: Check if username already exists ───────────────────
            if (isUsernameTaken(username)) {
                System.out.println("  ✗ Username '" + username +
                                   "' is already taken. Choose another.");
                return;
            }

            // ── Step 5: Auto-generate unique Account Number ───────────────
            // AccountUtils generates: ACC100001, ACC100002, etc.
            // In production: query MAX(account_number) from DB
            String accountNumber = generateUniqueAccountNumber();

            // ── Step 6: INSERT into database using PreparedStatement ───────
            String sql =
                "INSERT INTO accounts " +
                "(full_name, account_number, username, password, " +
                " balance, account_type) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
            //         1   2   3   4   5   6

            Connection conn = DBConnection.getConnection();

            // try-with-resources: PreparedStatement auto-closes after block
            try (PreparedStatement ps = conn.prepareStatement(
                     sql,
                     Statement.RETURN_GENERATED_KEYS  // fetch the auto-generated id
                 )) {

                // Bind each ? placeholder with actual value
                ps.setString(1, fullName);        // full_name
                ps.setString(2, accountNumber);   // account_number
                ps.setString(3, username);         // username
                ps.setString(4, password);         // password (hash in production)
                ps.setDouble(5, initialDeposit);  // balance
                ps.setString(6, accountType);      // account_type

                // executeUpdate() runs INSERT/UPDATE/DELETE
                // Returns number of rows affected
                int rowsAffected = ps.executeUpdate();

                if (rowsAffected > 0) {
                    // Retrieve the auto-generated primary key (id)
                    ResultSet generatedKeys = ps.getGeneratedKeys();
                    int newId = 0;
                    if (generatedKeys.next()) {
                        newId = generatedKeys.getInt(1);
                    }

                    // ── Step 7: Success Output ─────────────────────────────
                    AccountUtils.printDivider();
                    System.out.println("  ✓ Account registered successfully!");
                    System.out.println();
                    System.out.println("  ┌─────────────────────────────────┐");
                    System.out.printf ("  │  Account ID     : %-13d│%n", newId);
                    System.out.printf ("  │  Account Number : %-13s│%n", accountNumber);
                    System.out.printf ("  │  Account Type   : %-13s│%n", accountType);
                    System.out.printf ("  │  Initial Balance: ₹%-12.2f│%n", initialDeposit);
                    System.out.println("  └─────────────────────────────────┘");
                    System.out.println("  Please save your account number safely.");
                    AccountUtils.printDivider();
                } else {
                    System.out.println("  ✗ Registration failed. Please try again.");
                }
            }

        } catch (NumberFormatException e) {
            System.out.println("  ✗ Invalid number entered. Please enter digits only.");
        } catch (IllegalArgumentException e) {
            System.out.println("  ✗ Validation Error: " + e.getMessage());
        } catch (SQLException e) {
            // Catch specific MySQL error codes
            if (e.getErrorCode() == 1062) { // MySQL: Duplicate entry
                System.out.println("  ✗ Username or Account Number already exists.");
            } else {
                System.out.println("  ✗ Database error: " + e.getMessage());
            }
        }
    }

    // ── Private: Validate all registration inputs ──────────────────────────
    private void validateRegistrationInput(String fullName,
                                           String username,
                                           String password,
                                           String confirmPassword,
                                           double initialDeposit,
                                           int typeChoice) {

        if (AccountUtils.isNullOrEmpty(fullName))
            throw new IllegalArgumentException("Full name cannot be blank.");

        if (AccountUtils.isNullOrEmpty(username))
            throw new IllegalArgumentException("Username cannot be blank.");

        if (username.length() < 4)
            throw new IllegalArgumentException(
                "Username must be at least 4 characters.");

        if (password.length() < MIN_PASSWORD_LENGTH)
            throw new IllegalArgumentException(
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");

        if (!password.equals(confirmPassword))
            throw new IllegalArgumentException(
                "Passwords do not match.");

        if (typeChoice != 1 && typeChoice != 2)
            throw new IllegalArgumentException(
                "Account type must be 1 (Savings) or 2 (Current).");

        if (initialDeposit <= 0)
            throw new IllegalArgumentException(
                "Initial deposit must be a positive amount.");
    }

    // ── Private: Check if username already in DB ───────────────────────────
    private boolean isUsernameTaken(String username) throws SQLException {
        String sql = "SELECT COUNT(*) FROM accounts WHERE username = ?";
        Connection conn = DBConnection.getConnection();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0; // true if count > 0
            }
        }
        return false;
    }

    // ── Private: Generate account number not already in DB ────────────────
    private String generateUniqueAccountNumber() throws SQLException {
        String sql    = "SELECT COUNT(*) FROM accounts WHERE account_number = ?";
        Connection conn = DBConnection.getConnection();
        String accNo;

        do {
            accNo = AccountUtils.generateAccountNumber();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accNo);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) == 0) break; // unique → done
            }
        } while (true);

        return accNo;
    }


    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 8 — LOGIN
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Authenticates user with username + password.
     * Returns Account object if login succeeds, null if fails.
     * Limits to 3 attempts before locking out.
     *
     * @param sc Scanner from Main.java
     * @return authenticated Account object, or null on failure
     */
    public Account login(Scanner sc) {

        AccountUtils.printHeader("Secure Login");

        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            System.out.printf("  Attempt %d of %d%n", attempt, maxAttempts);
            System.out.print("  Username : ");
            String username = sc.nextLine().trim().toLowerCase();

            System.out.print("  Password : ");
            String password = sc.nextLine().trim();

            // Validate inputs are not blank
            if (AccountUtils.isNullOrEmpty(username) ||
                AccountUtils.isNullOrEmpty(password)) {
                System.out.println("  ✗ Username and password cannot be empty.\n");
                continue;
            }

            try {
                // ── Query DB with PreparedStatement ────────────────────────
                // Selects ALL columns for the matching username+password pair
                // PreparedStatement prevents: username = "admin' OR '1'='1"
                String sql =
                    "SELECT id, full_name, account_number, username, " +
                    "       password, balance, account_type, is_active " +
                    "FROM accounts " +
                    "WHERE username = ? AND password = ? AND is_active = 1";

                Connection conn = DBConnection.getConnection();

                try (PreparedStatement ps = conn.prepareStatement(sql)) {

                    ps.setString(1, username); // binds username safely
                    ps.setString(2, password); // binds password safely

                    // executeQuery() runs SELECT → returns ResultSet
                    ResultSet rs = ps.executeQuery();

                    // ── ResultSet Explained ────────────────────────────────
                    // ResultSet cursor starts BEFORE the first row.
                    // rs.next() moves cursor forward and returns true if
                    // there is a row, false if no rows found.
                    if (rs.next()) {

                        // ── Map ResultSet columns → Account object ─────────
                        // rs.getInt("column_name")    reads INT column
                        // rs.getString("column_name") reads VARCHAR column
                        // rs.getDouble("column_name") reads DECIMAL column
                        Account account = new Account();
                        account.setId(rs.getInt("id"));
                        account.setFullName(rs.getString("full_name"));
                        account.setAccountNumber(rs.getString("account_number"));
                        account.setUsername(rs.getString("username"));
                        account.setPassword(rs.getString("password"));
                        account.setBalance(rs.getDouble("balance"));
                        account.setAccountType(rs.getString("account_type"));
                        account.setActive(rs.getInt("is_active") == 1);

                        // ── Login Success ──────────────────────────────────
                        AccountUtils.printDivider();
                        System.out.println("  ✓ Login successful!");
                        System.out.printf ("  Welcome back, %s!%n",
                                           account.getFullName());
                        System.out.printf ("  Account  : %s%n",
                                           account.getAccountNumber());
                        System.out.printf ("  Type     : %s%n",
                                           account.getAccountType());
                        System.out.printf ("  Balance  : %s%n",
                                           AccountUtils.formatBalance(
                                               account.getBalance()));
                        AccountUtils.printDivider();

                        return account; // Return the logged-in account

                    } else {
                        // No row returned → wrong credentials
                        int remaining = maxAttempts - attempt;
                        if (remaining > 0) {
                            System.out.printf(
                                "  ✗ Invalid credentials. %d attempt(s) left.%n%n",
                                remaining
                            );
                        }
                    }
                }

            } catch (SQLException e) {
                System.out.println("  ✗ Database error during login: "
                                   + e.getMessage());
                return null;
            }
        }

        // All 3 attempts exhausted
        AccountUtils.printDivider();
        System.out.println("  ✗ Too many failed attempts.");
        System.out.println("  Your session has been locked.");
        System.out.println("  Please contact the bank.");
        AccountUtils.printDivider();

        return null;
    }


    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 9 — CHECK BALANCE
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Fetches and displays the current balance for an account number.
     * Always reads LIVE from DB — never from a cached Java variable.
     *
     * @param sc Scanner from Main.java
     */
    public void checkBalance(Scanner sc) {

        AccountUtils.printHeader("Account Balance Inquiry");

        System.out.print("  Enter Account Number : ");
        String accountNumber = sc.nextLine().trim();

        if (AccountUtils.isNullOrEmpty(accountNumber)) {
            System.out.println("  ✗ Account number cannot be blank.");
            return;
        }

        try {
            // SELECT specific columns only — never SELECT *
            // Fetches name, type, balance for the given account number
            String sql =
                "SELECT full_name, account_type, balance, " +
                "       created_at, is_active " +
                "FROM accounts " +
                "WHERE account_number = ?";

            Connection conn = DBConnection.getConnection();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, accountNumber);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    // Read each column by name from ResultSet
                    String name      = rs.getString("full_name");
                    String type      = rs.getString("account_type");
                    double balance   = rs.getDouble("balance");
                    String createdAt = rs.getString("created_at");
                    boolean active   = rs.getInt("is_active") == 1;

                    // ── Display Balance Statement ──────────────────────────
                    AccountUtils.printDivider();
                    System.out.println("  BALANCE STATEMENT");
                    AccountUtils.printDivider();
                    System.out.printf ("  Account Holder : %s%n", name);
                    System.out.printf ("  Account Number : %s%n", accountNumber);
                    System.out.printf ("  Account Type   : %s%n", type);
                    System.out.printf ("  Account Status : %s%n",
                                       active ? "✓ ACTIVE" : "✗ FROZEN");
                    System.out.printf ("  Member Since   : %s%n", createdAt);
                    AccountUtils.printDivider();
                    System.out.printf ("  Available Balance : %s%n",
                                       AccountUtils.formatBalance(balance));
                    AccountUtils.printDivider();

                    // Savings minimum balance warning
                    if (type.equals("SAVINGS") && balance < 1000.00) {
                        System.out.println(
                            "  ⚠ WARNING: Balance below minimum (₹1,000). " +
                            "Please deposit funds."
                        );
                    }

                } else {
                    System.out.println(
                        "  ✗ Account '" + accountNumber + "' not found.");
                }
            }

        } catch (SQLException e) {
            System.out.println("  ✗ Error fetching balance: " + e.getMessage());
        }
    }


    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 14 — TRANSACTION HISTORY
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Displays full transaction history for an account.
     * Traverses ResultSet row by row using while(rs.next()).
     *
     * @param sc Scanner from Main.java
     */
    public void showTransactionHistory(Scanner sc) {

        AccountUtils.printHeader("Transaction History");

        System.out.print("  Enter Account Number : ");
        String accountNumber = sc.nextLine().trim();

        // First verify the account exists
        if (!accountExists(accountNumber)) {
            System.out.println("  ✗ Account not found: " + accountNumber);
            return;
        }

        try {
            // Fetch all transactions where this account is sender OR receiver
            String sql =
                "SELECT transaction_id, sender_account, receiver_account, " +
                "       amount, transaction_type, balance_after, " +
                "       description, transaction_date " +
                "FROM transactions " +
                "WHERE sender_account = ? " +
                "ORDER BY transaction_date DESC " +   // newest first
                "LIMIT 20";                           // last 20 transactions

            Connection conn = DBConnection.getConnection();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountNumber);

                ResultSet rs = ps.executeQuery();

                // ── ResultSet Traversal ────────────────────────────────────
                // rs.next() returns true for each row, false when no more rows
                // This is a FORWARD-ONLY cursor — can only go forward
                boolean hasTransactions = false;

                System.out.println();
                System.out.printf(
                    "  %-6s %-16s %-12s %-10s %-14s %s%n",
                    "TxnID", "Date", "Type", "Amount", "Bal After", "Description"
                );
                AccountUtils.printDivider();

                while (rs.next()) {  // Move to next row; false = no more rows
                    hasTransactions = true;

                    int    txnId     = rs.getInt("transaction_id");
                    String type      = rs.getString("transaction_type");
                    double amount    = rs.getDouble("amount");
                    double balAfter  = rs.getDouble("balance_after");
                    String desc      = rs.getString("description");
                    String date      = rs.getString("transaction_date");

                    // Shorten date: "2024-01-15 10:30:00" → "2024-01-15"
                    String shortDate = date != null ? date.substring(0, 10) : "N/A";

                    // Credit or debit indicator
                    String amtStr = type.contains("DEPOSIT") ||
                                    type.contains("CREDIT")
                                    ? String.format("+₹%.2f", amount)
                                    : String.format("-₹%.2f", amount);

                    System.out.printf(
                        "  %-6d %-16s %-14s %-12s ₹%-12.2f %s%n",
                        txnId, shortDate, type, amtStr, balAfter, desc
                    );
                }

                AccountUtils.printDivider();
                if (!hasTransactions) {
                    System.out.println("  No transactions found for this account.");
                }
            }

        } catch (SQLException e) {
            System.out.println("  ✗ Error fetching history: " + e.getMessage());
        }
    }


    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 13 — CHANGE PASSWORD
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Changes the account password after verifying the old one.
     * Uses UPDATE with PreparedStatement.
     *
     * @param sc Scanner from Main.java
     */
    public void changePassword(Scanner sc) {

        AccountUtils.printHeader("Change Password");

        System.out.print("  Enter Account Number  : ");
        String accountNumber = sc.nextLine().trim();

        System.out.print("  Enter Current Password: ");
        String oldPassword = sc.nextLine().trim();

        System.out.print("  Enter New Password    : ");
        String newPassword = sc.nextLine().trim();

        System.out.print("  Confirm New Password  : ");
        String confirmNew = sc.nextLine().trim();

        // Validate new password
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            System.out.println("  ✗ New password must be at least "
                               + MIN_PASSWORD_LENGTH + " characters.");
            return;
        }

        if (!newPassword.equals(confirmNew)) {
            System.out.println("  ✗ New passwords do not match.");
            return;
        }

        if (oldPassword.equals(newPassword)) {
            System.out.println("  ✗ New password must differ from current password.");
            return;
        }

        try {
            // Step 1: Verify old password matches what's in DB
            String verifySQL =
                "SELECT COUNT(*) FROM accounts " +
                "WHERE account_number = ? AND password = ?";

            Connection conn = DBConnection.getConnection();

            try (PreparedStatement verifyPs = conn.prepareStatement(verifySQL)) {
                verifyPs.setString(1, accountNumber);
                verifyPs.setString(2, oldPassword);
                ResultSet rs = verifyPs.executeQuery();

                if (rs.next() && rs.getInt(1) == 0) {
                    System.out.println(
                        "  ✗ Current password is incorrect.");
                    return;
                }
            }

            // Step 2: UPDATE password in DB
            String updateSQL =
                "UPDATE accounts SET password = ? " +
                "WHERE account_number = ? AND password = ?";

            try (PreparedStatement updatePs = conn.prepareStatement(updateSQL)) {
                updatePs.setString(1, newPassword);
                updatePs.setString(2, accountNumber);
                updatePs.setString(3, oldPassword);

                int rows = updatePs.executeUpdate();

                if (rows > 0) {
                    AccountUtils.printDivider();
                    System.out.println("  ✓ Password changed successfully!");
                    System.out.println("  Please use your new password to login.");
                    AccountUtils.printDivider();
                } else {
                    System.out.println("  ✗ Password change failed. Try again.");
                }
            }

        } catch (SQLException e) {
            System.out.println("  ✗ Database error: " + e.getMessage());
        }
    }


    // ══════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Returns true if an account with this number exists in the DB.
     * Used internally before operations that need a valid account.
     */
    private boolean accountExists(String accountNumber) {
        String sql = "SELECT COUNT(*) FROM accounts WHERE account_number = ?";
        try {
            Connection conn = DBConnection.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountNumber);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.out.println("  ✗ DB error checking account: "
                               + e.getMessage());
        }
        return false;
    }
}