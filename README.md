# Mini-Banking-System
Mini Banking System — Console-based app using Core Java, JDBC &amp; MySQL. Implements OOP principles, ACID-compliant transactions with rollback safety, SQL injection prevention using PreparedStatement, and exception handling. Features include registration, login, deposit, withdrawal, fund transfer, transaction history, and password management.
# 🏦 Mini Banking System

<div align="center">

**A console-based banking application built with Core Java, JDBC, and MySQL.**  
Demonstrates all 4 OOP principles, ACID-compliant transactions, SQL injection prevention,  
and production-grade layered architecture.

[Features](#-features) · [Tech Stack](#-tech-stack) · [Project Structure](#-project-structure) · [Setup](#-installation--setup) · [Usage](#-usage) · [OOP](#-oop-principles) · [ACID](#-acid-properties) · [Interview Q&A](#-interview-preparation) · [Future Plans](#-future-enhancements)

</div>

---

## ✨ Features

| Module | Description |
|--------|-------------|
| 👤 **User Registration** | Auto-generated account number, account type selection (Savings/Current), input validation, duplicate username check |
| 🔐 **Secure Login** | Username + password authentication, 3-attempt lockout, session management |
| 💰 **Check Balance** | Live database read, minimum balance warning, account status display |
| ⬆️ **Deposit Money** | UPDATE + INSERT in one ACID transaction, max deposit limit enforced |
| ⬇️ **Withdraw Money** | Minimum balance enforcement per account type, max withdrawal cap |
| 🔄 **Transfer Money** | Full 4-step ACID transfer — debit, credit, dual log — with complete rollback on any failure |
| 📋 **Transaction History** | ResultSet traversal, formatted ledger view, newest-first, last 20 entries |
| 🔑 **Change Password** | Old password verification, new password validation, PreparedStatement UPDATE |
| 🛡️ **SQL Injection Safe** | PreparedStatement used for every single SQL query throughout the codebase |
| ⚠️ **Custom Exceptions** | `InsufficientBalanceException`, `InvalidUserException`, `AuthenticationException` with context data |

---

## 🛠 Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 LTS | Core language |
| JDBC | 4.2 | Database connectivity API |
| MySQL | 8.0 | Relational database (InnoDB engine) |
| MySQL Connector/J | 8.x | JDBC driver JAR |
| IntelliJ IDEA | Community | IDE |

---

## 📁 Project Structure

```
MiniBankingSystem/
│
├── src/
│   │
│   ├── database/
│   │   └── DBConnection.java           ← Singleton JDBC connection manager
│   │
│   ├── model/
│   │   ├── Account.java                ← Base class — encapsulation, getters/setters
│   │   ├── SavingsAccount.java         ← Extends Account — min balance, interest rate
│   │   └── CurrentAccount.java         ← Extends Account — overdraft limit
│   │
│   ├── operations/
│   │   ├── BankOperations.java         ← Interface — deposit, withdraw, transfer, balance
│   │   └── TransactionOperations.java  ← Interface — history, logging contract
│   │
│   ├── service/
│   │   ├── AccountService.java         ← Register, login, history, change password
│   │   └── TransactionService.java     ← Deposit, withdraw, ACID transfer
│   │
│   ├── exceptions/
│   │   ├── InsufficientBalanceException.java
│   │   ├── InvalidUserException.java
│   │   └── AuthenticationException.java
│   │
│   ├── utility/
│   │   └── AccountUtils.java           ← Account number generator, formatter, helpers
│   │
│   └── Main.java                       ← Entry point — menu, session, Scanner loop
│
├── lib/
│   └── mysql-connector-j-8.x.x.jar    ← JDBC driver (add as library in IntelliJ)
│
├── sql/
│   └── banking_system.sql              ← Complete DB setup script
│
└── README.md
```

---

## 🗄️ Database Schema

### `accounts` Table

```sql
CREATE TABLE accounts (
    id               INT            AUTO_INCREMENT PRIMARY KEY,
    full_name        VARCHAR(100)   NOT NULL,
    account_number   VARCHAR(20)    NOT NULL UNIQUE,
    username         VARCHAR(50)    NOT NULL UNIQUE,
    password         VARCHAR(100)   NOT NULL,
    balance          DECIMAL(15,2)  NOT NULL DEFAULT 1000.00,
    account_type     VARCHAR(20)    NOT NULL DEFAULT 'SAVINGS',
    is_active        TINYINT(1)     NOT NULL DEFAULT 1,
    created_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

> **Why `DECIMAL(15,2)` and not `double`?**  
> `double` has floating-point imprecision — `0.1 + 0.2 = 0.30000000000000004`.  
> For money, even 1 paisa error per transaction is unacceptable. `DECIMAL` stores exact values.

### `transactions` Table

```sql
CREATE TABLE transactions (
    transaction_id    INT            AUTO_INCREMENT PRIMARY KEY,
    sender_account    VARCHAR(20)    NOT NULL,
    receiver_account  VARCHAR(20)    DEFAULT NULL,
    amount            DECIMAL(15,2)  NOT NULL,
    transaction_type  ENUM('DEPOSIT','WITHDRAWAL','TRANSFER_DEBIT','TRANSFER_CREDIT') NOT NULL,
    balance_after     DECIMAL(15,2)  NOT NULL,
    description       VARCHAR(255)   DEFAULT 'No description',
    status            ENUM('SUCCESS','FAILED','PENDING') NOT NULL DEFAULT 'SUCCESS',
    transaction_date  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sender FOREIGN KEY (sender_account)
        REFERENCES accounts(account_number) ON DELETE RESTRICT ON UPDATE CASCADE
);
```

---

## ⚙️ Installation & Setup

### Prerequisites

- [JDK 17+](https://adoptium.net) — download Eclipse Temurin LTS
- [MySQL Server 8.0](https://dev.mysql.com/downloads/installer/)
- [MySQL Workbench](https://dev.mysql.com/downloads/workbench/)
- [IntelliJ IDEA Community](https://www.jetbrains.com/idea/download/)
- [MySQL Connector/J JAR](https://dev.mysql.com/downloads/connector/j/)

---

### Step 1 — Clone the Repository

```bash
git clone https://github.com/yourusername/MiniBankingSystem.git
cd MiniBankingSystem
```

---

### Step 2 — Setup the Database

Open **MySQL Workbench**, connect to `localhost:3306`, open a new query tab, and run:

```sql
-- Create and select database
CREATE DATABASE IF NOT EXISTS banking_system;
USE banking_system;

-- Run the full setup script
source sql/banking_system.sql;

-- Verify
SHOW TABLES;
SELECT * FROM accounts;
```

Or from terminal:

```bash
mysql -u root -p < sql/banking_system.sql
```

---

### Step 3 — Configure Database Credentials

Open `src/database/DBConnection.java` and update lines 10–11:

```java
private static final String DB_USERNAME = "root";
private static final String DB_PASSWORD = "your_mysql_password_here";
```

---

### Step 4 — Add JDBC Driver in IntelliJ

```
1. Copy  mysql-connector-j-8.x.x.jar  →  lib/ folder in the project
2. In IntelliJ: right-click the JAR file
3. Add as Library → Level: Project → OK
4. Verify: File → Project Structure → Libraries → mysql-connector-j listed ✓
```

---

### Step 5 — Run the Application

**IntelliJ:**
```
Right-click Main.java → Run 'Main'
```

**Terminal / Command Prompt:**
```bash
# Compile
javac -cp "lib/mysql-connector-j-8.x.x.jar" -d out src/**/*.java

# Run (Windows)
java -cp "out;lib/mysql-connector-j-8.x.x.jar" Main

# Run (Mac/Linux)
java -cp "out:lib/mysql-connector-j-8.x.x.jar" Main
```

---

## 🚀 Usage

### Sample Login Credentials (pre-loaded)

| Username | Password | Account Number | Balance | Type |
|----------|----------|----------------|---------|------|
| alice | alice@123 | ACC100001 | ₹50,000.00 | SAVINGS |
| bob | bob@456 | ACC100002 | ₹30,000.00 | CURRENT |
| charlie | charlie@789 | ACC100003 | ₹10,000.00 | SAVINGS |

### Application Flow

```
Launch Main.java
│
├── [Guest Menu]
│   ├── 1. Register New Account
│   ├── 2. Login
│   ├── 3. Check Balance (guest)
│   └── 4. Exit
│
└── [User Menu — after login]
    ├── 1. Check Balance
    ├── 2. Deposit Money
    ├── 3. Withdraw Money
    ├── 4. Transfer Money      ← Full ACID transaction
    ├── 5. Transaction History
    ├── 6. Change Password
    ├── 7. Account Details
    ├── 8. Logout
    └── 9. Exit
```

### Sample Console Output

```
╔══════════════════════════════════════╗
║     MINI BANKING SYSTEM v1.0         ║
║     Java + JDBC + MySQL              ║
╚══════════════════════════════════════╝

[ DB ] Connection established successfully.

──────────────────────────────────────
  SECURE LOGIN
──────────────────────────────────────
  Username : alice
  Password : ••••••••
──────────────────────────────────────
  ✓ Login successful!
  Welcome back, Alice Johnson!
  Account  : ACC100001  |  Balance : ₹50,000.00
──────────────────────────────────────

  [ Transfer initiated...    ]
  [ Receiver verified ✓      ]
  [ Sender debited ✓         ]
  [ Receiver credited ✓      ]
  [ Transaction committed ✓  ]

──────────────────────────────────────
  TRANSFER SUCCESSFUL ✓
  From : ACC100001 (Alice Johnson)
  To   : ACC100002 (Bob Smith)
  Amt  : ₹5,000.00  |  New Balance : ₹45,000.00
──────────────────────────────────────
```

---

## 🧬 OOP Principles

### 1. Encapsulation
All fields in `Account.java` are `private`. Balance and password are accessible only through validated getters and setters — protecting sensitive data and enforcing business rules.

```java
private double balance;   // cannot be accessed directly

public void setBalance(double balance) {
    if (balance < 0)
        throw new IllegalArgumentException("Balance cannot be negative.");
    this.balance = balance;
}
```

### 2. Abstraction
`BankOperations` interface defines WHAT the system does without exposing HOW. `Main.java` calls `deposit()`, `withdraw()`, `transfer()` without knowing a single line of SQL.

```java
public interface BankOperations {
    void   deposit(String accountNumber, double amount) throws Exception;
    void   withdraw(String accountNumber, double amount) throws Exception;
    void   transfer(String from, String to, double amount) throws Exception;
    double checkBalance(String accountNumber) throws Exception;
}
```

### 3. Inheritance
`SavingsAccount` and `CurrentAccount` extend `Account`, reusing all common fields and adding account-type-specific behaviour.

```java
public class SavingsAccount extends Account {
    public static final double MIN_BALANCE   = 1000.00;
    public static final double INTEREST_RATE = 3.5;

    public SavingsAccount(String fullName, String accountNumber,
                          String username, String password, double balance) {
        super(fullName, accountNumber, username, password, balance, "SAVINGS");
    }
}
```

### 4. Polymorphism
`toString()` is overridden in `SavingsAccount` and `CurrentAccount` — Java selects the correct version at **runtime** based on the actual object type. Methods are also overloaded (`deposit(acc, amt)` vs `deposit(acc, amt, desc)`).

```java
@Override
public String toString() {
    return super.toString()   // calls Account.toString()
           + String.format("\n  Interest: %.1f%% p.a.", INTEREST_RATE);
}
```

---

## 🔐 ACID Properties

### A — Atomicity
All 4 SQL steps of a transfer (debit, credit, log debit, log credit) either ALL succeed or ALL roll back. There is no partial transfer.

```java
conn.setAutoCommit(false);    // begin transaction
try {
    debitSender(...);         // SQL 1
    creditReceiver(...);      // SQL 2
    logDebit(...);            // SQL 3
    logCredit(...);           // SQL 4
    conn.commit();            // all 4 succeeded → save
} catch (Exception e) {
    conn.rollback();          // any failure → undo everything
} finally {
    conn.setAutoCommit(true); // always restore
}
```

### C — Consistency
The total money in the system stays constant before and after every transfer. Business rules are validated **before** any SQL executes — preventing invalid database states.

```java
if (senderBalance - amount < MIN_BALANCE)
    throw new InsufficientBalanceException("Min balance ₹1,000 required.");
// SQL only runs if this check passes
```

### I — Isolation
`TRANSACTION_SERIALIZABLE` is set before every transfer — the strictest isolation level. Prevents dirty reads, non-repeatable reads, and phantom reads from concurrent transactions.

```java
conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
```

### D — Durability
Once `conn.commit()` is called, data is permanently written to MySQL's InnoDB Write-Ahead Log (WAL) on disk. Even if the JVM crashes 1 millisecond after commit, the transfer is preserved on database restart.

---

## 🛡️ Security Features

| Feature | Implementation |
|---------|---------------|
| SQL Injection Prevention | `PreparedStatement` with `?` placeholders on every query — zero string concatenation in SQL |
| Login Lockout | 3 failed attempts lock the session |
| Password Verification | Old password confirmed before any change |
| Frozen Account Checks | `is_active = 1` checked on every financial operation |
| Input Validation | All user inputs validated before reaching the database layer |

---

## ⚡ Exception Handling

### Custom Exceptions

```java
// Carries current balance + required amount for precise error messages
throw new InsufficientBalanceException(
    "Insufficient balance for withdrawal.",
    currentBalance,   // what the account HAS
    requiredAmount    // what was requested
);

// Caller uses the context data:
} catch (InsufficientBalanceException e) {
    System.out.printf("You have ₹%.2f. Max withdrawal: ₹%.2f%n",
        e.getCurrentBalance(),
        e.getCurrentBalance() - MIN_BALANCE);
}
```

### Exception Hierarchy Used

```
Exception
├── SQLException              ← all JDBC/MySQL errors
├── InsufficientBalanceException (custom checked)
├── InvalidUserException       (custom checked)
├── AuthenticationException    (custom checked)
├── NumberFormatException      (unchecked — Scanner input)
├── InputMismatchException     (unchecked — menu input)
└── IllegalArgumentException   (unchecked — method arguments)
```

---

## 🧪 Test Cases

| # | Test | Expected Result |
|---|------|-----------------|
| 1 | Register new account | Account created, number assigned |
| 2 | Register duplicate username | Blocked with clear message |
| 3 | Login with correct credentials | Session opened |
| 4 | Login wrong password × 3 | Locked out after 3 attempts |
| 5 | Deposit valid amount | Balance updated + logged |
| 6 | Deposit negative / zero | Rejected before SQL |
| 7 | Withdraw within limit | Balance updated + logged |
| 8 | Withdraw below minimum balance | Blocked, balance unchanged |
| 9 | Transfer — both accounts valid | ACID transfer complete, both sides logged |
| 10 | Transfer — invalid receiver | Rolled back, sender balance restored |
| 11 | Transfer — insufficient sender balance | Blocked before any SQL |
| 12 | Change password — correct old password | Updated, old password rejected |
| 13 | Transaction history | Formatted ledger, newest first |

### Atomicity Verification (Test 10)

```sql
-- Before transfer attempt to invalid account
SELECT balance FROM accounts WHERE account_number = 'ACC100001';
-- ₹40,000.00

