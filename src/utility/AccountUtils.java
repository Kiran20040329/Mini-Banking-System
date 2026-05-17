package utility;

/**
 * AccountUtils — Static utility class.
 *
 * OOP Concepts:
 *   - Static methods  : called on class, not instances
 *   - Static counter  : shared across entire application
 *   - final           : START value is immutable
 */
public class AccountUtils {

    // Static counter — shared across all calls, persists in memory
    private static int counter = 100001;

    // Starting prefix for all account numbers
    private static final String PREFIX = "ACC";

    // Private constructor — utility classes should not be instantiated
    private AccountUtils() {}

    /**
     * Generates the next unique account number.
     * Example: ACC100001, ACC100002, ACC100003...
     *
     * In production: query MAX(account_number) from DB instead
     * to survive application restarts.
     */
    public static String generateAccountNumber() {
        return PREFIX + counter++;
    }

    /**
     * Formats a double balance as Indian Rupee string.
     * Example: 50000.0 → "₹50,000.00"
     */
    public static String formatBalance(double balance) {
        return String.format("₹%,.2f", balance);
    }

    /**
     * Prints a formatted section divider to console.
     */
    public static void printDivider() {
        System.out.println("─".repeat(38));
    }

    /**
     * Prints a formatted header banner.
     */
    public static void printHeader(String title) {
        printDivider();
        System.out.printf("  %s%n", title.toUpperCase());
        printDivider();
    }

    /**
     * Validates that a string is not null or empty.
     */
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}