package service;

import database.DBConnection;
import exceptions.InsufficientBalanceException;
import exceptions.InvalidUserException;
import operations.BankOperations;
import utility.AccountUtils;

import java.sql.*;
import java.util.Scanner;

/**
 * TransactionService — Handles all money operations.
 * Implements BankOperations interface (Abstraction).
 *
 * OOP Concepts:
 *   - Implements interface  : BankOperations (abstraction)
 *   - Encapsulation         : private helper methods
 *   - final constants       : MIN_BALANCE, MAX_TRANSFER_LIMIT
 *
 * JDBC + ACID Concepts:
 *   - PreparedStatement     : SQL injection prevention
 *   - setAutoCommit(false)  : begin transaction manually
 *   - commit()              : make all changes permanent (Durability)
 *   - rollback()            : undo all changes on failure (Atomicity)
 *   - Isolation levels      : prevent concurrent conflicts
 */
public class TransactionService implements BankOperations {

    // ── Business Rule Constants ────────────────────────────────────────────
    private static final double SAVINGS_MIN_BALANCE  = 1000.00;
    private static final double CURRENT_MIN_BALANCE  =    0.00;
    private static final double MAX_DEPOSIT_LIMIT    = 1_000_000.00; // ₹10 lakh
    private static final double MAX_WITHDRAWAL_LIMIT =   100_000.00; // ₹1 lakh/day
    private static final double MAX_TRANSFER_LIMIT   =    50_000.00; // ₹50k/txn


    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 10 — DEPOSIT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Deposits money into an account.
     * Updates balance + logs transaction, both in one unit of work.
     */
    @Override
    public void deposit(String accountNumber, double amount) throws Exception {

        // ── Step 1: Validate input ─────────────────────────────────────────
        validateAccount(accountNumber);
        if (amount <= 0)
            throw new IllegalArgumentException(
                "Deposit amount must be positive.");
        if (amount > MAX_DEPOSIT_LIMIT)
            throw new IllegalArgumentException(
                String.format("Maximum deposit limit is %s.",
                    AccountUtils.formatBalance(MAX_DEPOSIT_LIMIT)));

        Connection conn = DBConnection.getConnection();

        // setAutoCommit(false) — begin manual transaction
        // Even for deposit: if UPDATE succeeds but INSERT fails,
        // we roll back the UPDATE too. Both must succeed together.
        conn.setAutoCommit(false);

        try {
            // ── Step 2: UPDATE balance ─────────────────────────────────────
            // balance = balance + amount  (never set to a fixed value —
            // that would overwrite concurrent deposits!)
            String updateSQL =
                "UPDATE accounts " +
                "SET balance = balance + ?, updated_at = NOW() " +
                "WHERE account_number = ? AND is_active = 1";

            double newBalance; // We'll need this for the transaction log

            try (PreparedStatement updatePs = conn.prepareStatement(updateSQL)) {
                updatePs.setDouble(1, amount);
                updatePs.setString(2, accountNumber);

                int rowsAffected = updatePs.executeUpdate();

                // If 0 rows updated → account not found or is frozen
                if (rowsAffected == 0) {
                    conn.rollback();
                    throw new InvalidUserException(
                        "Account not found or is frozen: " + accountNumber);
                }
            }

            // ── Step 3: Fetch updated balance for log ──────────────────────
            // We read the NEW balance from DB (not calculated in Java)
            // This is safer — avoids race conditions in concurrent use
            newBalance = fetchBalance(accountNumber, conn);

            // ── Step 4: INSERT transaction log ────────────────────────────
            String logSQL =
                "INSERT INTO transactions " +
                "(sender_account, receiver_account, amount, " +
                " transaction_type, balance_after, description) " +
                "VALUES (?, NULL, ?, 'DEPOSIT', ?, ?)";

            try (PreparedStatement logPs = conn.prepareStatement(logSQL)) {
                logPs.setString(1, accountNumber);
                logPs.setDouble(2, amount);
                logPs.setDouble(3, newBalance);
                logPs.setString(4, "Cash deposit at branch");
                logPs.executeUpdate();
            }

            // ── Step 5: COMMIT — make both changes permanent ───────────────
            // Durability: from this point, data survives any crash
            conn.commit();

            // ── Step 6: Display receipt ────────────────────────────────────
            printDepositReceipt(accountNumber, amount, newBalance);

        } catch (SQLException e) {
            // Any SQL failure → rollback BOTH the UPDATE and INSERT
            conn.rollback();
            throw new Exception("Deposit failed. All changes rolled back.\n"
                                + "Reason: " + e.getMessage());
        } finally {
            // ALWAYS restore auto-commit — even if an exception occurred
            conn.setAutoCommit(true);
        }
    }

