(ns thdr.redux-cljs.rum
  "This namespace provides mixin and some
  other helpers for compatibility with Rum."
  (:require [thdr.redux-cljs.store :refer [create-store
                                           get-state
                                           subscribe
                                           unsubscribe] :as store]))

(def default-context-key ::store/state-atom)
(def default-store-key   ::store/store)

(defn- context-types-map
  [context-types-key context-key]
  {:class-properties {context-types-key
                      (clj->js
                       {context-key (js/React.PropTypes.instanceOf Atom)})}})

(defn- child-context-types [key]
  (context-types-map :childContextTypes key))

(defn context-types
  "Helper method which provides initial Rum mixin
  for dealing with child contexts in Rum components."
  ([] (context-types default-context-key))
  ([key] (context-types-map :contextTypes key)))

(defn context->redux-state
  ([comp-state] (context->redux-state comp-state default-context-key))
  ([comp-state key]
   (-> comp-state
       :rum/react-component
       .-context
       (js->clj)
       (get (name key)))))

(defn redux-store
  "Creates mixin which puts store object into Rum-component's map.

  Options:

  *key*: Key with which store will be associated in a component's map.

  *with-child-context?*: Adds `getChildContext` and `childContextTypes`

  *context-key*: Key with which redux state will be associated in
  React's context map. Ignored if `with-child-context?` is set to false."
  [initial-state reducer & {:keys [key context-key with-child-context?]
                            :or {key default-store-key
                                 context-key default-context-key
                                 with-child-context? false}}]
  (let [store (create-store initial-state reducer)
        mixin {:transfer-state
               (fn [old new]
                 (assoc new key (old key)))

               :will-mount
               (fn [state]
                 (assoc state key (subscribe store)))

               :will-unmount
               (fn [state]
                 (let [store (-> key state unsubscribe)]
                   (assoc state key nil)))}]
    (if with-child-context?
      (merge mixin
             {:child-context (fn [_] {context-key (get-state store)})}
             (child-context-types context-key))
      mixin)))
