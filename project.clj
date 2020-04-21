(defproject wf-soaker "1.4.0"
  :description "Send a steady point rate to Wavefront"
  :url "https://github.com/snltd/wf-soak"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.1.587"]
                 [environ "1.1.0"]
                 [clj-http "3.10.0"]]
  :main ^:skip-aot wf-soaker.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {
               :plugins [[com.jakemccrary/lein-test-refresh "0.24.1"]]
               :dependencies  [[com.jakemccrary/lein-test-refresh "0.24.1"]] }})
