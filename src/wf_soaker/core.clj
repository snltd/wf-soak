(ns wf-soaker.core
  (:require [clj-http.client :as client]
            [environ.core :refer [env]]
            [clojure.set :as set]
            [clojure.string :as str])
  (:gen-class))

;; There's some difficulty in this problem in that Wavefront has a resolution
;; of one second. So, sending a thousand points per second from a single host
;; will result in one point in Wavefront.
;;
;; Therefore we have to make ourselves appear like many different hosts
;; sending many different metrics. But we can't make the cardinality of any
;; metrics too high.
;;
;; Needs to know various things, which are passed by env vars, as it's made to
;; be run inside a container in ECS.  Use the following: (all numbers must be
;; integers.)
;;
;; WF_PROXY            where the proxy is. Requires the protocol and port
;;                     number: http://wavefront.localnet:2878. No default.
;; WF_PATH             the base metric path [default dev.soak]
;; WF_INTERVAL         send a bundle of metrics every this-many seconds [1]
;; WF_PARALLEL_METRICS each interval send a bundle of this-many metrics [10]
;; WF_PARALLEL_TAGS    each of those metrics repeats this many times with
;;                     this many different values for the 'dtag' point tag
;; WF_THREADS          duplicate the metric bundle this many times [10]
;; WF_ITERATIONS       send this many bundles of metrics
;; WF_DEBUG            print debug info to standard out
;;
;; So the number of points-per-second we send is
;; (WF_PARALLEL_METRICS × WF_PARALLEL_TAGS × WF_THREADS) ÷ WF_INTERVAL

(def points-sent (atom 0))

(def defaults
  { :wf-proxy nil
    :wf-path "dev.soak"
    :wf-interval 1
    :wf-parallel-metrics 10
    :wf-parallel-tags 10
    :wf-threads 5
    :wf-iterations 120 })

(defn now [] (quot (System/currentTimeMillis) 1000))

(defn debug-on []
  (env :wf-debug))

(defn debug [& chunks]
  (if (debug-on)
    (println (apply str chunks))))

(defn send-data! [data endpoint]
  "POST a chunk of data to a Wavefront endpoint"
  (client/post endpoint {:body data}))

(defn point-value []
  (rand-int 100))

(defn metric-name [path-base path-num]
  (str path-base ".path-" path-num))

(defn point-tag [tag-num]
  (str "dtag=" tag-num))

(defn source-tag [source-num]
  (str "source=soaker-" source-num))

(defn point-bundle [wf-path thread-num metric-range tag-range]
  "makes a vector of metric-range × tag-range points"
  (for [metric-num metric-range tag-num tag-range]
    (str/join
      " "
      [(metric-name wf-path metric-num) (point-value) (now)
       (source-tag thread-num) (point-tag tag-num)])))

(defn thread-writer [thread-num {:keys [wf-parallel-metrics wf-parallel-tags
                                         wf-interval wf-proxy wf-path]}]
  "after some random sub-second interval, sends a point bundle to Wavefront"
  (let [sleep-time (rand-int (* 1000 wf-interval))
        bundle (point-bundle wf-path thread-num (range wf-parallel-metrics)
                             (range wf-parallel-tags))]
    (debug (format "  thread %02d: sleeping %dms" thread-num sleep-time))
    (Thread/sleep sleep-time)
    (debug (format "  thread %02d: sending %d points" thread-num (count bundle)))
    (send-data! (str/join "\n" bundle) wf-proxy)
    (swap! points-sent (partial + (count bundle)))))

(defn run-thread [{:keys [wf-threads wf-parallel-metrics wf-parallel-tags]
                   :as vars}]
  "Run as many parallel write operations as are requested"
  (doseq [thread-num (range wf-threads)]
    (debug (format "  thread %02d: firing" thread-num))
    (future (thread-writer thread-num vars))))

(defn run-loop! [{:keys [wf-threads wf-parallel-metrics
                                 wf-iterations wf-parallel-tags
                                 wf-interval] :as vars}]
  (loop [iteration 0]
    (if (= wf-iterations iteration)
      (shutdown-agents)
      (do
        (debug (format "iteration %d of %d" (inc iteration) wf-iterations))
        (run-thread vars)
        (Thread/sleep (* wf-interval 1000))
        (recur (inc iteration))))))

(defn default-type [k]
  (type (k defaults)))

(defn fix-var-types [var-list]
  "Coerce env vars (strings) to numbers"
  (into {}
    (for [[k v] var-list]
      [k
       (if (number? (k defaults))
         (Integer/parseInt (str v))
         v)])))

(defn setup-vars [var-list]
  "Override default settings with any assigned in environment variables"
  (merge var-list (select-keys env (keys var-list)) ))

(defn any-unset-vars [var-list]
  "Returns the first unset var it finds in var-list"
  (get (set/map-invert var-list) nil))

(defn point-rate [{:keys [wf-threads wf-parallel-metrics
                                     wf-iterations wf-parallel-tags
                                     wf-interval]}]
  "calculate and return the point rate the given params will create"
  (quot (* wf-threads wf-parallel-tags wf-parallel-metrics) wf-interval))

(defn print-opening-banner! [{:keys [wf-proxy wf-iterations wf-interval]
                              :as vars}]
  (println (format "Requested pps: %d" (point-rate vars)))
  (println (format "Duration:      %ds (%d iterations at %ds intervals)"
                   (* wf-iterations wf-interval) wf-iterations wf-interval))
  (println (str    "Endpoint:      " wf-proxy)))

(defn print-closing-banner! [t-0]
  (let [elapsed-time (- (now) t-0)]
    (println (format "Elapsed time:  %ds" elapsed-time))
    (println (format "Sent points:   %d" @points-sent))
    (println (format "Actual pps:    %d" (quot @points-sent elapsed-time)))))

(defn -main []
  (let [vars (setup-vars defaults)
        t-0 (now)]
    (let [unset-vars (any-unset-vars vars)]
      (when unset-vars
        (println (str "ERROR: " unset-vars " is not set."))
        (comment System/exit 1))
      (print-opening-banner! (fix-var-types vars))
      (run-loop! (fix-var-types vars))
      (print-closing-banner! t-0)
      )))
