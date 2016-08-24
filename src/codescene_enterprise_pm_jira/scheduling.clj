(ns codescene-enterprise-pm-jira.scheduling
  (:require [clojure.core.async :refer [chan go alts! timeout >!!]]
            [taoensso.timbre :as log]
            [codescene-enterprise-pm-jira.sync :as sync]))

(defmacro fix-delay
  "Runs the body every ms after the last appication of body completed."
  [ms & body]
  `(let [close-ch# (chan)]
     (go (loop []
           (let [[v# ch#] (alts! [close-ch# (timeout ~ms)])]
             (if (not (= close-ch# ch#))
               (do
                 ~@body
                 (recur))))))
     close-ch#))

(def ^:private ^:const default-sync-hour-interval 1)

(defn- parse-hour-interval [{{:keys [hour-interval]} :sync}]
  (cond
    (integer? hour-interval)
    hour-interval

    (nil? hour-interval)
    (do
      (log/warnf "Sync hour interval is not configured, defaulting to %d."
                 default-sync-hour-interval)
      default-sync-hour-interval)

    :else
    (try
      (Integer/parseInt hour-interval)
      (catch NumberFormatException _
        (log/warnf "Invalid sync hour interval \"%s\", defaulting to %d."
                   hour-interval
                   default-sync-hour-interval)
        default-sync-hour-interval))))

(def ^:private close-channel (atom nil))

(defn stop-scheduled-sync []
  (when @close-channel
    (>!! @close-channel :stop))
  (reset! close-channel nil))

(defn start-scheduled-sync
  ([config]
   (start-scheduled-sync false config))
  ([run-initial config]
   (stop-scheduled-sync)

   (when run-initial
     (log/info "Running initial sync...")
     (sync/sync-all-projects config))

   (let [hour-interval (parse-hour-interval config)
         ms-interval (* 1000 60 60 hour-interval)]
     (log/infof "Scheduling sync to run every %d hour(s)." hour-interval)
     (reset! close-channel (fix-delay ms-interval
                                      (sync/sync-all-projects config))))))
