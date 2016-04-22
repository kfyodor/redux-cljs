(ns thdr.redux-cljs.rum
  (:require [thdr.redux-cljs.store:refer [create-store
                                           subscribe
                                           unsubscribe] :as store]))

(defn redux-store
  [initial-state reducer]
  (let [key ::store/store]
    {:transfer-state
     (fn [old new]
       (assoc new key (old key)))

     :will-mount
     (fn [state]
       (let [store (create-store initial-state reducer)]
         (assoc state key (subscribe store))))

     :will-unmount
     (fn [state]
       (let [store (-> key state unsubscribe)]
         (assoc state key nil)))}))
