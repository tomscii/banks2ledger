(defproject banks2ledger "1.0.0"
  :description "Banks' CSV to ledger converter with probabilistic payment matching"
  :url "https://tomszilagyi.github.io/payment-matching"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :main ^:skip-aot banks2ledger.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
