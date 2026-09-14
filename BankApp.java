import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/*
 * ============================================================================
 *  Bank Account & Transaction Management System
 *  Single-file edition - no external jars, no internet, no build tool.
 *
 *  Compile:  javac BankApp.java
 *  Run:      java BankApp
 *
 *  Pure command-line application - no GUI dependencies anywhere. Everything
 *  (models, exceptions, persistence, concurrency, CLI) lives in this one
 *  file as several top-level package-private classes plus the one public
 *  BankApp class, which is all plain javac needs. Persistence uses simple
 *  text files (accounts.txt, transactions.txt) written with java.io/java.nio
 *  instead of JDBC + a database driver jar, so there is nothing to download
 *  before it will run.
 * ============================================================================
 */
public class BankApp {

    private static AccountService accountService;
    private static TransactionEngine transactionEngine;
    private static final InputHelper input = new InputHelper(new Scanner(System.in));

    public static void main(String[] args) {
        accountService = new AccountService();
        transactionEngine = new TransactionEngine(accountService);

        boolean running = true;
        while (running) {
            printMenu();
            int choice = input.readInt("Enter choice: ");
            try {
                switch (choice) {
                    case 1: createAccount(); break;
                    case 2: deposit(); break;
                    case 3: withdraw(); break;
                    case 4: transfer(); break;
                    case 5: viewStatement(); break;
                    case 6: viewAllAccounts(); break;
                    case 7: simulateConcurrentLoad(); break;
                    case 8: running = false; break;
                    default: System.out.println("Invalid choice, try again.");
                }
            } catch (Exception e) {
                System.out.println("Operation failed: " + e.getMessage());
            }
        }

        transactionEngine.shutdown();
        System.out.println("Goodbye!");
    }

    private static void printMenu() {
        System.out.println("\n===== Bank Account & Transaction Management System =====");
        System.out.println("1. Create Account");
        System.out.println("2. Deposit");
        System.out.println("3. Withdraw");
        System.out.println("4. Transfer");
        System.out.println("5. View Account Statement");
        System.out.println("6. View All Accounts");
        System.out.println("7. Simulate Concurrent Load Test");
        System.out.println("8. Exit");
    }

    private static void createAccount() {
        String accNum = input.readLine("Account Number: ");
        String holder = input.readLine("Holder Name: ");
        BigDecimal opening = input.readAmount("Opening Balance: ");
        String type = input.readLine("Account Type (SAVINGS/CURRENT): ").toUpperCase();

        Account account;
        if ("SAVINGS".equals(type)) {
            account = new SavingsAccount(accNum, holder, opening, BigDecimal.valueOf(500), 0.04);
        } else {
            account = new CurrentAccount(accNum, holder, opening, BigDecimal.valueOf(1000));
        }
        accountService.registerAccount(account);
        System.out.println("Account created: " + account);
    }

    private static void deposit() throws Exception {
        String accNum = input.readLine("Account Number: ");
        BigDecimal amount = input.readAmount("Deposit Amount: ");
        Future<Transaction> future = transactionEngine.deposit(accNum, amount);
        System.out.println("Result: " + future.get());
    }

    private static void withdraw() throws Exception {
        String accNum = input.readLine("Account Number: ");
        BigDecimal amount = input.readAmount("Withdraw Amount: ");
        Future<Transaction> future = transactionEngine.withdraw(accNum, amount);
        System.out.println("Result: " + future.get());
    }

    private static void transfer() throws Exception {
        String from = input.readLine("From Account: ");
        String to = input.readLine("To Account: ");
        BigDecimal amount = input.readAmount("Transfer Amount: ");
        Future<Transaction> future = transactionEngine.transfer(from, to, amount);
        System.out.println("Result: " + future.get());
    }

