(ns thdr.redux-cljs.store
  (:require [cljs.core.async :as async :refer [<! >!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defprotocol IReduxStore
  (get-state       [this])
  (subscribe       [this])
  (unsubscribe     [this])
  (dispatch        [this action])
  (replace-reducer [this next-reducer]))

(defn- make-event-bus
  [reducer]
  (async/chan 1 (map reducer)))

(defrecord Store [state bus event-loop]
  IReduxStore

  (get-state [_]
    state)

  (subscribe [this]
    (if-not event-loop
      (let [event-loop (go-loop []
                         (when-let [state-fn (<! bus)]
                           (swap! state state-fn)
                           (recur)))]
        (assoc this :event-loop event-loop))
      this))

  (unsubscribe [this]
    (if bus
      (do
        (async/close! bus)
        (async/close! event-loop)
        (assoc this :bus nil :event-loop nil))
      this))

  (dispatch [this action]
    (if (fn? action)
      (action this)
      (when event-loop
        (go (>! bus action)))))

  (replace-reducer [this next-reducer]
    (-> this
        unsubscribe
        (assoc :bus (make-event-bus next-reducer))
        subscribe)))

(defn combine-reducers [& reducers]
  (fn [action]
    (apply comp (map #(% action) reducers))))

(defn create-store
  ([reducer] (create-store {} reducer))
  ([initial-state reducer & {:keys [atom-fn] :as opts
                             :or {atom-fn #'atom}}]
   {:pre [#(associative? initial-state)]}
   (let [state  (atom-fn initial-state)
         bus    (make-event-bus reducer)]
     (map->Store {:state state :bus bus}))))
