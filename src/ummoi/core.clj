(ns ummoi.core
  (:gen-class)
  #_(:require
   #_[clojure.java.shell :as sh]
   [tla-edn.core :as tla-edn]))

#_(sh/sh)

#_"um -cfg p.edn"

#_'{:operators
  {"TransferMoney"
   {:args [x y z]
    :call {:type :shell
           :command ["bb" "a.clj" x y z]}}}}

(defn -main
  [& args]
  (println "Hello, World!"))