    private static void viewStatement() throws IOException {
        String accNum = input.readLine("Account Number: ");
        List<Transaction> transactions = transactionEngine.getTransactionsForAccount(accNum);
        if (transactions.isEmpty()) {
            System.out.println("No transactions found.");
            return;
        }
        transactions.forEach(System.out::println);

        String choice = input.readLine("Export statement to file? (y/n): ");
        if (choice.equalsIgnoreCase("y")) {
            String fileName = accNum + "_statement.csv";
            StatementExporter.exportToCsv(transactions, fileName);
            System.out.println("Exported to " + fileName);
        }
    }

    private static void viewAllAccounts() {
        accountService.getAllAccounts().forEach(System.out::println);
    }

    private static void simulateConcurrentLoad() throws Exception {
        List<Account> accounts = new ArrayList<>(accountService.getAllAccounts());
        if (accounts.size() < 2) {
            System.out.println("Need at least 2 accounts to simulate transfers.");
            return;
        }

        int transferCount = input.readInt("Number of concurrent transfers to simulate: ");
        BigDecimal totalBefore = accounts.stream().map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Random random = new Random();
        List<Future<Transaction>> futures = new ArrayList<>();
        for (int i = 0; i < transferCount; i++) {
            Account from = accounts.get(random.nextInt(accounts.size()));
            Account to = accounts.get(random.nextInt(accounts.size()));
            if (from == to) continue;
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(50));
            futures.add(transactionEngine.transfer(from.getAccountNumber(), to.getAccountNumber(), amount));
        }
        for (Future<Transaction> f : futures) f.get();

        BigDecimal totalAfter = accountService.getAllAccounts().stream().map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        System.out.println("Total balance before: " + totalBefore);
        System.out.println("Total balance after : " + totalAfter);
        System.out.println("Balances conserved: " + (totalBefore.compareTo(totalAfter) == 0));
    }
}

// ============================================================================
// Custom exceptions
// ============================================================================

class InsufficientFundsException extends Exception {
    public InsufficientFundsException(String message) { super(message); }
}

class InvalidAccountException extends Exception {
    public InvalidAccountException(String message) { super(message); }
}

class NegativeAmountException extends Exception {
    public NegativeAmountException(String message) { super(message); }
}

// ============================================================================
// Model
// ============================================================================

enum TransactionType { DEPOSIT, WITHDRAW, TRANSFER }

interface Transactable {
    void deposit(BigDecimal amount) throws NegativeAmountException;
    void withdraw(BigDecimal amount) throws NegativeAmountException, InsufficientFundsException;
}

abstract class Account implements Transactable {

    private final String accountNumber;
    private final String holderName;
    protected BigDecimal balance;
    private final ReentrantLock lock;

    protected Account(String accountNumber, String holderName, BigDecimal openingBalance) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.balance = openingBalance;
        this.lock = new ReentrantLock();
    }

    public String getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public ReentrantLock getLock() { return lock; }

    @Override
    public void deposit(BigDecimal amount) throws NegativeAmountException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegativeAmountException("Deposit amount must be positive: " + amount);
        }
        lock.lock();
        try {
            balance = balance.add(amount);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void withdraw(BigDecimal amount) throws NegativeAmountException, InsufficientFundsException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new NegativeAmountException("Withdrawal amount must be positive: " + amount);
        }
        lock.lock();
        try {
            if (!canWithdraw(amount)) {
                throw new InsufficientFundsException(
                        "Insufficient funds in account " + accountNumber + " for withdrawal of " + amount);
            }
            balance = balance.subtract(amount);
        } finally {
            lock.unlock();
        }
    }

    protected abstract boolean canWithdraw(BigDecimal amount);

    public abstract String getAccountType();

    /** Account-type-specific extra field persisted alongside the balance (min balance / overdraft limit). */
    public abstract BigDecimal getExtraField();

    @Override
    public String toString() {
        return String.format("[%s] %s | Holder: %s | Balance: %.2f",
                getAccountType(), accountNumber, holderName, balance);
    }
}

class SavingsAccount extends Account {

