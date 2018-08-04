#! /bin/bash

lein test; export RC=$?
if [ $RC -ne 0 ]; then
    exit $RC
fi

echo
echo Now running end-to-end tests:

LEDGER="test/data/ledger.dat"

rm -f test/data/*.out

echo -n "testing bb.csv... "
lein run -l $LEDGER -f test/data/bb.csv -sa 3 -sz 2 -D 'yyyy/MM/dd' \
     -r 3 -m 4 -t '%9!%1 %6 %7 %8' -a 'Assets:BB Account' -c HUF > test/data/bb.out
diff -u test/data/bb.out test/data/bb.ref-out >/dev/null; export RC=$?
if [ $RC -ne 0 ]; then
    echo "FAIL, inspect bb.out!"
    exit $RC
else
    rm test/data/bb.out
    echo "OK"
fi

echo -n "testing ica.csv... "
lein run -l $LEDGER -f test/data/ica.csv -F ';' -sa 1 -m 4 -t '%1' \
	-a 'Assets:ICA Account' -ds "," -gs " " > test/data/ica.out
diff -u test/data/ica.out test/data/ica.ref-out >/dev/null; export RC=$?
if [ $RC -ne 0 ]; then
    echo "FAIL, inspect ica.out!"
    exit $RC
else
    rm test/data/ica.out
    echo "OK"
fi

echo -n "testing seb.csv... "
lein run -l $LEDGER -f test/data/seb.csv -sa 5 -r 2 -m 4 -t '%3' \
     -hf test/data/hooks.clj -a 'Assets:SEB Account' > test/data/seb.out
diff -u test/data/seb.out test/data/seb.ref-out >/dev/null; export RC=$?
if [ $RC -ne 0 ]; then
    echo "FAIL, inspect seb.out!"
    exit $RC
else
    rm test/data/seb.out
    echo "OK"
fi
