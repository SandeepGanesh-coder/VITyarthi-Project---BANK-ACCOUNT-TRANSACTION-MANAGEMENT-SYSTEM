# Bank Account & Transaction Management System

This is a small command-line banking application written in Java. It
supports opening accounts, depositing and withdrawing money, transferring
funds between two accounts, and pulling up a statement of everything
that's happened on an account. The whole thing lives in one file, which
keeps setup simple — no fighting with Maven, Gradle, or missing jars.
There's no database either. Balances and transactions are written to
plain text files using `java.io` / `java.nio`, and the concurrent side of
things (so two transactions can safely run at the same time) is handled
through `java.util.concurrent` — a thread pool plus a lock per account.

This README assumes no prior familiarity with the project, so it starts
from zero: installing Java, compiling, and running it, all covered below.

---

## 1. Features

- **Create accounts** — choose `SAVINGS` (has a minimum balance that
  can't be dipped below, and earns a bit of interest) or `CURRENT` (no
  interest, but comes with an overdraft cushion instead).
- **Deposit & withdraw** — every amount gets validated, and withdrawals
  respect whatever rule applies to that account type.
- **Transfer between accounts** — happens atomically, and stays safe even
  when several transfers are firing at once, since locks on both accounts
  are always taken in a fixed order (more on that further down).
- **Transaction statements** — view the full history for an account, and
  export it to CSV for a copy outside the app.
- **View all accounts** — a quick list of everything registered, with
  current balances.
- **Concurrent load test** — throws a batch of random transfers at the
  existing accounts all at once, then checks that the total money in the
  system hasn't changed. It works as a built-in sanity check confirming
  the locking logic actually holds up.
- **Data sticks around** — close the program, reopen it, and accounts and
  history are still there. No database, just text files.

---

## 2. Technologies / Tools Used

- **Language:** Java — built and tested on JDK 17, though it should run
  fine on JDK 11 and up.
- **What it relies on from the standard library:**
  - `java.io` / `java.nio.file` for reading and writing the account and
    transaction files (no JDBC, no database driver)
  - `java.util.concurrent` — an `ExecutorService` thread pool plus
    `ReentrantLock` for each account, so concurrent operations don't step
    on each other
  - `java.math.BigDecimal` for money math, since floating point and
    currency don't mix well
  - `java.time` for transaction timestamps
  - `java.util.Scanner` for the command-line prompts
- **Build tool:** none — just `javac`. No Maven, no Gradle.
- **External dependencies:** none. Pure JDK, nothing to download.
- **Version control:** Git / GitHub.

---

## 3. Project Contents

| File | Purpose |
|---|---|
| `BankApp.java` | Everything — the CLI, the account models, the transaction engine, and the file persistence — in a single file. |
| `accounts.txt` | Created automatically the first time an account is made. Holds every account's data. |
| `transactions.txt` | Also auto-created. Appends every deposit, withdrawal, and transfer as it happens. |
| `<accountNumber>_statement.csv` | Only appears if a statement is exported (e.g. `1000_statement.csv`). |

> `accounts.txt`, `transactions.txt`, and any exported statement files
> appear on their own in whatever folder the program is run from — there's
> no need to create them ahead of time.

---

## 4. Prerequisites

All that's needed is a Java Development Kit. Nothing else.

- **JDK version:** Java 17 or newer is recommended, though the APIs used
  here (`java.time`, `BigDecimal`, `java.util.concurrent`) have been
  around since JDK 11, so anything from there up should work. When in
  doubt, use the latest LTS release.
- No Maven or Gradle needed.
- No internet connection needed to build or run it.
- No external `.jar` files to track down.

### Checking if Java is already installed

Open a terminal — Command Prompt or PowerShell on Windows, Terminal on
macOS/Linux — and run:

```bash
java -version
javac -version
```

If both print out a version number (something like `openjdk version
"17.0.x"`), everything's ready. If either one comes back as "command not
found," a JDK needs to be installed first:

