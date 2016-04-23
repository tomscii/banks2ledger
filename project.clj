(defproject ledger-paymatch "0.1.0-SNAPSHOT"
  :description "Bayesian payment matching for ledger"
  :url "https://tomszilagyi.github.io/ledger-paymatch"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :main ^:skip-aot ledger-paymatch.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