    /** Scanner-based wrapper for deposit — called from Main.java */
    public void depositInteractive(Scanner sc) {
        AccountUtils.printHeader("Deposit Money");
        try {
            System.out.print("  Enter Account Number : ");
            String accNo = sc.nextLine().trim();

            System.out.print("  Enter Deposit Amount : ₹");
            double amount = Double.parseDouble(sc.nextLine().trim());

            deposit(accNo, amount);

        } catch (NumberFormatException e) {
            System.out.println("  ✗ Invalid amount. Enter digits only.");
        } catch (Exception e) {
            System.out.println("  ✗ " + e.getMessage());
        }
    }

    // ── Private: Print deposit receipt ────────────────────────────────────
    private void printDepositReceipt(String accNo,
                                     double amount,
                                     double newBalance) {
        AccountUtils.printDivider();
        System.out.println("  DEPOSIT SUCCESSFUL ✓");
        AccountUtils.printDivider();
        System.out.printf ("  Account    : %s%n",   accNo);
        System.out.printf ("  Deposited  : %s%n",
                           AccountUtils.formatBalance(amount));
        System.out.printf ("  New Balance: %s%n",
                           AccountUtils.formatBalance(newBalance));
        System.out.printf ("  Time       : %s%n",
                           new java.util.Date());
        AccountUtils.printDivider();
    }


    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 11 — WITHDRAW
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Withdraws money from an account.
     * Enforces:
     *   - Minimum balance rule (SAVINGS: ₹1000, CURRENT: ₹0)
     *   - Maximum withdrawal limit per transaction
     *   - Account must be active
     *
     * ACID: UPDATE + INSERT in one transaction — rolled back together on fail.
     */
    @Override
    public void withdraw(String accountNumber, double amount) throws Exception {

        // ── Step 1: Input validation ───────────────────────────────────────
        validateAccount(accountNumber);
        if (amount <= 0)
            throw new IllegalArgumentException(
                "Withdrawal amount must be positive.");
        if (amount > MAX_WITHDRAWAL_LIMIT)
            throw new IllegalArgumentException(
                String.format("Maximum withdrawal per transaction: %s.",
                    AccountUtils.formatBalance(MAX_WITHDRAWAL_LIMIT)));

        Connection conn = DBConnection.getConnection();

        // Begin transaction — both UPDATE and INSERT must succeed together
        conn.setAutoCommit(false);

        try {
            // ── Step 2: Fetch CURRENT balance + account type ───────────────
            // Must read balance INSIDE the transaction to prevent
            // another concurrent withdrawal from reading stale data
            AccountSnapshot snapshot = fetchAccountSnapshot(accountNumber, conn);

            if (!snapshot.isActive) {
                conn.rollback();
                throw new InvalidUserException(
                    "Account is frozen. Withdrawal not allowed.");
            }

            // ── Step 3: Determine minimum balance by account type ──────────
            double minBalance = snapshot.accountType.equals("SAVINGS")
                                ? SAVINGS_MIN_BALANCE
                                : CURRENT_MIN_BALANCE;

            double balanceAfterWithdrawal = snapshot.balance - amount;

            // ── Step 4: CONSISTENCY check — enforce business rules ─────────
            // This is the C in ACID: prevent invalid states
            if (balanceAfterWithdrawal < minBalance) {
                conn.rollback();
                throw new InsufficientBalanceException(
                    String.format(
                        "Insufficient balance.%n" +
                        "  Current Balance  : %s%n" +
                        "  Withdrawal Amount: %s%n" +
                        "  Minimum Required : %s%n" +
                        "  Maximum Allowed  : %s",
                        AccountUtils.formatBalance(snapshot.balance),
                        AccountUtils.formatBalance(amount),
                        AccountUtils.formatBalance(minBalance),
                        AccountUtils.formatBalance(
                            snapshot.balance - minBalance)
                    ),
                    snapshot.balance,
                    amount
                );
            }

            // ── Step 5: UPDATE balance — deduct amount ─────────────────────
            String updateSQL =
                "UPDATE accounts " +
                "SET balance = balance - ?, updated_at = NOW() " +
                "WHERE account_number = ? AND is_active = 1";

            try (PreparedStatement updatePs = conn.prepareStatement(updateSQL)) {
                updatePs.setDouble(1, amount);
                updatePs.setString(2, accountNumber);
                int rows = updatePs.executeUpdate();

                if (rows == 0) {
                    conn.rollback();
                    throw new InvalidUserException(
                        "Account not found or inactive.");
                }
            }

            // ── Step 6: INSERT transaction log ─────────────────────────────
            String logSQL =
                "INSERT INTO transactions " +
                "(sender_account, receiver_account, amount, " +
                " transaction_type, balance_after, description) " +
                "VALUES (?, NULL, ?, 'WITHDRAWAL', ?, ?)";

            try (PreparedStatement logPs = conn.prepareStatement(logSQL)) {
                logPs.setString(1, accountNumber);
                logPs.setDouble(2, amount);
                logPs.setDouble(3, balanceAfterWithdrawal);
                logPs.setString(4, "Cash withdrawal");
                logPs.executeUpdate();
            }

            // ── Step 7: COMMIT both changes ────────────────────────────────
            conn.commit();

            // ── Step 8: Print receipt ──────────────────────────────────────
            printWithdrawalReceipt(accountNumber, amount,
                                   balanceAfterWithdrawal);

        } catch (InsufficientBalanceException | InvalidUserException e) {
            conn.rollback(); // Domain exceptions → rollback + rethrow
            throw e;
        } catch (SQLException e) {
            conn.rollback();
            throw new Exception("Withdrawal failed. Rolled back.\n"
                                + "Reason: " + e.getMessage());
        } finally {
            conn.setAutoCommit(true); // Always restore
        }
    }

