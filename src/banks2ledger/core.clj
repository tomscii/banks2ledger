(ns banks2ledger.core
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
  (->>
   (-> str
       .toUpperCase
       (clojure.string/replace #"20\d{6}" "YYYYMMDD")
       (clojure.string/replace #"/\d{2}-\d{2}-\d{2}" "/YY-MM-DD")
       (clojure.string/split #",|/| "))
   (filter #(> (count %) 0))))

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

(defn decide-account [acc-maps descr account]
  (let [accs (account-for-descr acc-maps descr account)]
    (cond (empty? accs) "Unknown"
          (= (first (first accs)) (first (second accs))) "Unknown"
          :else (second (first accs)))))

;; Science up to this point. From here, only machinery.

;; Parse a ledger entry from string to acc-map
(defn parse-ledger-entry [entry]
  (let [[first-line0 & rest-lines] (clojure.string/split entry #"\n")
        first-line (clip-string "|" first-line0)
        [date descr] (clojure.string/split first-line #" " 2)
        toks (tokenize descr)
        accs (->> rest-lines
                  (map clojure.string/trim)
                  (map (partial clip-string "  ")))]
    {:date date :toks toks :accs accs}))

;; Read and parse a ledger file; return acc-maps
(defn parse-ledger [filename]
  (->> (clojure.string/split (slurp filename) #"\n\n")
       (map clojure.string/trim) ;; remove odd newlines
       (filter #(> (count %) 0))
       (map parse-ledger-entry)
       (reduce toktab-update {})))

;; command line args spec
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

   :csv-skip-header-lines
   {:opt "-sa" :value 0 :conv-fun #(Integer. %)
    :help "CSV header lines to skip"}

   :csv-skip-trailer-lines
   {:opt "-sz" :value 0 :conv-fun #(Integer. %)
    :help "CSV trailer lines to skip"}

   :currency
   {:opt "-c" :value "SEK" :help "Currency"}

   :date-format
   {:opt "-D" :value "yyyy-MM-dd" :help "Format of date field in CSV file"}

   :date-col
   {:opt "-d" :value 0 :conv-fun #(Integer. %)
    :help "Date column index (zero-based)"}

   :ref-col
   {:opt "-r" :value -1 :conv-fun #(Integer. %)
    :help "Payment reference column index (zero-based)"}

   :amount-col
   {:opt "-m" :value 2 :conv-fun #(Integer. %)
    :help "Amount column index (zero-based)"}

   :descr-col
   {:opt "-t" :value "%3"
    :help "Text (descriptor) column index specs (zero-based)"}})

(defn print-usage-and-die [message]
  (println message)
  (println)
  (println "Usage: banks2ledger [options]")
  (println "  available options (syntax to set: -x value)")
  (doseq [{:keys [opt value help]} (vals cl-args-spec)]
    (println " " opt ":" help)
    (println "       default:" value))
  (System/exit 0))

(defn optvec [args-spec]
  (into [] (map :opt (vals args-spec))))

;; Get the value of an argument given by key.
;; If present, apply converter function.
(defn get-arg [args-spec key]
  (let [arg-spec (key args-spec)
        conv-fun (:conv-fun arg-spec)
        raw-value (:value arg-spec)]
    (if (nil? conv-fun)
      raw-value
      (conv-fun raw-value))))

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
   (.parse (java.text.SimpleDateFormat. (get-arg args-spec :date-format))
           datestr)))

;; Convert amount string - note the return value is still a string!
;; - strip anything that does not belong to the number
;; - change decimal comma to dot
(defn convert-amount [string]
  (->>
   (-> (re-find #"-?\d[\d ]+[,\.]?\d*" string)
       (clojure.string/replace #"," ".")
       (.replace " " "")
       (Double.))
   (format "%,.2f")))

;; Remove quotes from start & end of the string, if both present
(defn unquote-string [str]
  (let [len (count str)
        last (dec len)]
    (cond (< len 3) str
          (or (and (.startsWith str "'") (.endsWith str "'"))
              (and (.startsWith str "\"") (.endsWith str "\"")))
          (subs str 1 last)
          :else str)))

(defn all-indices-1 [str sub pos acc]
  (let [idx (.indexOf str sub pos)]
    (if (= idx -1)
      acc
      (all-indices-1 str sub (inc idx) (conj acc idx)))))

;; Return an array of all indices where sub starts within str
(defn all-indices [str sub]
  (all-indices-1 str sub 0 []))

;; Split string at the list of supplied indices and return a
;; list of substrings. Note that the sublists do not contain the
;; characters at the indices, only those in between.
(defn split-by-indices [str ixs]
  (let [op-ixs (concat (list -1) ixs (list (count str)))]
    (map (fn [[s e]]
           (subs str (inc s) e))
         (partition 2 1 op-ixs))))

;; Return a vector of columns split from csv line.
;; NB: delimiters in quoted cells will not split the string
(defn split-csv-line [str delim]
  (let [delim-ixs (all-indices str delim)
        quote-ixs (all-indices str "\"")
        split-ixs (reduce
                   (fn [acc [start end]]
                     (filter #(not (and (< start %) (< % end))) acc))
                   delim-ixs
                   (partition 2 quote-ixs))]
    (into [] (split-by-indices str split-ixs))))

;; Render a colspec to an actual string based on cols;
;; return whitespace-trimmed version.
(defn format-colspec [cols colspec]
  (-> colspec
      (clojure.string/replace #"\%(\d)*"
             #(unquote-string (nth cols (Integer. (second %1)))))
      (clojure.string/trim)))

(defn get-col-1 [cols [spec & spec-list]]
  (let [fmt (format-colspec cols spec)]
    (if (or (> (count fmt) 0)
            (empty? spec-list))
      fmt
      (get-col-1 cols spec-list))))

;; Get column data from cols according to colspec, which is a string
;; similar to a printf format string but allowing alternatives to be
;; used if an earlier spec results in an empty string.
;; "%4" - get fourth column
;; "%4 %5" - get fourth and fifth column separated by a space
;; "%4!%1 %2 %3!%7" - fourth column by default, but if that is empty,
;;   (contains only whitespace) concatenate the first three columns;
;;   if that is empty, take the seventh column.
(defn get-col [cols colspec]
  (let [delim-ixs (all-indices colspec "!")
        spec-list (split-by-indices colspec delim-ixs)]
    (get-col-1 cols spec-list)))

;; Parse a line of CSV into a map with :date :ref :amount :descr
(defn parse-csv-entry [params string]
  (let [cols (split-csv-line string (get-arg params :csv-field-separator))
        ref-col (get-arg params :ref-col)]
    {:date (convert-date params (nth cols (get-arg params :date-col)))
     :ref (if (< ref-col 0) nil (unquote-string (nth cols ref-col)))
     :amount (convert-amount (nth cols (get-arg params :amount-col)))
     :descr (unquote-string (get-col cols (get-arg params :descr-col)))}))

;; Drop the configured number of header and trailer lines
(defn drop-lines [lines params]
  (subvec lines
          (get-arg params :csv-skip-header-lines)
          (- (count lines) (get-arg params :csv-skip-trailer-lines))))

;; Parse input CSV into a list of maps
(defn parse-csv [params]
  (->> (-> (slurp (get-arg params :csv-file))
           (clojure.string/split #"\n")
           (drop-lines params))
       (map clojure.string/trim-newline)
       (map (partial parse-csv-entry params))))

;; format and print a ledger entry to *out*
(defn print-ledger-entry [params acc-maps
                          {:keys [date ref amount descr] :as cm}]
  (let [account (get-arg params :account)
        counter-acc (decide-account acc-maps descr account)
        currency (get-arg params :currency)]
  (printf "%s " date)
  (if (and ref (not (empty? ref))) (printf "(%s) " ref))
  (println descr)
  (if (= \- (first amount))
    (do (printf "    %-38s%s %s\n" counter-acc currency (subs amount 1))
        (printf "    %s\n" account))
    (do (printf "    %-38s%s %s\n" account currency amount)
        (printf "    %s\n" counter-acc)))
  (println)))

;; Convert CSV of bank account transactions to corresponding ledger entries
(defn -main [& args]
  (let [params (parse-args cl-args-spec args)
        acc-maps (parse-ledger (get-arg params :ledger-file))
        csv-maps (parse-csv params)]
    (doseq [cm csv-maps]
      (print-ledger-entry params acc-maps cm))
    (flush)))