-- Attempt transfer → receiver 'ACC999999' does not exist → rollback

-- After rollback
SELECT balance FROM accounts WHERE account_number = 'ACC100001';
-- ₹40,000.00  ← UNCHANGED ✓ atomicity proved

SELECT COUNT(*) FROM transactions
WHERE sender_account = 'ACC100001'
AND transaction_date > NOW() - INTERVAL 1 MINUTE;
-- 0  ← no log entry either ✓
```

---

## 📚 Interview Preparation

<details>
<summary><strong>Q: What is SQL Injection and how does PreparedStatement prevent it?</strong></summary>

SQL Injection inserts malicious SQL through user input — e.g., typing `admin' OR '1'='1` as a username bypasses authentication with a raw `Statement`. `PreparedStatement` precompiles the SQL structure first, then binds user input as **data parameters** via `?` placeholders. The driver escapes all special characters, so injection attempts become harmless literal strings.

</details>

<details>
<summary><strong>Q: What happens if the JVM crashes between debit and credit in a transfer?</strong></summary>

With `setAutoCommit(false)`, neither the debit nor credit is committed. MySQL's InnoDB engine holds both changes in an uncommitted transaction. When the JVM crashes and the connection closes, MySQL automatically rolls back all uncommitted changes. The sender's balance is restored to its original value — no money is lost or created.

</details>

<details>
<summary><strong>Q: Why use DECIMAL instead of double for balance?</strong></summary>

`double` uses IEEE 754 floating-point — it cannot represent all decimal values exactly. `0.1 + 0.2 = 0.30000000000000004` in Java. In banking, even a 1 paisa error per transaction × millions of daily transactions creates massive discrepancies. `DECIMAL(15,2)` in MySQL stores exact decimal values up to ₹9,999,999,999,999.99.

</details>

<details>
<summary><strong>Q: What is the Singleton pattern and why use it for DBConnection?</strong></summary>

Singleton ensures only one instance of a class exists in the JVM. `DBConnection` uses a `private constructor`, `private static Connection` field, and a `public static getConnection()` method as the sole access point. Creating a new database connection involves TCP handshake + authentication (~50–200ms) — sharing one connection across all service calls avoids this overhead on every query.

</details>

<details>
<summary><strong>Q: What is the difference between Abstraction and Encapsulation?</strong></summary>

**Encapsulation** hides internal **data** — `Account.balance` is `private`, accessible only through a validated setter. It's about data protection. **Abstraction** hides internal **complexity** — `BankOperations` interface exposes `deposit()`, `withdraw()`, `transfer()` without revealing any SQL. `Main.java` calls these methods without knowing a single database detail. Encapsulation is about "how data is stored"; abstraction is about "what operations are available."

</details>

<details>
<summary><strong>Q: What is try-with-resources and why use it for PreparedStatement?</strong></summary>

`try-with-resources` automatically calls `.close()` on `AutoCloseable` objects when the block ends — even if an exception occurs. `PreparedStatement` and `ResultSet` implement `AutoCloseable`. Without it, a forgotten `ps.close()` call after an exception leaves the cursor open. In production with thousands of queries per second, this causes "Too many open cursors" errors and eventually crashes the application.

</details>

<details>
<summary><strong>Q: What are the four ACID properties?</strong></summary>

**Atomicity** — all operations in a transaction succeed or all are rolled back (no partial transfer). **Consistency** — business rules are enforced; total money in system stays constant. **Isolation** — concurrent transactions don't interfere; `TRANSACTION_SERIALIZABLE` prevents dirty reads. **Durability** — once `commit()` is called, data is permanently written to MySQL's InnoDB Write-Ahead Log on disk and survives crashes.

</details>

---

## 🚀 Future Enhancements

| Enhancement | Description |
|------------|-------------|
| 🖥️ **JavaFX GUI** | Graphical interface with dashboards and charts |
| 🌐 **Spring Boot REST API** | Convert to full web application with JSON API |
| 🔒 **BCrypt Password Hashing** | Replace plain text with industry-standard hashing |
| 🎫 **JWT Authentication** | Stateless session tokens for API security |
| ⚡ **HikariCP Connection Pool** | Production-grade pooling (50× faster connections) |
| 📱 **OTP via Twilio SMS** | Two-factor authentication for transfers |
| 👨‍💼 **Admin Dashboard** | View and manage all accounts and transactions |
| 📧 **Email Notifications** | Transaction alerts via JavaMail |
| 📄 **PDF Statements** | Export transaction history as PDF |
| 🏗️ **Microservices** | Split into Auth, Account, and Transaction services |

---

## 📊 Project Stats

```
Total Phases      : 20
Java Classes      : 12  (across 6 packages)
Database Tables   : 2   (accounts + transactions)
Feature Modules   : 8
Test Cases        : 13  (success + edge + ACID verification)
OOP Principles    : 4   (Encapsulation, Abstraction, Inheritance, Polymorphism)
ACID Properties   : 4   (Atomicity, Consistency, Isolation, Durability)
SQL Queries       : 12+ (all using PreparedStatement)
Custom Exceptions : 3   (with contextual data fields)
```

---

## 👤 Author

**KIRAN KUMAR**  
Java Developer  
📧 kk2004kiran@gmail.com  
🔗 [LinkedIn](https://www.linkedin.com/in/kiran-kumar-madam-93a837277) · [GitHub](https://github.com/Kiran20040329)

---

## 📝 License

This project is open source under the [MIT License](LICENSE).

```
MIT License — free to use, modify, and distribute with attribution.
```

---

<div align="center">

⭐ **Star this repo if it helped you prepare for interviews!** ⭐

Made with ☕ Java · ⚡ JDBC · 🐬 MySQL

</div>
