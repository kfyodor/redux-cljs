(ns thdr.redux-cljs.store
  "Main API for creating and using Redux-cljs"
  (:require [cljs.core.async :as async :refer [<! >!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defprotocol IReduxStore
  (get-state [this]
    "Returns a state atom")

  (subscribe [this]
    "Creates a go-loop which reacts to actions stream.
    Must be called after store is created.")

  (unsubscribe [this]
    "Closes core.async channels. It's recommended to
    call `unsubscribe` in `will-unmount` React handlers
    in order to prevent memory leaks.")

  (dispatch [this action]
    "Puts an action (see [thdr.redux-cljs.macros/defaction])
    to main event loop. Action should be either a hash-map
    with required :type key or a function of store.")

  (replace-reducer [this next-reducer]
    "Replaces reducer passed in [create-store]"))

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
      (when event-loop ;; todo: validate action shape
        (go (>! bus action)))))

  (replace-reducer [this next-reducer]
    (-> this
        unsubscribe
        (assoc :bus (make-event-bus next-reducer))
        subscribe)))

(defn- atom? [obj]
  (satisfies? IAtom obj))

(defn- make-state-atom [initial-state atom-fn]
  {:pre [(or (associative? initial-state)
             (atom? initial-state))]}
  (if (atom? initial-state)
    initial-state
    (let [state (atom-fn initial-state)]
      (if (atom? state)
        state
        (throw (js/Error. (str "You are trying to pass as :atom-fn "
                               "something that doesn't make an atom.")))))))

(defn create-store
  "Creates a redux-store.

  **initial-state** should be either an associative
  data structure or an atom (which could be useful
  in development environments with live-reloading
  but not recommended in production environments).

  **reducer** is a function which matches action types
  and returns a function of state which then updates
  state atom via `swap!`.

  It should look like this:

  ```
  (fn [state]
   (fn [action]
     (case (:type action)
       ;; match action and update state
       state
  ```

  There is also a macro for that: [thdr.redux-cljs.macros/defreducer].

  Options:

  *atom-fn*: provide a custom function for creating state atom.
  For example, if you're using Reagent, you might want the state to be
  held in RAtom instead of plain Clojure atom."
  ([reducer] (create-store {} reducer))
  ([initial-state reducer & {:keys [atom-fn] :or {atom-fn #'atom}}]
   (let [state  (make-state-atom initial-state atom-fn)
         bus    (make-event-bus reducer)]
     (map->Store {:state state :bus bus}))))

(defn combine-reducers [& reducers]
  (fn [action]
    (apply comp (map #(% action) reducers))))
