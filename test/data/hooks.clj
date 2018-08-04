(ns banks2ledger.core)

;; Define a helper function to define hooks for income transactions,
;; given that we want to book them in a particular non-standard way.
;;
;; The idea is that we want to keep track of tax information, so we
;; will edit the generated transaction by hand, filling in the
;; correct numbers for GrossIncome and IncomeTax from the pay stub.
;; Nevertheless, we want to avoid having to type (or copy-paste)
;; the whole template each month.

(defn income-formatter [income-account]
  #(let [year (subs (:date %1) 0 4)
         verifs [{:comment "Pay stub data"}
                 {:account (format "Tax:%s:GrossIncome" year)
                  :amount "-00,000.00"
                  :currency (:currency %1)}
                 {:account (format "Tax:%s:IncomeTax" year)
                  :amount "00,000.00"
                  :currency (:currency %1)}
                 {:account (format "Tax:%s:NetIncome" year)}
                 {:comment "Distribution of net income"}
                 {:account income-account
                  :amount (format "-%s" (:amount %1))
                  :currency (:currency %1)}
                 {:account (:account %1)
                  :amount (:amount %1)
                  :currency (:currency %1)}]]
     (print-ledger-entry (conj %1 [:verifs verifs]))))


;; Define a hook to customize salary income transactions

(defn salary-hook-predicate []
  #(some #{"LÖN"} (tokenize (:descr %1))))

(add-entry-hook {:predicate (salary-hook-predicate)
                 :formatter (income-formatter "Income:Salary")})


;; Define a hook to customize social security income transactions

(defn fkassa-hook-predicate []
  #(some #{"FKASSA"} (tokenize (:descr %1))))

(add-entry-hook {:predicate (fkassa-hook-predicate)
                 :formatter (income-formatter "Income:Fkassa")})


;; Define a hook to completely discard matching transactions
;; (e.g., because they are moves from/to another account of ours,
;;  and the corresponding transactions are received in that CSV).

(defn ignore-hook-predicate []
  #(let [toks (tokenize (:descr %1))]
     (and (some #{"ANDRA"} toks)
          (some #{"KONTON"} toks))))

(add-entry-hook {:predicate (ignore-hook-predicate)
                 :formatter nil})
