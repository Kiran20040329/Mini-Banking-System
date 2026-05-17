import database.DBConnection;
import exceptions.AuthenticationException;
import exceptions.InsufficientBalanceException;
import exceptions.InvalidUserException;
import model.Account;
import service.AccountService;
import service.TransactionService;
import utility.AccountUtils;

import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Main — Entry point for the Mini Banking System.
 *
 * Responsibilities:
 *   - Display menus (pre-login + post-login)
 *   - Manage login session (currentUser Account object)
 *   - Route user choices to correct service methods
 *   - Handle top-level exceptions gracefully
 *
 * OOP Concepts:
 *   - Encapsulation : session state in private static field
 *   - Static methods: menu helpers keep main() clean
 *   - Polymorphism  : BankOperations reference holds TransactionService
 */
public class Main {

    // ── Session State ──────────────────────────────────────────────────────
    // Holds the currently logged-in account.
    // null = no one is logged in.
    // Set on successful login, cleared on logout.
    private static Account currentUser = null;

    // ── Service Layer References ───────────────────────────────────────────
    // Using interface type on left side = Abstraction (polymorphism)
    private static final AccountService     accountService = new AccountService();
    private static final TransactionService txnService     = new TransactionService();

    // ── Shared Scanner ─────────────────────────────────────────────────────
    // One Scanner for the entire app — never create multiple Scanner(System.in)
    private static final Scanner sc = new Scanner(System.in);


    // ══════════════════════════════════════════════════════════════════════
    //  APPLICATION ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {

        // ── Step 1: Test DB connection on startup ──────────────────────────
        // Fail fast — if DB is unreachable, tell user immediately
        DBConnection.testConnection();

        // ── Step 2: Show welcome banner ────────────────────────────────────
        printWelcomeBanner();

        // ── Step 3: Main application loop ─────────────────────────────────
        boolean running = true;

        while (running) {
            try {
                // Show different menus based on login state
                if (currentUser == null) {
                    running = handleGuestMenu();   // Not logged in
                } else {
                    running = handleUserMenu();    // Logged in
                }

            } catch (InputMismatchException e) {
                // User typed letters where a number was expected
                System.out.println("\n  ✗ Invalid input. Please enter a number.");
                sc.nextLine(); // Clear the bad input from Scanner buffer

            } catch (Exception e) {
                // Catch-all for any unexpected errors
                System.out.println("\n  ✗ Unexpected error: " + e.getMessage());
                System.out.println("  Please try again.");
            }
        }

