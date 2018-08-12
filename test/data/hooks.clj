(ns banks2ledger.core)

;; Define a helper function to define hooks for income transactions,
;; given that we want to book them in a particular non-standard way.
;;
;; The idea is that we want to keep track of tax information, so we
;; will edit the generated transaction by hand, filling in the
;; correct numbers for GrossIncome and IncomeTax from the pay stub.
;; Nevertheless, we want to avoid having to type (or copy-paste)
;; the whole template each month.

(defn income-formatter [income-account entry]
  (let [year (subs (:date entry) 0 4)
        verifs [{:comment "Pay stub data"}
                {:account (format "Tax:%s:GrossIncome" year)
                 :amount "-00,000.00"
                 :currency (:currency entry)}
                {:account (format "Tax:%s:IncomeTax" year)
                 :amount "00,000.00"
                 :currency (:currency entry)}
                {:account (format "Tax:%s:NetIncome" year)}
                {:comment "Distribution of net income"}
                {:account income-account
                 :amount (format "-%s" (:amount entry))
                 :currency (:currency entry)}
                {:account (:account entry)
                 :amount (:amount entry)
                 :currency (:currency entry)}]]
    (print-ledger-entry (conj entry [:verifs verifs]))))


;; Define a hook to customize salary income transactions

(defn salary-hook-predicate [entry]
  (some #{"LÖN"} (tokenize (:descr entry))))

(add-entry-hook {:predicate #(salary-hook-predicate %1)
                 :formatter #(income-formatter "Income:Salary" %1)})


;; Define a hook to customize social security income transactions

(defn fkassa-hook-predicate [entry]
  (some #{"FKASSA"} (tokenize (:descr entry))))

(add-entry-hook {:predicate #(fkassa-hook-predicate %1)
                 :formatter #(income-formatter "Income:Fkassa" %1)})


;; Define a hook to completely discard matching transactions
;; (e.g., because they are moves from/to another account of ours,
;;  and the corresponding transactions are received in that CSV).

(defn ignore-hook-predicate []
  #(let [toks (tokenize (:descr %1))]
     (and (some #{"ANDRA"} toks)
          (some #{"KONTON"} toks))))

(add-entry-hook {:predicate (ignore-hook-predicate)
                 :formatter nil})
