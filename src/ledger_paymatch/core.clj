(ns ledger-paymatch.core
  (:gen-class))

;; Bump account's token counter for token
(defn toktab-inc [toktab [account token]]
  (let [acctab0 (or (get toktab account) {})
        cnt (or (get acctab0 token) 0)
        acctab (conj acctab0 [token (inc cnt)])]
    (conj toktab [account acctab])))

;; Update toktab by bumping all accounts in entry for all tokens
(defn toktab-update [toktab {accs :accs toks :toks}]
  (reduce toktab-inc toktab (for [acc accs
                                  tok toks]
                              [acc tok])))

;; Clip string to the part before the given endmark
;; endmark is first arg to allow meaningful use of `partial'
(defn clip-string [endmark string]
  (let [end-idx (.indexOf string endmark)]
    (if (= end-idx -1)
      string
      (.substring string 0 end-idx))))

;; Create tokens from string
;; One string may become one or more tokens, returned as a seq
;; - Convert to uppercase
;; - replace dates with degraded forms
;; - Split at '/' ',' and space
(defn tokenize [str]
  (->> (-> str
           .toUpperCase
           (clojure.string/replace #"20\d{6}" "YYYYMMDD")
           (clojure.string/replace #"/\d{2}-\d{2}-\d{2}" "/YY-MM-DD")
           (clojure.string/split #",|/| "))
       (filter #(> (count %) 0))))

;; Parse a ledger entry from string to map
(defn parse-entry [entry]
  (let [[first-line0 & rest-lines] (clojure.string/split entry #"\n")
        first-line (clip-string "|" first-line0)
        [date descr] (clojure.string/split first-line #" " 2)
        toks (tokenize descr)
        accs (->> rest-lines
                  (map clojure.string/trim)
                  (map (partial clip-string "  ")))]
    {:date date :toks toks :accs accs}))

;; Read and parse a ledger file; return map of accounts with
;; values that are maps of token counts
(defn parse-ledger [filename]
  (->> (clojure.string/split (slurp filename) #"\n\n")
       (filter #(> (count %) 0))
       (map parse-entry)
       (reduce toktab-update {})))

;; P_occur is the occurrence probability of token among
;; all tokens recorded for account.
;; acc-maps is the output of parse-ledger.
(defn p_occur [acc-maps token account]
  (let [acc-table (get acc-maps account)
        n_t_a (or (get acc-table token) 0)
        n_all (apply + (vals acc-table))]
    (if (= 0 n_all)
      0.0
      (/ (float n_t_a) n_all))))

;; P_belong is the probability that a transaction with
;; token in its descriptor belongs to account.
;; acc-maps is the output of parse-ledger.
(defn p_belong [acc-maps token account]
  (let [p_occ (p_occur acc-maps token account)
        p_occ_all (apply + (map (fn [acc] (p_occur acc-maps token acc))
                                (keys acc-maps)))]
    (if (= 0.0 p_occ_all)
      0.0
      (/ p_occ p_occ_all))))

;; Combine probability values according to the Bayes theorem
(defn bayes* [probs]
  (let [prod-probs (apply * probs)
        prod-comps (apply * (map #(- 1.0 %) probs))]
    (/ prod-probs (+ prod-probs prod-comps))))

;; Combined p_belong of given tokens for account
(defn p_belong* [acc-maps tokens account]
  (bayes* (map (fn [tok] (p_belong acc-maps tok account))
               tokens)))

;; Return a list of [p_belong, account] pairs in descending order
;; only accounts with nonzero probs are returned
(defn best-accounts [acc-maps token]
  (sort-by first >
           (filter #(> (first %) 0.0)
                   (map (fn [acc] [(p_belong acc-maps token acc) acc])
                        (keys acc-maps)))))

;; Print a table of combined probs for given tokens
(defn p_table [acc-maps tokens]
  (let [nz-toks (filter #(> (count (best-accounts acc-maps %)) 0) tokens)]
    (sort-by first >
             (filter #(> (first %) 0.0)
                     (map (fn [acc] [(p_belong* acc-maps nz-toks acc) acc])
                          (keys acc-maps))))))

;; Return the most probable counter-accounts for given descr captured for
;; account. This account will be excluded from possible counter-accounts.
(defn account-for-descr [acc-maps descr account]
  (let [tokens (tokenize descr)
        p_tab (p_table acc-maps tokens)]
    (filter #(= false (.contains ^String (second %) account)) p_tab)))


;; CL args spec and defaults
(def cl-args-spec
  {:ledger-file
   {:opt "-l" :value "ledger.dat"
    :help "Ledger file to get accounts and probabilities"}
   :csv-file
   {:opt "-f" :value "transactions.csv"
    :help "Input transactions in CSV format"}
   :account
   {:opt "-a" :value "Assets:Checking"
    :help "Originating account of transactions"}
   :csv-field-separator
   {:opt "-F" :value "," :help "CSV field separator"}
   :csv-skip-lines
   {:opt "-s" :value 0 :help "CSV header lines to skip"}
   :currency
   {:opt "-c" :value "SEK" :help "Currency"}
   :date-col
   {:opt "-d" :value 0 :help "Date column index (zero-based)"}
   :date-format
   {:opt "-D" :value "yyyy-MM-dd" :help "Format of date field in CSV file"}
   :amount-col
   {:opt "-m" :value 1 :help "Amount column index (zero-based)"}
   :descr-col
   {:opt "-t" :value 2 :help "Text (descriptor) column index (zero-based)"}})

(defn print-usage-and-die [message]
  (println message)
  (println)
  (println "Usage: ledger-paymatch [options]")
  (println "  available options (syntax to set: -x value)")
  (doseq [{:keys [opt value help]} (vals cl-args-spec)]
    (println " " opt ":" help)
    (println "       default:" value))
  (System/exit 0))

(defn optvec [args-spec]
  (into [] (map :opt (vals args-spec))))

(defn set-arg [args-spec arg value]
  (let [key (first (filter #(= arg (:opt (% args-spec)))
                           (keys args-spec)))
        val0 (key args-spec)
        val (conj val0 [:value value])]
    (conj args-spec [key val])))

(declare parse-args)
(defn parse-arg [args-spec arg rest-args]
  (if (some #{arg} (optvec cl-args-spec))
    (let [value (first rest-args)
          rest (rest rest-args)]
      (if (nil? value)
        (print-usage-and-die (str "Value expected for option " arg))
        (parse-args (set-arg args-spec arg value) rest)))
    (print-usage-and-die (str "Invalid argument: " arg))))

;; Go through the args list and return an updated args-spec
(defn parse-args [args-spec args]
  (if (empty? args)
    args-spec
    (parse-arg args-spec (first args) (rest args))))

;; Convert date field from CSV format to Ledger entry format
(defn convert-date [args-spec datestr]
  (.format
   (java.text.SimpleDateFormat. "yyyy/MM/dd")
   (.parse (java.text.SimpleDateFormat. (:value (:date-format args-spec)))
           datestr)))

;; Convert CSV of bank account transactions to corresponding ledger entries
(defn -main [& args]
  (let [params (parse-args cl-args-spec args)
        acc-maps (parse-ledger (:value (:ledger-file params)))]

    ;; TODO read the CSV with the set params and generate Ledger entries

    ;; this will be the meat of the converter
    (println
    (account-for-descr acc-maps "Forgalmi jutalék 01N00/702595/ számláról"
                       (:value (:account params))))))

;; run this with -l /home/tom/doc/ledger/ledger.dat -a "Assets:BB Folyószámla"
