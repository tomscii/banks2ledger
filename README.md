# banks2ledger

A tool to convert bank account CSV files to ledger. Guesses account
name via simple Bayesian inference based on your existing ledger file.
Read about the motivation, the algorithm and the workflow in [this
article].

## Running and installation

After cloning the repository, you might want to run `lein uberjar` to
obtain a self-contained JAR file that can be copied to a system-wide
installation location. The program can also be run directly via `lein
run`.

## Usage

    Usage: banks2ledger [options]
      available options (syntax to set: -x value)
        -l : Ledger file to get accounts and probabilities
             default: ledger.dat
        -f : Input transactions in CSV format
             default: transactions.csv
        -a : Originating account of transactions
             default: Assets:Checking
        -F : CSV field separator
             default: ,
       -sa : CSV header lines to skip
             default: 0
       -sz : CSV trailer lines to skip
             default: 0
        -c : Currency
             default: SEK
        -D : Format of date field in CSV file
             default: yyyy-MM-dd
        -d : Date column index (zero-based)
             default: 0
        -r : Payment reference column index (zero-based)
             default: -1
        -m : Amount column index (zero-based)
             default: 2
        -t : Text (descriptor) column index specs (zero-based)
             default: %3

`banks2ledger` will write ledger transactions to standard output. It
will not modify the contents of any file unless you use an output
redirect (eg. `> out.dat`) in the shell.

The program expects to be set up so the structure of the CSV can be
correctly parsed (no guessing there). It also expects to be able to
read your main ledger file containing all those transactions that will
form the basis of the Bayesian inference for newly created
transactions.

The `-t` option takes something called 'column index specs' that
warrants further explanation. Since the description string forms
the basis of the account inference, and different banks provide
different layouts in their CSV files (even multiple possible layouts
for the same file provider) this is used as a flexible way to create
the descriptor.

The specs string provided for the `-t` option is a string similar to a
printf format string, but allows multiple alternatives to be
specified.  Alternatives will be tried in order from left to right,
and the next alternative is considered only if the current one results
in an empty string. Alternatives are separated by the exclamation
mark, and individual columns in the CSV are referenced by `%n` (n is
the column number; columns are numbered starting with 0).

Examples to provide as the `-t` option:
 - "%4": get fourth column
 - "%4 %5": get fourth and fifth column separated by a space
 - "%4!%1 %2 %3!%7": fourth column by default, but if that is empty
   (contains only whitespace) concatenate the first three columns; if
   that in turn is empty, take the seventh column.

## Development

There are several unit tests you can run via `lein test`. Make sure
they don't break; also, add coverage for any new functionality you
might add or regression tests for bugs you might fix.


[this article]:               https://tomszilagyi.github.io/payment-matching
