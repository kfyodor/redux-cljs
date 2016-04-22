(set-env! :source-paths #{"src/clj" "src/cljs"}
          :dependencies '[[org.clojure/clojurescript "1.8.40" :scope "provided"]
                          [org.clojure/core.async "0.2.374"   :scope "provided"]
                          [adzerk/bootlaces "0.1.13"          :scope "test"]
                          [rum "0.8.1"                        :scope "test"]
                          [reagent "0.6.0-alpha"              :scope "test"]

                          [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT"
                                                       :scope "test"]])

(require '[crisptrutski.boot-cljs-test :refer [test-cljs]]
         '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0-SNAPSHOT")

(deftask with-test-paths []
  (merge-env! :source-paths #{"test/cljs"})
  identity)

(deftask test []
  (comp (with-test-paths)
        (test-cljs)))