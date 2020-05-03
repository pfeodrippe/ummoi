(ns ummoi.core
  (:gen-class)
  (:require
   [borkdude.deps :as deps]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.string :as str]
   [me.raynes.fs :as fs]
   #_[tla-edn.core :as tla-edn]
   #_[tla-edn.spec :as spec])
  #_(:import
   (java.io File)))

#_(sh/sh)

#_"um -c ummoi.edn example.tla"
#_"um example.tla"                      ; same folder as configuration

(def ummoi-config
  '{:operators
    {"TransferMoney"
     {:module "example"
      :args [a b c]
      :run {:type :shell
            :command ["bb" "a.clj" x y z]}}}})

#_(def vars-keys
  [:c1 :c2 :account :receiver-new-amount :sender-new-amount :sender
   :receiver :money :pc])

#_(spec/defop TransferMoney {:module "example"}
  [self account vars]
  (let [self (tla-edn/to-edn self)
        vars (zipmap vars-keys (tla-edn/to-edn vars))
        sender (get-in vars [:sender self])
        receiver (get-in vars [:receiver self])
        money (get-in vars [:money self])]
    (-> (tla-edn/to-edn account)
        (update sender - money)
        (update receiver + money)
        tla-edn/to-tla-value)))

#_(spec/defop TransferMoney {:module "example"}
  [self account vars]
  (let [self (tla-edn/to-edn self)
        vars (zipmap vars-keys (tla-edn/to-edn vars))
        sender (get-in vars [:sender self])
        receiver (get-in vars [:receiver self])
        money (get-in vars [:money self])]
    (-> (tla-edn/to-edn account)
        (update sender + money)
        (update receiver + money)
        tla-edn/to-tla-value)))

(defn op-form
  [{:keys [:name :module :args]}]
  `(spec/defop ~(symbol name) {:module ~module}
     ~args
     10))

(defn core-form
  [op-forms]
  (->>
   `[(~'ns ummoi-runner.core
      ~'(:require
         [clojure.java.shell :as sh]
         [tla-edn.core :as tla-edn]
         [tla-edn.spec :as spec]))

     ~@op-forms

     (defn ~'-main
       []
       (spec/run-spec "/home/rafael/dev/ummoi/resources/example.tla"
                      #_(.getAbsolutePath (File. "resources/example.tla"))
                      "example.cfg")
       (System/exit 0))]
   (map str)
   (str/join "\n")))

(def op-forms
  (mapv (fn [[name {:keys [:args :module :run]}]]
          (op-form {:name name :args args :module module}))
        (:operators ummoi-config)))

(defn deps-config
  []
  `{:deps {org.clojure/clojure {:mvn/version "1.10.1"}
           pfeodrippe/tla-edn {:mvn/version "0.4.0"}}
    :paths ["src" "classes"]})

(defn -main
  [& [which :as command-line-args]]
  (println :STA)
  (if (= which "deps.exe")
    (apply deps/-main (rest command-line-args))
    (let [path (.getPath ^java.io.File (fs/temp-dir "ummoi-"))
          tlc-overrides-path (.getPath ^java.io.File (fs/temp-file ""))
          _ (fs/mkdirs (str path "/src/ummoi_runner"))
          _ (fs/mkdirs (str path "/classes/tlc2/overrides"))
          deps-file (str path "/deps.edn")
          core-file (str path "/src/ummoi_runner/core.clj")
          java-cmd (System/getProperty "sun.java.command")
          command-dir (.getPath ^java.io.File fs/*cwd*)]
      (println (into {} (System/getProperties)))
      (println (into {} (System/getenv)))
      #_(println path)
      (do (println "Project created at" path)
          (spit deps-file (deps-config))
          (spit core-file (core-form op-forms))
          (io/copy (io/input-stream (io/resource "ummoi-runner/classes/tlc2/overrides/TLCOverrides.class"))
                   (io/file tlc-overrides-path))
          (fs/copy tlc-overrides-path (str path "/classes/tlc2/overrides/TLCOverrides.class"))
          (deps/shell-command
           (->> ["cd" path "&&"
                 "~/dev/ummoi/ummoi" "deps.exe"
                 "-m" "ummoi-runner.core"]
                (str/join " ")
                (conj ["bash" "-c"]))
           {:to-string? false
            :throw? true
            :show-errors? true}))))
  (System/exit 0))
