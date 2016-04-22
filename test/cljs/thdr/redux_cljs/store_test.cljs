(ns thdr.redux-cljs.store-test
  (:require-macros [cljs.test :refer [deftest testing is async]]
                   [thdr.redux-cljs.macros :refer [defreducer]]
                   [cljs.core.async.macros :refer [go]])
  (:require [cljs.test]
            [cljs.core.async :refer [<! >! chan close!]]
            [reagent.core :as r]
            [thdr.redux-cljs.store :as s]))

;;;;;;;;;; testing data ;;;;;;;;;;

(def initial-state {:counter 0})

(defn reset-action
  [counter]
  {:type :reset
   :counter counter})

(def inc-action {:type :inc})
(def dec-action {:type :dec})

(def add-stuff-action
  {:type :add-stuff
   :stuff "stuff"})

(def thunk-action
  (fn [state]
    (dotimes [_ 2] (s/dispatch state inc-action))
    (s/dispatch state dec-action)))

(def do-a-action {:type :do-a})
(def do-b-action {:type :do-b})

(defreducer test-reducer [state data]
  :inc   (update-in state [:counter] inc)
  :dec   (update-in state [:counter] dec)
  :reset (assoc state :counter (:counter data)))

(defreducer test-replace-reducer [state data]
  :add-stuff (assoc state :stuff (:stuff data)))

(defreducer test-reducer-a [state _]
  :do-a (assoc state :a "a"))

(defreducer test-reducer-b [state _]
  :do-b (assoc state :b "b"))

;;;;;;;;;; tests ;;;;;;;;;;

(deftest store-test
  (let [store (s/create-store initial-state test-reducer)]
    (testing "create store"
      (is (satisfies? s/IReduxStore store))
      (is (instance? s/Store store))
      (is (nil? (:event-loop store))))

    (testing "get state"
      (is (= initial-state @(s/get-state store))))))

(deftest initial-state-is-an-atom
  (testing "clojure atom"
    (let [state (atom {})
          store (s/create-store state test-reducer)]
      (is (= state (s/get-state store)))))

  (testing "reagent atom"
    (let [state (r/atom {})
          store (s/create-store state test-reducer)]
      (is (= state (s/get-state store)))))

  (testing ":atom-fn atom"
    (let [store (s/create-store initial-state test-reducer :atom-fn atom)
          state (s/get-state store)]
      (is (instance? Atom state))
      (is (= initial-state @state))))

  (testing ":atom-fn ratom"
    (let [store (s/create-store initial-state test-reducer :atom-fn r/atom)
          state (s/get-state store)]
      (is (instance? reagent.ratom.RAtom state))
      (is (= initial-state @state)))))

(deftest dispatch-test
  (let [store (s/subscribe (s/create-store initial-state test-reducer))
        state (s/get-state store)
        chan  (chan)]
    (async done
      (add-watch state :dispatch-test (fn [_ _ _ new-state]
                                        (go (>! chan new-state))))

      (go
        (s/dispatch store inc-action)
        (is (= 1 (:counter (<! chan))))

        (s/dispatch store dec-action)
        (is (= 0 (:counter (<! chan))))

        (s/dispatch store (reset-action 5))
        (is (= 5 (:counter (<! chan))))

        (s/unsubscribe store)
        (remove-watch state :dispatch-test)
        (close! chan)
        (done)))))

(deftest thunk-action-test
  (let [store (s/subscribe (s/create-store initial-state test-reducer))
        state (s/get-state store)
        chan  (chan)]
    (async done
      (add-watch state :thunk-action-test (fn [_ _ _ new-state]
                                            (go (>! chan new-state))))

      (go
        (s/dispatch store thunk-action)

        (is (= 1 (:counter (<! chan))))
        (is (= 2 (:counter (<! chan))))
        (is (= 1 (:counter (<! chan))))

        (s/unsubscribe store)
        (remove-watch state :thunk-action-test)
        (close! chan)
        (done)))))

(deftest replace-reducer-test
  (let [store (-> (s/create-store initial-state test-reducer)
                  (s/subscribe)
                  (s/replace-reducer test-replace-reducer))
        state (s/get-state store)
        chan  (chan)]

    (async done
      (add-watch state :replace-reducer-test (fn [_ _ _ new-state]
                                               (go (>! chan new-state))))

      (go
        (s/dispatch store add-stuff-action)
        (is (= "stuff" (:stuff (<! chan))))

        (s/unsubscribe store)
        (remove-watch state :replace-reducer-test)
        (close! chan)
        (done)))))

(deftest combine-reducers-test
  (let [reducer (s/combine-reducers test-reducer-a test-reducer-b)
        store   (s/subscribe (s/create-store initial-state reducer))
        state   (s/get-state store)
        chan    (chan)]
    (async done
      (add-watch state :combine-reducers-test (fn [_ _ _ new-state]
                                                (go (>! chan new-state))))

      (go
        (s/dispatch store do-a-action)
        (is (= "a" (:a (<! chan))))

        (s/dispatch store do-b-action)
        (is (= "b" (:b (<! chan))))

        (s/unsubscribe store)
        (remove-watch state :combine-reducers-test)
        (close! chan)
        (done)))))
