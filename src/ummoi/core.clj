(ns ummoi.core
  (:gen-class)
  (:require
   [borkdude.deps :as deps]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [me.raynes.fs :as fs]
   #_[tla-edn.core :as tla-edn]
   #_[tla-edn.spec :as spec])
  (:import
   (java.io File)
   (java.lang ProcessBuilder$Redirect)))

#_(sh/sh)

#_"um -cfg p.edn"

(def ummoi-config
  '{:operators
    {"TransferMoney"
     {:module "example"
      :args [a b c]
      :run {:type :shell
            :command ["bb" "a.clj" x y z]}}}})

(def vars-keys
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

(defn pp-spit
  [file data]
  (spit file (with-out-str (pp/pprint data))))

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
   (map #(with-out-str (pp/pprint %)))
   (str/join "\n")))

(def op-forms
  (mapv (fn [[name {:keys [:args :module :run]}]]
          (op-form {:name name :args args :module module}))
        (:operators ummoi-config)))

(defn deps-config
  [dir]
  `{:deps ~'{org.clojure/clojure {:mvn/version "1.10.1"}
             pfeodrippe/tla-edn {:mvn/version "0.4.0-SNAPSHOT"}}
    :paths ~(mapv #(str dir "/" %) ["src" "classes"])})

(def ^:dynamic *cwd* ".")

(defn shell-command
  "Executes shell command.

  Accepts the following options:

  `:input`: instead of reading from stdin, read from this string.

  `:to-string?`: instead of writing to stdoud, write to a string and
  return it.

  `:throw?`: Unless `false`, exits script when the shell-command has a
  non-zero exit code, unless `throw?` is set to false."
  ([args] (shell-command args nil))
  ([args {:keys [:input :to-string? :throw? :show-errors?]
          :or {throw? true
               show-errors? true}
          :as opts}]
   (clojure.pprint/pprint
    {:args args
     :opts opts})
   (let [args (->> (mapv str args)
                   (str/join " ")
                   (apply str "cd " *cwd* " && ")
                   (conj ["bash" "-c"]))
         pb (cond-> (ProcessBuilder. ^java.util.List args)
              show-errors? (.redirectError ProcessBuilder$Redirect/INHERIT)
              (not to-string?) (.redirectOutput ProcessBuilder$Redirect/INHERIT)
              (not input) (.redirectInput ProcessBuilder$Redirect/INHERIT))
         proc (.start pb)]
     (when input
       (with-open [w (io/writer (.getOutputStream proc))]
         (binding [*out* w]
           (print input)
           (flush))))
     (let [string-out
           (when to-string?
             (let [sw (java.io.StringWriter.)]
               (with-open [w (io/reader (.getInputStream proc))]
                 (io/copy w sw))
               (str sw)))
           exit-code (.waitFor proc)]
       (when (and throw? (not (zero? exit-code)))
         (System/exit exit-code))
       string-out))))

(comment

  (shell-command ["bash" "-c" "cd ../ && ls"]
                 {:to-string? true, :throw? false, :show-errors? false})

  ())


(def my-ns *ns*)

(defn -main
  []
  (let [{:keys [:path]} (bean (fs/temp-dir "ummoi-"))
        _ (fs/mkdirs (str path "/src/ummoi_runner"))
        _ (fs/mkdirs (str path "/classes/tlc2/overrides"))
        deps-file (str path "/deps.edn")
        core-file (str path "/src/ummoi_runner/core.clj")]
    (println :PATH path)
    (pp-spit deps-file (deps-config path))
    (spit core-file (core-form op-forms))
    (binding [*cwd* path]
      (with-redefs [deps/shell-command shell-command]
        (fs/with-cwd (fs/file path)
          (deps/-main "-Sdeps-file" deps-file "-m" "ummoi-runner.core")))))
  #_(spec/run-spec (.getAbsolutePath (File. "resources/example.tla"))
                   "example.cfg")
  (System/exit 0))
