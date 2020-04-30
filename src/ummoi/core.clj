(ns ummoi.core
  (:gen-class)
  (:require
   [clojure.java.shell :as sh]
   [tla-edn.core :as tla-edn]
   [tla-edn.spec :as spec]))

#_(sh/sh)

#_"um -cfg p.edn"

#_'{:operators
  {"TransferMoney"
   {:args [x y z]
    :call {:type :shell
           :command ["bb" "a.clj" x y z]}}}}

(spec/defop ex {:module "par"}
  [olha eit ss])

(defn -main
  [& args]
  (println :LOADED? (spec/classes-loaded?))
  (spec/compile-operators 'ummoi.core)
  (println :LOADED? (spec/classes-loaded?))
  (doall (repeatedly 20 #(spec/compile-operators 'ummoi.core)))
  (println "Hello, World!")
  (System/exit 0))