    /** Scanner-based wrapper for withdraw — called from Main.java */
    public void withdrawInteractive(Scanner sc) {
        AccountUtils.printHeader("Withdraw Money");
        try {
            System.out.print("  Enter Account Number : ");
            String accNo = sc.nextLine().trim();

            System.out.print("  Enter Withdrawal Amount : ₹");
            double amount = Double.parseDouble(sc.nextLine().trim());

            withdraw(accNo, amount);

        } catch (NumberFormatException e) {
            System.out.println("  ✗ Invalid amount. Enter digits only.");
        } catch (InsufficientBalanceException e) {
            System.out.println("  ✗ " + e.getMessage());
        } catch (Exception e) {
            System.out.println("  ✗ " + e.getMessage());
        }
    }

    // ── Private: Print withdrawal receipt ─────────────────────────────────
    private void printWithdrawalReceipt(String accNo,
                                        double amount,
                                        double newBalance) {
        AccountUtils.printDivider();
        System.out.println("  WITHDRAWAL SUCCESSFUL ✓");
        AccountUtils.printDivider();
        System.out.printf ("  Account     : %s%n",  accNo);
        System.out.printf ("  Withdrawn   : %s%n",
                           AccountUtils.formatBalance(amount));
        System.out.printf ("  New Balance : %s%n",
                           AccountUtils.formatBalance(newBalance));
        System.out.printf ("  Time        : %s%n",
                           new java.util.Date());
        AccountUtils.printDivider();
    }


