(ns wf-soaker.core
  (:require [clj-http.client :as client]
            [clojure.string :as str]))

;; There's some difficulty in this problem in that Wavefront has a resolution
;; of one second. So, sending a thousand points per second from a single host
;; will result in one point in Wavefront.
;;
;; Therefore we have to make ourselves appear like many different hosts
;; sending many different metrics. But we can't make the cardinality of any
;; metrics too high.
;;
;; Start a REPL, and call the run-for-some-time function with suitable args.

;; each second, we will send a bundle containing this many different metrics
;;
(def parallel-metrics 10)

;; and each of those metrics will be repeated this many times with different
;; values for the 'dtag' point tag
;;
(def parallel-tags 10)

;; So the number of points-per-second we send is
;; parallel-metrics × parallel-tags × number-of-threads

(def debug-on true)
(def endpoint "http://wavefront.localnet:2878")
(def metric-path "soak.dev")

(defn now [] (quot (System/currentTimeMillis) 1000))

(defn send-data [data]
  "POST a chunk of data to a Wavefront endpoint"
  (client/post endpoint {:body data}))

(defn- point-value []
  (rand-int 100))

(defn- metric-name [path-num]
  (str metric-path ".path-" path-num))

(defn- point-tag [tag-num]
  (str "dtag=" tag-num))

(defn- source-tag [source-num]
  (str "source=soaker-" source-num))

(defn- debug [& chunks]
  (if debug-on
    (println (apply str chunks))))

(defn- point-bundle [thread-num metric-range tag-range]
  "makes a vector of metric-range × tag-range points"
  (for [metric-num metric-range tag-num tag-range]
    (str/join
      " "
      [(metric-name metric-num) (point-value) (now) (source-tag thread-num)
       (point-tag tag-num)])))

(defn- thread-writer [thread-num]
  "after some random sub-second interval, sends a point bundle to Wavefront"
  (let [sleep-time (rand-int 1000)
        bundle (point-bundle thread-num (range parallel-metrics)
                             (range parallel-tags))]
    (debug "  thread " thread-num " sleeping " sleep-time "s")
    (Thread/sleep sleep-time)
    (debug "  thread " thread-num " sending " (count bundle) " points")
    (send-data (str/join "\n" bundle))))

(defn- run-thread [threads]
  "Run as many parallel write operations as are requested"
  (doseq [thread-num (range threads)]
    (debug "  -> firing thread " thread-num)
    (future (thread-writer thread-num))))

(defn run-for-some-time [threads desired-iterations]
  (println
    (str
      "sending " (* threads parallel-tags parallel-metrics) "pps for "
      desired-iterations "s."))
  (loop [iteration 0]
    (when-not (= desired-iterations iteration)
      (debug "running iteration " iteration)
      (run-thread threads)
      (Thread/sleep 1000)
      (recur (inc iteration)))))
