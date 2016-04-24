(ns ledger-paymatch.core-test
  (:require [clojure.test :refer :all]
            [ledger-paymatch.core :refer :all]))

(deftest test-all-indices
  (testing "all-indices"
    (is (= (all-indices "abcdef" ",")
           []))
    (is (= (all-indices ",abc,def," ",")
           [0 4 8]))
    (is (= (all-indices "abc,de,\"f,g,x\",hi,\"al,ma\"" ",")
           [3 6 9 11 14 17 21]))))

(deftest test-split-csv-line
  (testing "split-csv-line"
    (is (= (split-csv-line "abc,de,\"f,g,x\",hi,\"al,ma\"" ",")
           ["abc" "de" "\"f,g,x\"" "hi" "\"al,ma"]))))
