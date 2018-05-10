# banks2ledger

[![Build Status](https://travis-ci.org/tomszilagyi/banks2ledger.svg?branch=master)](https://travis-ci.org/tomszilagyi/banks2ledger)

A tool to convert bank account CSV files to ledger. Guesses account
name via simple Bayesian inference based on your existing ledger file.
Read about the motivation, the algorithm and the workflow in [this
article].

## Running and installation

banks2ledger is written in [Clojure], and [Leiningen] is all you
should need to get up and running.

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
        -e : Encoding of the CSV file
             default: UTF-8
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
       -ds : Decimal sign character
             default: .
       -gs : Decimal group (thousands) separator character
             default: ,


`banks2ledger` will write ledger transactions to standard output. It
will not modify the contents of any file unless you use an output
redirect (eg. `> out.dat`) in the shell.

The program expects to be set up so the structure of the CSV can be
correctly parsed (no guessing there). It also expects to be able to
read your main ledger file containing all those transactions that will
form the basis of the Bayesian inference for newly created
transactions.

### CSV column mapping

The default value for `-r` (-1) means that in case you don't have a
reference column in the CSV, you can simply omit this option and no
reference column will be used. Otherwise, set it to the column number
for the payment reference, and the data from there will be printed as
part of the generated ledger entry.

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
 - `"%4"`: get fourth column
 - `"%4 %5"`: get fourth and fifth column separated by a space
 - `"%4!%1 %2 %3!%7"`: fourth column by default, but if that is empty
   (contains only whitespace) concatenate the first three columns; if
   that in turn is empty, take the seventh column.

### Amount format

The `-ds` and `-gs` options allow parsing almost arbitrarily formatted
decimal numbers from the amount column. Their usage is entirely
optional. With both options omitted, the accepted numbers are of the
usual "Western" format, i.e., a string of digits starting with an
optional minus for negative numbers, grouped with optional commas for
thousands (or other) separation, followed by an optional fractional
part after a decimal dot.

Evident garbage (text that cannot be possibly part of a number) both
before and after the number is implicitly discarded, so having a
currency as part of the amount field should not be a problem.

Examples of setting `-ds` and `-gs` for parsing different amount
formats:

|   Example amount | Parse options     |
|-----------------:|-------------------|
| `"1,234,567.89"` | *(defaults)*      |
| `"1.234.567,89"` | `-ds ',' -gs '.'` |
| `"1 234 567,89"` | `-ds ',' -gs ' '` |
|  `"123_4567.89"` |         `-gs '_'` |

## Status

`banks2ledger` development is governed, first and foremost, by the
author's own needs. Naturally, pull requests to add features or fix
problems by other developers are gladly considered.

Because of the above, the program is not aiming to be complete. In
particular, it does not implement every documented feature of
`ledger-cli` file syntax. If you run into problems (i.e. because
`banks2ledger` does not parse your ledger file that is flawlessly
parsed by ledger itself) please open an issue with a specific minimal
example demonstrating the problem.

## Development

Feel free to open a pull request if you find a bug, or have a feature
you would like to see included.

There are several unit tests you can run via `lein test`. Make sure
they don't break; also, add coverage for any new functionality you
might add or regression tests for bugs you might fix.

The script `test.sh` runs the unit tests, and if they are successful,
proceeds with doing some end-to-end testing with "real" files. The
input files are under `test/data/` along with the reference output,
which is used to validate the results. The test script also shows the
usual invocation (parameterization) of banks2ledger for differently
structured CSV files. For real production usage, it is recommended to
roll a `Makefile` or similar solution to process your input files; see
[this article] for an example.


[this article]:               https://tomszilagyi.github.io/payment-matching
[Clojure]:                    http://clojure.org
[Leiningen]:                  http://leiningen.org
