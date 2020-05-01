(ns ummoi.core
  (:gen-class)
  (:require
   [clojure.java.shell :as sh]
   [tla-edn.core :as tla-edn]
   [tla-edn.spec :as spec])
  (:import
   (java.io File)))

#_(sh/sh)

#_"um -cfg p.edn"

#_'{:operators
  {"TransferMoney"
   {:args [x y z]
    :call {:type :shell
           :command ["bb" "a.clj" x y z]}}}}

#_(spec/defop TransferMoney {:module "example"}
    [_ _ _ ])

(def vars-keys
  [:c1 :c2 :account :receiver-new-amount :sender-new-amount :sender
   :receiver :money :pc])

(defn -main
  [& [n]]
  (println :CLASSES @spec/classes-to-be-loaded)
  (println :LOADED_BEFORE? (spec/classes-loaded?))
  (spec/compile-operators 'ummoi.core)
  (println :LOADED_AFTER? (spec/classes-loaded?))
  (spec/run-spec (.getAbsolutePath (File. "resources/example.tla"))
                 "example.cfg"
                 ["-workers" "1"])
  (System/exit 0))
