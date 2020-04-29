(ns ummoi.core
  (:require
   [clojure.java.shell :as sh]
   [tla-edn.core :as tla-edn]))

#_(sh/sh)

"um -cfg p.edn"

'{:operators
  {"TransferMoney"
   {:args [x y z]
    :call {:type :shell
           :command ["bb" "a.clj" x y z]}}}}