    // ══════════════════════════════════════════════════════════════════════
    //  PHASE 12 — TRANSFER MONEY (Full ACID Implementation)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Transfers money between two accounts.
     *
     * ACID Properties fully demonstrated here:
     *
     *  A — ATOMICITY   : All 4 SQL steps succeed or ALL are rolled back.
     *                    Partial transfer (debit without credit) is impossible.
     *
     *  C — CONSISTENCY : Total money in system stays constant.
     *                    Sender cannot go below minimum balance.
     *                    Receiver account must exist.
     *
     *  I — ISOLATION   : TRANSACTION_SERIALIZABLE prevents two concurrent
     *                    transfers from reading stale balances.
     *
     *  D — DURABILITY  : conn.commit() writes permanently to MySQL InnoDB
     *                    WAL (Write-Ahead Log). Survives crashes.
     */
    @Override
    public void transfer(String senderAccount,
                         String receiverAccount,
                         double amount) throws Exception {

        // ── Step 1: Input Validation ───────────────────────────────────────
        validateAccount(senderAccount);
        validateAccount(receiverAccount);

        if (senderAccount.equalsIgnoreCase(receiverAccount))
            throw new IllegalArgumentException(
                "Cannot transfer to the same account.");

        if (amount <= 0)
            throw new IllegalArgumentException(
                "Transfer amount must be positive.");

        if (amount > MAX_TRANSFER_LIMIT)
            throw new IllegalArgumentException(
                String.format("Maximum transfer per transaction: %s.",
                    AccountUtils.formatBalance(MAX_TRANSFER_LIMIT)));

        Connection conn = DBConnection.getConnection();

        // ── Step 2: Set Isolation Level ────────────────────────────────────
        // SERIALIZABLE: Highest isolation level.
        // Prevents:
        //   - Dirty Reads    : reading uncommitted data from another transaction
        //   - Non-Repeatable : data changes between two reads in same txn
        //   - Phantom Reads  : new rows appear between two reads in same txn
        // Cost: Lower concurrency (rows are locked). Correct for banking.
        conn.setTransactionIsolation(
            Connection.TRANSACTION_SERIALIZABLE);

        // ── Step 3: Begin Transaction ──────────────────────────────────────
        // ATOMICITY begins here — nothing auto-saves until conn.commit()
        conn.setAutoCommit(false);

        System.out.println("\n  [ Transfer initiated... ]");

        try {
            // ── Step 4: Verify SENDER account exists and is active ─────────
            AccountSnapshot sender = fetchAccountSnapshot(senderAccount, conn);

            if (!sender.isActive)
                throw new InvalidUserException(
                    "Sender account is frozen.");

            // ── Step 5: CONSISTENCY — check sender has enough balance ───────
            double senderMinBalance = sender.accountType.equals("SAVINGS")
                                      ? SAVINGS_MIN_BALANCE
                                      : CURRENT_MIN_BALANCE;

            double senderBalanceAfter = sender.balance - amount;

            if (senderBalanceAfter < senderMinBalance) {
                throw new InsufficientBalanceException(
                    String.format(
                        "Insufficient balance for transfer.%n" +
                        "  Your Balance  : %s%n" +
                        "  Transfer Amt  : %s%n" +
                        "  Min Required  : %s%n" +
                        "  Max You Can   : %s",
                        AccountUtils.formatBalance(sender.balance),
                        AccountUtils.formatBalance(amount),
                        AccountUtils.formatBalance(senderMinBalance),
                        AccountUtils.formatBalance(
                            sender.balance - senderMinBalance)
                    ),
                    sender.balance,
                    amount
                );
            }

            // ── Step 6: Verify RECEIVER exists and is active ───────────────
            AccountSnapshot receiver = fetchAccountSnapshot(receiverAccount, conn);

            if (!receiver.isActive)
                throw new InvalidUserException(
                    "Receiver account is frozen. Transfer cancelled.");

            System.out.println("  [ Receiver verified ✓ ]");
            System.out.println("  [ Debiting sender...  ]");

            // ── Step 7: DEBIT sender ───────────────────────────────────────
            // SQL 1 of 4
            String debitSQL =
                "UPDATE accounts " +
                "SET balance = balance - ?, updated_at = NOW() " +
                "WHERE account_number = ? AND is_active = 1";

            try (PreparedStatement debitPs = conn.prepareStatement(debitSQL)) {
                debitPs.setDouble(1, amount);
                debitPs.setString(2, senderAccount);
                int rows = debitPs.executeUpdate();
                if (rows == 0) throw new InvalidUserException(
                    "Sender debit failed — account issue.");
            }

            System.out.println("  [ Sender debited ✓    ]");
            System.out.println("  [ Crediting receiver...]");

            // ── Step 8: CREDIT receiver ────────────────────────────────────
            // SQL 2 of 4
            String creditSQL =
                "UPDATE accounts " +
                "SET balance = balance + ?, updated_at = NOW() " +
                "WHERE account_number = ? AND is_active = 1";

            try (PreparedStatement creditPs = conn.prepareStatement(creditSQL)) {
                creditPs.setDouble(1, amount);
                creditPs.setString(2, receiverAccount);
                int rows = creditPs.executeUpdate();
                if (rows == 0) {
                    // Receiver not found — rollback debit immediately
                    throw new InvalidUserException(
                        "Receiver account '" + receiverAccount +
                        "' not found or frozen. Transfer cancelled.");
                }
            }

            System.out.println("  [ Receiver credited ✓ ]");
            System.out.println("  [ Logging transaction...]");

            // ── Step 9: LOG debit side of transaction ──────────────────────
            // SQL 3 of 4
            double senderNewBalance   = fetchBalance(senderAccount, conn);
            double receiverNewBalance = fetchBalance(receiverAccount, conn);

            String debitLogSQL =
                "INSERT INTO transactions " +
                "(sender_account, receiver_account, amount, " +
                " transaction_type, balance_after, description) " +
                "VALUES (?, ?, ?, 'TRANSFER_DEBIT', ?, ?)";

            try (PreparedStatement logPs = conn.prepareStatement(debitLogSQL)) {
                logPs.setString(1, senderAccount);
                logPs.setString(2, receiverAccount);
                logPs.setDouble(3, amount);
                logPs.setDouble(4, senderNewBalance);
                logPs.setString(5, "Transfer to " + receiverAccount);
                logPs.executeUpdate();
            }

            // ── Step 10: LOG credit side of transaction ────────────────────
            // SQL 4 of 4 — records credit from receiver's perspective
            String creditLogSQL =
                "INSERT INTO transactions " +
                "(sender_account, receiver_account, amount, " +
                " transaction_type, balance_after, description) " +
                "VALUES (?, ?, ?, 'TRANSFER_CREDIT', ?, ?)";

            try (PreparedStatement logPs = conn.prepareStatement(creditLogSQL)) {
                logPs.setString(1, receiverAccount);
                logPs.setString(2, senderAccount);
                logPs.setDouble(3, amount);
                logPs.setDouble(4, receiverNewBalance);
                logPs.setString(5, "Transfer from " + senderAccount);
                logPs.executeUpdate();
            }

            // ── Step 11: COMMIT — DURABILITY ──────────────────────────────
            // All 4 SQL statements succeeded.
            // conn.commit() writes PERMANENTLY to MySQL InnoDB disk.
            // Even if the JVM crashes 1ms after this line,
            // the transfer is saved. MySQL WAL guarantees it.
            conn.commit();

            System.out.println("  [ Transaction committed ✓ ]");

            // ── Step 12: Print transfer receipt ────────────────────────────
            printTransferReceipt(
                senderAccount, receiverAccount, amount,
                senderNewBalance, receiverNewBalance,
                sender.fullName, receiver.fullName
            );

        } catch (InsufficientBalanceException | InvalidUserException e) {
            // Known domain exceptions
            conn.rollback(); // ATOMICITY: undo everything
            System.out.println("  [ Transaction rolled back ✗ ]");
            throw e;

        } catch (SQLException e) {
            // Unexpected DB errors
            conn.rollback(); // ATOMICITY: undo everything
            System.out.println("  [ Transaction rolled back ✗ ]");
            throw new Exception(
                "Transfer failed due to database error. " +
                "All changes rolled back. No money was moved.\n" +
                "Reason: " + e.getMessage()
            );
        } finally {
            // ALWAYS restore auto-commit and isolation level
            // Even if an exception skipped the try block
            conn.setAutoCommit(true);
            conn.setTransactionIsolation(
                Connection.TRANSACTION_READ_COMMITTED); // MySQL default
        }
    }

