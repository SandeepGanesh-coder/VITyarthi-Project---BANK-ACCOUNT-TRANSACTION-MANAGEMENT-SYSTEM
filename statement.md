# Project Statement

## Problem Statement

Manually tracking bank balances and transaction history — through
spreadsheets, notebooks, or ad-hoc scripts — is error-prone and doesn't
scale once multiple accounts and simultaneous operations are involved.
Core banking concerns like enforcing a minimum balance, respecting an
overdraft limit, keeping an accurate transaction log, and making sure two
transfers happening at the same time don't corrupt a balance are easy to
get wrong without a properly structured system behind them. This project
addresses that by building a self-contained banking application that
models accounts, transactions, and concurrent operations correctly, using
core Java concepts rather than relying on a database or external
framework to do the heavy lifting.

## Scope of the Project

The project is a single-file, command-line Java application covering the
essential operations of a banking system:

- Creating `SAVINGS` and `CURRENT` accounts, each with different rules
  (minimum balance vs. overdraft limit)
- Depositing and withdrawing funds, with validation on every operation
- Transferring funds between two accounts safely, even under concurrent
  access
- Viewing and exporting per-account transaction statements
- Persisting account and transaction data across program restarts using
  plain text files

It's a learning-scale project built to demonstrate correct application of
object-oriented design, exception handling, multithreading, and file I/O
— not a production banking system. Deliberately out of scope: a GUI or
web interface, a real database, user authentication/login, multi-currency
support, and interest/tax computations beyond a simple monthly interest
figure for savings accounts.

## Target Users

- **Evaluators/reviewers** assessing the project against the course's
  functional, non-functional, and technical requirements.
- **Developers or students** who want a self-contained, dependency-free
  example of applying OOP, exception handling, concurrency, and file-based
  persistence in Java.
- **End users of the CLI itself** — anyone running the application
  locally to create sample accounts and simulate deposits, withdrawals,
  and transfers for testing or demonstration purposes.

## High-Level Features

- Account creation for two account types, each with type-specific
  withdrawal rules (`SAVINGS` minimum balance, `CURRENT` overdraft limit)
- Deposit and withdrawal operations with input validation and custom
  exceptions for invalid amounts, insufficient funds, and invalid accounts
- Atomic, deadlock-safe fund transfers between accounts using ordered
  per-account locking
- Transaction history lookup per account, with optional CSV export
- A full account listing view
- A built-in concurrent load simulation that fires many random transfers
  at once and verifies total system balance is conserved afterward, as a
  correctness check on the locking strategy
- Plain-text file persistence (`accounts.txt`, `transactions.txt`) that
  survives program restarts with no external database required