    private final BigDecimal minimumBalance;
    private final double interestRate;

    public SavingsAccount(String accountNumber, String holderName, BigDecimal openingBalance,
                           BigDecimal minimumBalance, double interestRate) {
        super(accountNumber, holderName, openingBalance);
        this.minimumBalance = minimumBalance;
        this.interestRate = interestRate;
    }

    @Override
    protected boolean canWithdraw(BigDecimal amount) {
        return balance.subtract(amount).compareTo(minimumBalance) >= 0;
    }

    @Override
    public String getAccountType() { return "SAVINGS"; }

    @Override
    public BigDecimal getExtraField() { return minimumBalance; }

    public BigDecimal getMinimumBalance() { return minimumBalance; }
    public double getInterestRate() { return interestRate; }

    public BigDecimal calculateMonthlyInterest() {
        return balance.multiply(BigDecimal.valueOf(interestRate / 12));
    }
}

class CurrentAccount extends Account {

    private final BigDecimal overdraftLimit;

    public CurrentAccount(String accountNumber, String holderName, BigDecimal openingBalance,
                           BigDecimal overdraftLimit) {
        super(accountNumber, holderName, openingBalance);
        this.overdraftLimit = overdraftLimit;
    }

    @Override
    protected boolean canWithdraw(BigDecimal amount) {
        return balance.subtract(amount).compareTo(overdraftLimit.negate()) >= 0;
    }

    @Override
    public String getAccountType() { return "CURRENT"; }

    @Override
    public BigDecimal getExtraField() { return overdraftLimit; }

    public BigDecimal getOverdraftLimit() { return overdraftLimit; }
}

class Transaction {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String transactionId;
    private final String fromAccount;
    private final String toAccount;
    private final TransactionType type;
    private final BigDecimal amount;
    private final LocalDateTime timestamp;
    private final String status;
    private final String remarks;

    public Transaction(String transactionId, String fromAccount, String toAccount, TransactionType type,
                        BigDecimal amount, LocalDateTime timestamp, String status, String remarks) {
        this.transactionId = transactionId;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.status = status;
        this.remarks = remarks;
    }

    public String getTransactionId() { return transactionId; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
    public String getRemarks() { return remarks; }

    /** Serialize to one pipe-delimited line for the flat-file transaction log. */
    public String toFileRow() {
        return String.join("|",
                transactionId, nullToEmpty(fromAccount), nullToEmpty(toAccount), type.name(),
                amount.toPlainString(), timestamp.format(FORMATTER), status,
                nullToEmpty(remarks).replace("|", ";"));
    }

    public static Transaction fromFileRow(String line) {
        String[] p = line.split("\\|", -1);
        return new Transaction(
                p[0], emptyToNull(p[1]), emptyToNull(p[2]), TransactionType.valueOf(p[3]),
                new BigDecimal(p[4]), LocalDateTime.parse(p[5], FORMATTER), p[6], p[7]);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String emptyToNull(String s) { return s.isEmpty() ? null : s; }

    @Override
    public String toString() {
        return String.format("%s | %-8s | From: %-6s | To: %-6s | Amt: %10.2f | %s | %s",
                timestamp.format(FORMATTER), type, nullToEmpty(fromAccount), nullToEmpty(toAccount),
                amount, status, remarks == null ? "" : remarks);
    }
}

// ============================================================================
// Persistence - plain text files via java.io/java.nio (no JDBC, no driver jar)
// ============================================================================

class AccountStore {

    private static final Path FILE = Path.of("accounts.txt");
    private final Object fileLock = new Object();

    public List<Account> loadAll() throws IOException {
        List<Account> accounts = new ArrayList<>();
        if (!Files.exists(FILE)) return accounts;
        synchronized (fileLock) {
            try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    accounts.add(parseAccount(line));
                }
            }
        }
        return accounts;
    }

