(ns thdr.redux-cljs.macros)

(defmacro defreducer
  "Define Redux-like reducer"
  [name bindings & matches]
  {:pre [#(vector? bindings)
         #(= (2 (count bindings)))]}
  (let [[state data] bindings]
    `(def ~name
       (fn [action#]
         (fn [~state]
           (let [~data (dissoc action# :type)]
             (case (:type action#)
               ~@matches
               ~state)))))))

(comment
  (defreducer inc-reducer [state data]
    :inc   (update-in state [:counter] inc)
    :dec   (update-in state [:counter] dec)
    :reset (assoc state :counter (:counter data))))
