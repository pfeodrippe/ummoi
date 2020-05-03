(ns ummoi.core
  (:gen-class)
  (:require
   [borkdude.deps :as deps]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [me.raynes.fs :as fs]
   #_[tla-edn.core :as tla-edn]
   #_[tla-edn.spec :as spec])
  #_(:import
   (java.io File)))

#_(sh/sh)

#_"um -c ummoi.edn example.tla"
#_"um example.tla"                      ; same folder as configuration

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

(def ummoi-config
  '{:operators
    {"TransferMoney"
     {:module "example"
      :args [self account vars]
      :run {:type :shell
            :command ["/home/rafael/dev/ummoi/a.sh"]}}}})

(defn op-form
  [name {:keys [:module :args :run]}]
  `(spec/defop ~(symbol name) {:module ~module}
     ~args
     ~(case (:type run)
        :shell
        `(let [env-vars# (->> (mapv (comp json/generate-string tla-edn/to-edn) ~args)
                             (mapv (fn [arg-name# arg-value#]
                                     [arg-name# arg-value#])
                                   ~(mapv str args))
                             (into {}))]
          (sh/with-sh-env env-vars#
            (println :CMD (apply sh/sh ~(:command run))))))
     10))

(def op-forms
  (mapv (fn [[name op-args]]
          (op-form name op-args))
        (:operators ummoi-config)))

(defn deps-config
  []
  `{:deps ~'{org.clojure/clojure {:mvn/version "1.10.1"}
             pfeodrippe/tla-edn {:mvn/version "0.4.0"}
             cheshire {:mvn/version "5.10.0"}}
    :paths ["src" "classes"]})

(defn core-form
  [op-forms]
  (->>
   `[(~'ns ummoi-runner.core
      ~'(:require
         [cheshire.core :as json]
         [clojure.java.shell :as sh]
         [clojure.pprint :as pp]
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
      (println "Project created at" path)
      ;; create deps.edn and core.clj
      (spit deps-file (deps-config))
      (spit core-file (core-form op-forms))
      ;; copy TLCOverrides.class so you don't need to call ummoi-runner twice (the first
      ;; one would be for operators compilation)
      (io/copy (io/input-stream (io/resource "ummoi-runner/classes/tlc2/overrides/TLCOverrides.class"))
               (io/file tlc-overrides-path))
      (fs/copy tlc-overrides-path (str path "/classes/tlc2/overrides/TLCOverrides.class"))
      ;; call the generated project using ummoi itself
      (if java-cmd
        (deps/shell-command
         (->> ["cd" path "&&"
               "deps.exe"
               "-m" "ummoi-runner.core"]
              (str/join " ")
              (conj ["bash" "-c"]))
         {:to-string? false
          :throw? true
          :show-errors? true})
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
