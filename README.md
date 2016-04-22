# Redux for ClojureScript based on core.async and transducers. 
[![Clojars Project](https://img.shields.io/clojars/v/io.thdr/redux-cljs.svg)](https://clojars.org/io.thdr/redux-cljs)

Functional state management for reactive apps.

It's highly recommended to read [Redux overview](https://github.com/reactjs/redux) first.

## Differences from pure Redux

+ [Redux-thunk](https://github.com/gaearon/redux-thunk) is already part of redux-cljs.
+ Slightly different reducer's type signature.
+ No middlewares and enhancers yet. I'm thinking of adding extensibility in next releases.

## Usage

### Reducers

#### Creating reducers

Reducer must be a pure function of `action` which returns a function (also pure) of `state`.

```clojure
(def reducer
  (fn [action]
    (fn [state]
      (case (:type action)
        :inc-counter   (update-in state [:counter] inc)
        :dec-counter   (update-in state [:counter] dec)
        :reset-counter (assoc state [:counter] (:counter action))
        state))))
```

There is a macro which makes adding reducers easier:

```clojure
(require [thdr.redux-cljs.macros :as r])

(r/defreducer reducer [state data]
  :inc (update-in state [:counter] inc)
  :dec (update-in state [:counter] dec)
  :reset (assoc state [:counter] (:counter data)))
```

#### Combining reducers
 
 Two or more reducers can be combined to one.

```clojure
(require [thdr.redux-cljs.store :refer [combine-reducers]])

(defreducer first-reducer [state _]
  :first (assoc state :first "first"))

(defreducer second-reducer [state _]
  :second (assoc state :second "second"))

(def reducer ;; which can be passed to `create-store`
  (combine-reducers first-reducer second-reducer))
```

### Actions

Action should be a map with `:type` key or a function of `state` (see [redux-thunk](https://github.com/gaearon/redux-thunk))).

```clojure
(def inc-action {:type :inc})
(def dec-action {:type :dec})

(defn reset-action [value]
  {:type :reset
  :counter value})

(require [thdr.redux-cljs.store :refer [dispatch]]) ;; see below

;; useful for http requests and other stuff
(def thunk-action
  (fn [state]
    (dispatch state inc-action)
    (js/setTimeout (clj->js #(dispatch state dec-action)) 1000))
```

### Stores

```clojure
(require [thdr.redux-cljs :refer [create-store
                                  subscribe
                                  unsubscribe
                                  dispatch
                                  get-state]])

(def initial-state
  {:counter 0})

;; After store is created it must subscribe to actions stream.
;; It is also possible to create a store with empty initial state:
;;    (create-store reducer)
(def store
  (-> (create-store initial-state reducer)
      (subscribe)))

(dispatch store inc-action)      ;; => state == {:counter 1}
(dispatch store dec-action)      ;; => state == {:counter 0}
(dispatch store (reset-action 5) ;; => state == {:counter 5}

(dispatch store                  ;; => state == {:counter 6} ... and after 1 second
          thunk-action           ;;    state == {:counter 5}

(unsubscribe store) ;; closes core.async chanels
```

## Compatibility with ClojureScript React-based libraries

There's currently an adapter for Rum only but it should be trivial to use cljs-redux with any react-based library based on atoms. Don't forget to call `unsubscribe` on store in `componentWillUnmount` in order to prevent memory leaks.

### Rum example

```clojure
(ns rum-example.core
  (:require [thdr.redux-cljs.rum :refer [redux-store]]
            [thdr.redux-cljs.store :refer [dispatch get-state] :as store]
            [rum.core :as rum])
  (:require-macros [thdr.redux-cljs.macros :refer [defreducer]]))

(def initial-state {:counter 0})

(def inc-action {:type :inc})
(def dec-action {:type :dec})

(defreducer reducer [state data]
  :inc (update-in state [:counter] inc)
  :dec (update-in state [:counter] dec))

(rum/defc counter-component < rum/cursored rum/cursored-watch [counter]
  [:p (str "Counter is: " counter)])

(rum/defcs test-page < (redux-store initial-state reducer) [rum-state]
  (let [store (::store/store rum-state)
        counter (rum/cursor (get-state store [:counter]))]
    [:.page
	  (counter-component counter)
	  [:.controls
	    [:span {:on-click #(dispatch store inc-action)} "+"]
	    [:span {:on-click #(dispatch store dec-action)} "-"]]]))

(rum/mount (test-page) js/document.body)
```

### Reagent (not tested yet)

To use redux-cljs with Reagent you should tell `create-store` to use `ratom` instead of plain Clojure `atom`:

```clojure
(require [reagent.core :as r])

...

(def store (create-store initial-state reducer :atom-fn #'r/atom))
```

## To-do

+ Validate actions with `plumatic/schema`
+ Add examples
+ Write docstrings :)
+ Test reagent
+ Maybe add middlewares/enhances.


## License

Copyright © 2016 Theodore Konukhov <me@thdr.io>

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