    public void saveAll(Collection<Account> accounts) throws IOException {
        synchronized (fileLock) {
            try (BufferedWriter writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                for (Account a : accounts) {
                    writer.write(String.join("|",
                            a.getAccountNumber(), a.getHolderName(), a.getAccountType(),
                            a.getBalance().toPlainString(), a.getExtraField().toPlainString()));
                    writer.newLine();
                }
            }
        }
    }

    private Account parseAccount(String line) {
        String[] p = line.split("\\|", -1);
        String accNum = p[0], holder = p[1], type = p[2];
        BigDecimal balance = new BigDecimal(p[3]);
        BigDecimal extra = new BigDecimal(p[4]);
        if ("SAVINGS".equals(type)) {
            return new SavingsAccount(accNum, holder, balance, extra, 0.04);
        }
        return new CurrentAccount(accNum, holder, balance, extra);
    }
}

class TransactionStore {

    private static final Path FILE = Path.of("transactions.txt");
    private final Object fileLock = new Object();

    public void append(Transaction transaction) throws IOException {
        synchronized (fileLock) {
            try (BufferedWriter writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                writer.write(transaction.toFileRow());
                writer.newLine();
            }
        }
    }

    public List<Transaction> loadAll() throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        if (!Files.exists(FILE)) return transactions;
        synchronized (fileLock) {
            try (BufferedReader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    transactions.add(Transaction.fromFileRow(line));
                }
            }
        }
        return transactions;
    }

    public List<Transaction> loadForAccount(String accountNumber) throws IOException {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : loadAll()) {
            if (accountNumber.equals(t.getFromAccount()) || accountNumber.equals(t.getToAccount())) {
                result.add(t);
            }
        }
        return result;
    }
}

// ============================================================================
// Service layer - in-memory cache + concurrency
// ============================================================================

class AccountService {

    private final Map<String, Account> accountCache = new ConcurrentHashMap<>();
    private final AccountStore accountStore = new AccountStore();

    public AccountService() {
        try {
            for (Account a : accountStore.loadAll()) {
                accountCache.put(a.getAccountNumber(), a);
            }
        } catch (IOException e) {
            System.err.println("Failed to load accounts from disk: " + e.getMessage());
        }
    }

    public void registerAccount(Account account) {
        accountCache.put(account.getAccountNumber(), account);
        persistAll();
    }

    public Account getAccount(String accountNumber) throws InvalidAccountException {
        Account account = accountCache.get(accountNumber);
        if (account == null) {
            throw new InvalidAccountException("No account found with number: " + accountNumber);
        }
        return account;
    }

    public Collection<Account> getAllAccounts() {
        return accountCache.values();
    }

    /** Rewrites the whole accounts file. Simple and safe for a learning-scale project. */
    public void persistAll() {
        try {
            accountStore.saveAll(accountCache.values());
        } catch (IOException e) {
            System.err.println("Failed to persist accounts: " + e.getMessage());
        }
    }
}

/**
 * Executes a single transfer between two accounts on a worker thread.
 * Locks on both accounts are acquired in a globally consistent order
 * (by account number) before any balance is mutated, so two threads
 * transferring in opposite directions between the same pair of accounts
 * can never deadlock or corrupt a balance.
 */
class TransferTask implements Callable<Transaction> {

    private final AccountService accountService;
    private final TransactionStore transactionStore;
    private final String fromAccountNumber;
    private final String toAccountNumber;
    private final BigDecimal amount;

    public TransferTask(AccountService accountService, TransactionStore transactionStore,
                         String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        this.accountService = accountService;
        this.transactionStore = transactionStore;
        this.fromAccountNumber = fromAccountNumber;
        this.toAccountNumber = toAccountNumber;
        this.amount = amount;
    }