- Available from [Eclipse Temurin (Adoptium)](https://adoptium.net/) or
  [Oracle JDK](https://www.oracle.com/java/technologies/downloads/).
  During installation, make sure `java` and `javac` end up on the system
  `PATH` — most installers handle this automatically.

---

## 5. Setup

1. Place `BankApp.java` into a folder of choice. This is also where the
   data files will end up — `accounts.txt`, `transactions.txt`, and any
   exported statements — so pick somewhere sensible.
2. Open a terminal and `cd` into that folder:

   ```bash
   cd path/to/your/folder
   ```

That covers the entire setup. There are no dependencies to install and no
configuration file to touch beforehand.

---

## 6. Compiling the Project

From inside the folder containing `BankApp.java`, run:

```bash
javac BankApp.java
```

This builds `BankApp.java` along with every class defined inside it —
`Account`, `SavingsAccount`, `CurrentAccount`, `Transaction`,
`AccountService`, `TransactionEngine`, and the rest — into `.class` files
sitting next to it. A clean compile produces no output at all; the new
`.class` files simply appear in the folder.

---

## 7. Running the Application

Once compiled, run:

```bash
java BankApp
```

This brings up a menu like the one below:

```
===== Bank Account & Transaction Management System =====
1. Create Account
2. Deposit
3. Withdraw
4. Transfer
5. View Account Statement
6. View All Accounts
7. Simulate Concurrent Load Test
8. Exit
Enter choice:
```

Type a number, press Enter, and follow whatever prompt comes next.

### What each option does

1. **Create Account** — asks for an account number, the holder's name, an
   opening balance, and whether it's `SAVINGS` or `CURRENT`. Savings
   accounts carry a minimum balance requirement but earn interest;
   current accounts skip interest in exchange for overdraft room.
2. **Deposit** — adds money to an account that already exists.
3. **Withdraw** — takes money out, limited by that account type's rules.
4. **Transfer** — moves funds from one account to another in a single,
   safe step; locking is handled so this can't deadlock.
5. **View Account Statement** — shows everything that's happened on an
   account, with the option to save it as a CSV named
   `<accountNumber>_statement.csv`.
6. **View All Accounts** — lists every account on record.
7. **Simulate Concurrent Load Test** — runs a batch of random transfers
   between existing accounts at the same time and confirms total money in
   the system stayed the same afterward.
8. **Exit** — shuts everything down cleanly and closes the program.

### A quick example

```
Enter choice: 1
Account Number: 1000
Holder Name: Jane Doe
Opening Balance: 500
Account Type (SAVINGS/CURRENT): SAVINGS
Account created: [SAVINGS] 1000 | Holder: Jane Doe | Balance: 500.00
```

Right after this, `accounts.txt` appears in the folder with that
account's data recorded in it.

---

## 8. Data Persistence

- Accounts live in `accounts.txt`, which gets rewritten in full whenever
  something changes.
- Every deposit, withdrawal, or transfer gets appended as a new line in
  `transactions.txt`, so over time this file becomes a running audit log.
- Both files are plain text, pipe-delimited (`|`). Closing and reopening
  the app reloads everything from `accounts.txt` automatically.
- Exporting a statement (option 5) produces a proper CSV with this
  header:

  ```
  TransactionId,FromAccount,ToAccount,Type,Amount,Timestamp,Status,Remarks
  ```

---

## 9. Testing

There's no separate JUnit suite included — the application is meant to be
tested end-to-end through the CLI itself. A reasonable set of checks to
run through:

1. **Create a couple of accounts** — one `SAVINGS`, one `CURRENT` (option
   `1`), then confirm both show up correctly under **View All Accounts**
   (option `6`).
2. **Deposit into one** (option `2`) and check the balance increased via
   option `6`.
3. **Withdraw a reasonable amount** (option `3`) — something within the
   account's limits — and confirm it goes through.
4. **Try withdrawing too much** — more than the savings minimum or
   current overdraft allows — and confirm it fails gracefully with an
   `Insufficient funds` message instead of crashing.
5. **Transfer between the two accounts** (option `4`) and check both
   balances updated correctly on each side.
6. **Export a statement** (option `5`) and open the resulting
   `<accountNumber>_statement.csv` to confirm the header and rows look
   right.
7. **Run the concurrency test** (option `7`) with a decent number of
   transfers, say `100`, and confirm it reports `Balances conserved:
   true`. This is essentially the built-in proof that the locking holds
   up under pressure.
8. **Restart and check persistence** — exit (option `8`), run `java
   BankApp` again, and confirm the accounts and balances are still there
   via option `6`.

---

## 10. Notes for Evaluators / Reviewers

- Everything — the models, the custom exceptions, the persistence layer,
  the multithreaded transaction engine, and the CLI — sits in one file,
  `BankApp.java`, structured as a single `public class BankApp` plus a
  handful of package-private helper classes alongside it. That's a
  deliberate design choice, noted in the file's own header comment, so
  the whole thing runs off a bare JDK with nothing else installed.
- On concurrency: whenever a transfer happens between two accounts, both
  locks are acquired in a consistent order (sorted by account number) so
  two transfers running in opposite directions can never deadlock each
  other. Each account also has its own lock protecting deposits and
  withdrawals individually.
- To start fresh, delete `accounts.txt` and `transactions.txt` from the
  folder before running the app again — it will behave like a brand-new
  install.