    /** Scanner-based wrapper for transfer — called from Main.java */
    public void transferInteractive(Scanner sc) {
        AccountUtils.printHeader("Transfer Money");
        try {
            System.out.print("  Your Account Number      : ");
            String from = sc.nextLine().trim();

            System.out.print("  Receiver Account Number  : ");
            String to = sc.nextLine().trim();

            System.out.print("  Transfer Amount          : ₹");
            double amount = Double.parseDouble(sc.nextLine().trim());

            // Confirmation step — good UX for irreversible operations
            System.out.printf("%n  ┌─────────────────────────────────┐%n");
            System.out.printf("  │  FROM    : %-21s│%n", from);
            System.out.printf("  │  TO      : %-21s│%n", to);
            System.out.printf("  │  AMOUNT  : ₹%-20.2f│%n", amount);
            System.out.printf("  └─────────────────────────────────┘%n");
            System.out.print("  Confirm transfer? (yes/no) : ");
            String confirm = sc.nextLine().trim().toLowerCase();

            if (!confirm.equals("yes")) {
                System.out.println("  Transfer cancelled by user.");
                return;
            }

            transfer(from, to, amount);

        } catch (NumberFormatException e) {
            System.out.println("  ✗ Invalid amount. Enter digits only.");
        } catch (InsufficientBalanceException e) {
            System.out.println("  ✗ " + e.getMessage());
        } catch (InvalidUserException e) {
            System.out.println("  ✗ " + e.getMessage());
        } catch (Exception e) {
            System.out.println("  ✗ " + e.getMessage());
        }
    }