    @Override
    public Transaction call() throws IOException {
        String txId = UUID.randomUUID().toString();
        String status;
        String remarks;

        try {
            Account from = accountService.getAccount(fromAccountNumber);
            Account to = accountService.getAccount(toAccountNumber);

            boolean fromFirst = from.getAccountNumber().compareTo(to.getAccountNumber()) < 0;
            ReentrantLock firstLock = fromFirst ? from.getLock() : to.getLock();
            ReentrantLock secondLock = fromFirst ? to.getLock() : from.getLock();

            firstLock.lock();
            try {
                secondLock.lock();
                try {
                    from.withdraw(amount);
                    to.deposit(amount);
                    accountService.persistAll();
                    status = "SUCCESS";
                    remarks = "Transfer completed";
                } finally {
                    secondLock.unlock();
                }
            } finally {
                firstLock.unlock();
            }
        } catch (InvalidAccountException | InsufficientFundsException | NegativeAmountException e) {
            status = "FAILED";
            remarks = e.getMessage();
        }

        Transaction transaction = new Transaction(txId, fromAccountNumber, toAccountNumber,
                TransactionType.TRANSFER, amount, LocalDateTime.now(), status, remarks);
        transactionStore.append(transaction);
        return transaction;
    }
}

class TransactionEngine {

    private final AccountService accountService;
    private final TransactionStore transactionStore = new TransactionStore();
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public TransactionEngine(AccountService accountService) {
        this.accountService = accountService;
    }

    public Future<Transaction> deposit(String accountNumber, BigDecimal amount) {
        return executor.submit(() -> {
            String txId = UUID.randomUUID().toString();
            String status, remarks;
            try {
                Account account = accountService.getAccount(accountNumber);
                account.deposit(amount);
                accountService.persistAll();
                status = "SUCCESS";
                remarks = "Deposit completed";
            } catch (InvalidAccountException | NegativeAmountException e) {
                status = "FAILED";
                remarks = e.getMessage();
            }
            Transaction tx = new Transaction(txId, null, accountNumber, TransactionType.DEPOSIT,
                    amount, LocalDateTime.now(), status, remarks);
            transactionStore.append(tx);
            return tx;
        });
    }

    public Future<Transaction> withdraw(String accountNumber, BigDecimal amount) {
        return executor.submit(() -> {
            String txId = UUID.randomUUID().toString();
            String status, remarks;
            try {
                Account account = accountService.getAccount(accountNumber);
                account.withdraw(amount);
                accountService.persistAll();
                status = "SUCCESS";
                remarks = "Withdrawal completed";
            } catch (InvalidAccountException | InsufficientFundsException | NegativeAmountException e) {
                status = "FAILED";
                remarks = e.getMessage();
            }
            Transaction tx = new Transaction(txId, accountNumber, null, TransactionType.WITHDRAW,
                    amount, LocalDateTime.now(), status, remarks);
            transactionStore.append(tx);
            return tx;
        });
    }

    public Future<Transaction> transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        return executor.submit(new TransferTask(accountService, transactionStore, fromAccountNumber, toAccountNumber, amount));
    }

    public List<Transaction> getTransactionsForAccount(String accountNumber) throws IOException {
        return transactionStore.loadForAccount(accountNumber);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

// ============================================================================
// I/O - statement export
// ============================================================================

class StatementExporter {

    private static final String CSV_HEADER = "TransactionId,FromAccount,ToAccount,Type,Amount,Timestamp,Status,Remarks";

    public static void exportToCsv(List<Transaction> transactions, String filePath) throws IOException {
        Path path = Path.of(filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(CSV_HEADER);
            writer.newLine();
            for (Transaction t : transactions) {
                writer.write(t.toFileRow().replace('|', ','));
                writer.newLine();
            }
        }
    }
}

// ============================================================================
// Utility - validated console input
// ============================================================================

class InputHelper {

    private final Scanner scanner;

    public InputHelper(Scanner scanner) {
        this.scanner = scanner;
    }

    public String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    public int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    public BigDecimal readAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                BigDecimal amount = new BigDecimal(line);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Amount must be greater than zero.");
                    continue;
                }
                return amount;
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid amount.");
            }
        }
    }
}