        // ── Step 4: Clean shutdown ─────────────────────────────────────────
        shutdown();
    }


    // ══════════════════════════════════════════════════════════════════════
    //  GUEST MENU (Not Logged In)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Displays menu for users who are not logged in.
     * @return false if user chose Exit, true otherwise (keep loop running)
     */
    private static boolean handleGuestMenu() throws Exception {

        printGuestMenu();

        System.out.print("  Enter choice: ");
        int choice = readIntInput();

        switch (choice) {

            // ── Option 1: Register New Account ────────────────────────────
            case 1 -> {
                accountService.register(sc);
            }

            // ── Option 2: Login ───────────────────────────────────────────
            case 2 -> {
                Account account = accountService.login(sc);
                if (account != null) {
                    currentUser = account; // Set session
                    System.out.println(
                        "\n  You are now logged in as: " +
                        currentUser.getFullName());
                }
            }

            // ── Option 3: Check Balance (no login needed) ─────────────────
            case 3 -> {
                accountService.checkBalance(sc);
            }

            // ── Option 4: Exit ────────────────────────────────────────────
            case 4 -> {
                System.out.println("\n  Thank you for using Mini Banking System.");
                System.out.println("  Goodbye!");
                return false; // Stop the loop
            }

            default -> System.out.println("\n  ✗ Invalid choice. Select 1–4.");
        }

        return true; // Keep loop running
    }


    // ══════════════════════════════════════════════════════════════════════
    //  USER MENU (Logged In)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Displays menu for logged-in users.
     * All operations use currentUser.getAccountNumber() — no manual entry.
     * @return false if user chose Exit, true otherwise
     */
    private static boolean handleUserMenu() throws Exception {

        printUserMenu();

        System.out.print("  Enter choice: ");
        int choice = readIntInput();

        switch (choice) {

            // ── Option 1: Check Balance ───────────────────────────────────
            case 1 -> {
                AccountUtils.printHeader("Your Balance");
                try {
                    double balance = txnService.checkBalance(
                        currentUser.getAccountNumber());

                    // Update the in-memory session balance
                    currentUser.setBalance(balance);

                    System.out.printf(
                        "%n  Account  : %s%n" +
                        "  Name     : %s%n" +
                        "  Type     : %s%n" +
                        "  Balance  : %s%n",
                        currentUser.getAccountNumber(),
                        currentUser.getFullName(),
                        currentUser.getAccountType(),
                        AccountUtils.formatBalance(balance)
                    );
                    AccountUtils.printDivider();
                } catch (Exception e) {
                    System.out.println("  ✗ " + e.getMessage());
                }
            }

            // ── Option 2: Deposit Money ───────────────────────────────────
            case 2 -> {
                AccountUtils.printHeader("Deposit Money");
                System.out.print(
                    "  Depositing to: " +
                    currentUser.getAccountNumber() + "\n");
                System.out.print("  Enter amount : ₹");

                try {
                    double amount = Double.parseDouble(sc.nextLine().trim());
                    txnService.deposit(currentUser.getAccountNumber(), amount);
                } catch (NumberFormatException e) {
                    System.out.println("  ✗ Invalid amount.");
                } catch (InvalidUserException e) {
                    System.out.println("  ✗ Account error: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("  ✗ " + e.getMessage());
                }
            }

            // ── Option 3: Withdraw Money ──────────────────────────────────
            case 3 -> {
                AccountUtils.printHeader("Withdraw Money");
                System.out.printf(
                    "  Account  : %s%n" +
                    "  Balance  : %s%n",
                    currentUser.getAccountNumber(),
                    AccountUtils.formatBalance(
                        txnService.checkBalance(
                            currentUser.getAccountNumber()))
                );
                System.out.print("  Amount   : ₹");

                try {
                    double amount = Double.parseDouble(sc.nextLine().trim());
                    txnService.withdraw(currentUser.getAccountNumber(), amount);
                } catch (NumberFormatException e) {
                    System.out.println("  ✗ Invalid amount.");
                } catch (InsufficientBalanceException e) {
                    System.out.println("  ✗ " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("  ✗ " + e.getMessage());
                }
            }

            // ── Option 4: Transfer Money ──────────────────────────────────
            case 4 -> {
                AccountUtils.printHeader("Transfer Money");
                System.out.printf(
                    "  From     : %s (%s)%n",
                    currentUser.getAccountNumber(),
                    currentUser.getFullName()
                );
                System.out.print("  To (Acc) : ");
                String toAcc = sc.nextLine().trim();

                System.out.print("  Amount   : ₹");

                try {
                    double amount = Double.parseDouble(sc.nextLine().trim());

                    // Confirm before executing irreversible operation
                    System.out.printf(
                        "%n  ┌──────────────────────────────────┐%n" +
                        "  │  FROM    : %-22s│%n" +
                        "  │  TO      : %-22s│%n" +
                        "  │  AMOUNT  : ₹%-21.2f│%n" +
                        "  └──────────────────────────────────┘%n",
                        currentUser.getAccountNumber(), toAcc, amount
                    );
                    System.out.print("  Confirm? (yes/no) : ");
                    String confirm = sc.nextLine().trim().toLowerCase();

                    if (confirm.equals("yes")) {
                        txnService.transfer(
                            currentUser.getAccountNumber(), toAcc, amount);
                    } else {
                        System.out.println("  Transfer cancelled.");
                    }

                } catch (NumberFormatException e) {
                    System.out.println("  ✗ Invalid amount.");
                } catch (InsufficientBalanceException e) {
                    System.out.println("  ✗ " + e.getMessage());
                } catch (InvalidUserException e) {
                    System.out.println("  ✗ " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("  ✗ " + e.getMessage());
                }
            }

            // ── Option 5: Transaction History ─────────────────────────────
            case 5 -> {
                AccountUtils.printHeader("Transaction History");
                // Pass current user's account directly — no manual input
                Scanner mockSc = new Scanner(
                    currentUser.getAccountNumber() + "\n");
                accountService.showTransactionHistory(mockSc);
            }

            // ── Option 6: Change Password ─────────────────────────────────
            case 6 -> {
                accountService.changePassword(sc);
            }

            // ── Option 7: Account Details ─────────────────────────────────
            case 7 -> {
                System.out.println(currentUser.toString());
            }

            // ── Option 8: Logout ──────────────────────────────────────────
            case 8 -> {
                System.out.printf(
                    "%n  Goodbye, %s! You have been logged out.%n",
                    currentUser.getFullName()
                );
                currentUser = null; // Clear session
                System.out.println("  Returning to main menu...");
            }

            // ── Option 9: Exit ────────────────────────────────────────────
            case 9 -> {
                System.out.println(
                    "\n  Thank you for banking with us. Goodbye!");
                return false;
            }

            default -> System.out.println("\n  ✗ Invalid choice. Select 1–9.");
        }

        return true;
    }


    // ══════════════════════════════════════════════════════════════════════
    //  MENU DISPLAY METHODS
    // ══════════════════════════════════════════════════════════════════════

    private static void printWelcomeBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║                                      ║");
        System.out.println("  ║     MINI BANKING SYSTEM v1.0         ║");
        System.out.println("  ║     Java + JDBC + MySQL              ║");
        System.out.println("  ║                                      ║");
        System.out.println("  ║     Secure  •  Reliable  •  Simple   ║");
        System.out.println("  ║                                      ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
    }

    private static void printGuestMenu() {
        System.out.println();
        AccountUtils.printDivider();
        System.out.println("  MAIN MENU");
        AccountUtils.printDivider();
        System.out.println("  1. Register New Account");
        System.out.println("  2. Login");
        System.out.println("  3. Check Balance (Guest)");
        System.out.println("  4. Exit");
        AccountUtils.printDivider();
    }

    private static void printUserMenu() {
        System.out.println();
        AccountUtils.printDivider();
        System.out.printf("  Logged in as: %s%n",
                          currentUser.getFullName());
        System.out.printf("  Account     : %s%n",
                          currentUser.getAccountNumber());
        AccountUtils.printDivider();
        System.out.println("  1. Check Balance");
        System.out.println("  2. Deposit Money");
        System.out.println("  3. Withdraw Money");
        System.out.println("  4. Transfer Money");
        System.out.println("  5. Transaction History");
        System.out.println("  6. Change Password");
        System.out.println("  7. Account Details");
        System.out.println("  8. Logout");
        System.out.println("  9. Exit");
        AccountUtils.printDivider();
    }


    // ══════════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Reads an integer from Scanner safely.
     * Consumes the leftover newline after nextInt() with nextLine().
     * Throws InputMismatchException if input is not a number
     * (caught in main loop).
     */
    private static int readIntInput() {
        // Using nextLine() + parseInt is safer than nextInt()
        // nextInt() leaves '\n' in buffer causing empty nextLine() reads
        try {
            return Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException e) {
            throw new InputMismatchException("Not a valid menu number.");
        }
    }

    /**
     * Graceful shutdown — close DB connection + Scanner.
     * Called when the main loop exits normally.
     */
    private static void shutdown() {
        System.out.println("\n  [ Closing database connection... ]");
        DBConnection.closeConnection();
        sc.close();
        System.out.println("  [ System shutdown complete. ]");
        System.out.println("  [ Thank you for using Mini Banking System. ]\n");
    }
}