    // ── Private: Print transfer receipt ───────────────────────────────────
    private void printTransferReceipt(String fromAcc, String toAcc,
                                      double amount,
                                      double senderBalance,
                                      double receiverBalance,
                                      String senderName,
                                      String receiverName) {
        AccountUtils.printDivider();
        System.out.println("  TRANSFER SUCCESSFUL ✓");
        AccountUtils.printDivider();
        System.out.printf ("  From        : %s (%s)%n", fromAcc, senderName);
        System.out.printf ("  To          : %s (%s)%n", toAcc, receiverName);
        System.out.printf ("  Amount      : %s%n",
                           AccountUtils.formatBalance(amount));
        System.out.printf ("  Your Balance: %s%n",
                           AccountUtils.formatBalance(senderBalance));
        System.out.printf ("  Time        : %s%n",
                           new java.util.Date());
        AccountUtils.printDivider();
    }


    // ══════════════════════════════════════════════════════════════════════
    //  INTERFACE METHOD — checkBalance
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public double checkBalance(String accountNumber) throws Exception {
        validateAccount(accountNumber);
        Connection conn = DBConnection.getConnection();
        return fetchBalance(accountNumber, conn);
    }


    // ══════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Fetches just the balance for an account.
     * Accepts Connection parameter — called INSIDE open transactions
     * so it uses the same connection (sees uncommitted changes).
     */
    private double fetchBalance(String accountNumber,
                                Connection conn) throws SQLException {
        String sql = "SELECT balance FROM accounts " +
                     "WHERE account_number = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
            throw new SQLException("Account not found: " + accountNumber);
        }
    }

    /**
     * Inner static class — bundles multiple account fields together.
     * Avoids making multiple DB calls to fetch id, name, balance, type.
     * This is a lightweight DTO (Data Transfer Object) pattern.
     */
    private static class AccountSnapshot {
        String fullName;
        String accountType;
        double balance;
        boolean isActive;

        AccountSnapshot(String fullName, String accountType,
                        double balance, boolean isActive) {
            this.fullName    = fullName;
            this.accountType = accountType;
            this.balance     = balance;
            this.isActive    = isActive;
        }
    }

    /**
     * Fetches a full account snapshot in ONE query.
     * Called inside open transactions — uses same Connection.
     */
    private AccountSnapshot fetchAccountSnapshot(String accountNumber,
                                                  Connection conn)
            throws SQLException, InvalidUserException {

        String sql =
            "SELECT full_name, account_type, balance, is_active " +
            "FROM accounts " +
            "WHERE account_number = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new AccountSnapshot(
                    rs.getString("full_name"),
                    rs.getString("account_type"),
                    rs.getDouble("balance"),
                    rs.getInt("is_active") == 1
                );
            }
            throw new InvalidUserException(
                "Account not found: " + accountNumber);
        }
    }

    /**
     * Validates account number format before any DB call.
     * Prevents empty/null account numbers reaching SQL layer.
     */
    private void validateAccount(String accountNumber) {
        if (AccountUtils.isNullOrEmpty(accountNumber))
            throw new IllegalArgumentException(
                "Account number cannot be blank.");
        if (!accountNumber.startsWith("ACC"))
            throw new IllegalArgumentException(
                "Invalid account number format. Expected: ACC######");
    }
}