(ns ummoi.core
  (:gen-class)
  (:require
   [borkdude.deps :as deps]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [me.raynes.fs :as fs])
  (:import
   (java.io File)))

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


(defn deps-config
  []
  `{:deps ~'{org.clojure/clojure {:mvn/version "1.10.1"}
             pfeodrippe/tla-edn {:mvn/version "0.4.0"}
             cheshire {:mvn/version "5.10.0"}}
    :paths ["src" "classes"]})

(defn op-form
  [name {:keys [:module :args :run]} {:keys [:user-dir]}]
  (let [args (mapv symbol args)]
    `(spec/defop ~(symbol name) {:module ~module}
       ~args
       ~(case (keyword (:type run))
          :shell
          `(let [env-vars# (->> (mapv (comp json/generate-string tla-edn/to-edn) ~args)
                                (mapv (fn [arg-name# arg-value#]
                                        [arg-name# arg-value#])
                                      ~(mapv str args))
                                (into {}))
                 response# (sh/with-sh-dir ~user-dir
                             (sh/with-sh-env env-vars# (apply sh/sh ~(:command run))))]
             (if (empty? (:err response#))
               (-> (:out response#)
                   json/parse-string
                   tla-edn/to-tla-value)
               (do (println :OUT (:out response#))
                   (println :ERR (:err response#))
                   (pp/pprint
                    {:message (str "Error running operator " ~name)
                     :operator ~name
                     :env-vars env-vars#})
                   (throw (ex-info (str "Error running operator " ~name)
                                   {:operator ~name
                                    :env-vars env-vars#})))))))))

(defn error
  [msg]
  (println "ERROR: " msg)
  (System/exit 1))

(def json-example
  "{\n  \"spec-file\" : \"dev/ummoi/resources/example.tla\",\n  \"operators\" : {\n    \"TransferMoney\" : {\n      \"module\" : \"example\",\n      \"args\" : [ \"self\", \"account\", \"vars\" ],\n      \"run\" : {\n        \"type\" : \"shell\",\n        \"command\" : [ \"dev/ummoi/resources/ex.py\" ]\n      }\n    }\n  }\n}")

(def edn-example
  "{:spec-file \"dev/ummoi/resources/example.tla\"\n :operators\n {\"TransferMoney\"\n  {:module \"example\"\n   :args [self account vars]\n   :run {:type :shell\n         :command [\"dev/ummoi/resources/ex.py\"]}}}}\n\n\n")

(defn core-form
  [{:keys [:spec-file :config-file :operators]} {:keys [:verbose?] :as opts}]
  (cond
    (nil? spec-file) (error "missing `spec-file` key (\"/path/to/spec.tla\")")
    (or (nil? operators)
        (some nil? (mapcat (juxt :module :args :run) (vals operators))))
    (error (str "invalid `operators` key (\"/path/to/spec.tla\")\n"
                "see examples below: \n\nEDN\n" edn-example
                "\n\nJSON\n" json-example)))
  (let [spec-file (.getAbsolutePath ^java.io.File (io/file spec-file))
        config-file (or config-file
                        (str/replace (.getName ^java.io.File (io/file spec-file)) #"tla" "cfg"))]
    (when verbose?
      (deps/describe [[:spec-file spec-file]
                      [:config-file config-file]]))
    (->>
     `[(~'ns ummoi-runner.core
        ~'(:require
           [cheshire.core :as json]
           [clojure.java.shell :as sh]
           [clojure.pprint :as pp]
           [tla-edn.core :as tla-edn]
           [tla-edn.spec :as spec]))

       ~@(mapv (fn [[name op-args]]
                 (op-form name op-args opts))
               operators)

       (defn ~'-main
         []
         ;; here we pass the tla file and tlc config file paths.
         ;; if a tlc file is not passed, it's assumed the same filename as the tla file.
         (spec/run-spec ~spec-file ~config-file)
         (System/exit 0))]
     (map str)
     (str/join "\n"))))

(defn -main
  [& [which :as command-line-args]]
  (if (= which "deps.exe")
    (apply deps/-main (rest command-line-args))
    (let [path (.getPath ^java.io.File (fs/temp-dir "ummoi-"))
          tlc-overrides-path (.getPath ^java.io.File (fs/temp-file ""))
          _ (fs/mkdirs (str path "/src/ummoi_runner"))
          _ (fs/mkdirs (str path "/classes/tlc2/overrides"))
          deps-file (str path "/deps.edn")
          core-file (str path "/src/ummoi_runner/core.clj")
          from-java? (System/getProperty "sun.java.command")
          ummoi-path (let [p (deps/where "./umm")]
                       (if-not (empty? p)
                         p
                         (deps/where "um")))
          user-dir (System/getProperty "user.dir")]
      (println "Project created at" path)
      ;; create deps.edn and core.clj
      (spit deps-file (deps-config))
      (cond
        (.exists (io/as-file "ummoi.edn"))
        (spit core-file (core-form (clojure.edn/read-string (slurp "ummoi.edn"))
                                   {:user-dir user-dir
                                    :verbose? (= (first command-line-args) "-v")}))

        (.exists (io/as-file "ummoi.json"))
        (spit core-file (core-form (json/parse-string (slurp "ummoi.json") keyword)
                                   {:user-dir user-dir
                                    :verbose? (= (first command-line-args) "-v")}))

        :else (error "must exist a `ummoi.edn` or `ummoi.json` file"))
      ;; copy TLCOverrides.class so you don't need to call ummoi-runner twice (the first
      ;; one would be for operators compilation)
      (io/copy (io/input-stream (io/resource "ummoi-runner/classes/tlc2/overrides/TLCOverrides.class"))
               (io/file tlc-overrides-path))
      (fs/copy tlc-overrides-path (str path "/classes/tlc2/overrides/TLCOverrides.class"))
      ;; call the generated project using ummoi itself
      (cond
        from-java? (deps/shell-command (->> ["cd" path "&&"
                                             "deps.exe"
                                             "-m" "ummoi-runner.core"]
                                            (str/join " ")
                                            (conj ["bash" "-c"]))
                                       {:to-string? false
                                        :throw? true
                                        :show-errors? true})
        (empty? ummoi-path) (error "ummoi is not at your path, please add it")
        :else (deps/shell-command (->> ["cd" path "&&"
                                        ummoi-path "deps.exe"
                                        "-m" "ummoi-runner.core"]
                                       (str/join " ")
                                       (conj ["bash" "-c"]))
                                  {:to-string? false
                                   :throw? true
                                   :show-errors? true}))))
  (System/exit 0))
