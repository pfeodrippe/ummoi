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

(defn deps-config
  []
  `{:deps ~'{org.clojure/clojure {:mvn/version "1.10.1"}
             pfeodrippe/tla-edn {:mvn/version "0.4.0"}
             cheshire {:mvn/version "5.10.0"}
             clj-http {:mvn/version "3.10.1"}}
    :paths ["src" "classes"]})

(defn op-form
  [name {:keys [:module :args :run]} {:keys [:config-dir]}]
  (let [args (mapv symbol args)
        arg->value# (gensym)]
    `(spec/defop ~(symbol name) {:module ~module}
       ~args
       (let [~arg->value# (->> (mapv tla-edn/to-edn ~args)
                               (mapv (fn [arg-name# arg-value#]
                                       [arg-name# arg-value#])
                                     ~(mapv str args))
                               (into {}))]
         ~(case (keyword (:type run))
            :shell
            `(let [env-vars# (->> ~arg->value#
                                  (mapv (fn [[arg-name# arg-value#]]
                                          [arg-name# (json/generate-string arg-value#)]))
                                  (into {}))
                   response# (sh/with-sh-dir ~config-dir
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
                                      :env-vars env-vars#})))))

            :http-post
            `(let [form-params# ~arg->value#
                   response# (http/post ~(:endpoint run)
                                        {:form-params form-params#
                                         :throw-exceptions false
                                         :content-type :json
                                         :as :json})]
               (if (>= 299 (:status response#) 200)
                 (tla-edn/to-tla-value (:body response#))
                 (do (pp/pprint
                      {:message (str "Error running operator " ~name)
                       :operator ~name
                       :form-params form-params#
                       :error-response response#})
                     (throw (ex-info (str "Error running operator " ~name)
                                     {:operator ~name
                                      :form-params form-params#}))))))))))

(defn error
  [msg]
  (println "ERROR: " msg)
  (System/exit 1))

(def json-example
  "{\n  \"spec-file\" : \"dev/ummoi/resources/example.tla\",\n  \"operators\" : {\n    \"TransferMoney\" : {\n      \"module\" : \"example\",\n      \"args\" : [ \"self\", \"account\", \"vars\" ],\n      \"run\" : {\n        \"type\" : \"shell\",\n        \"command\" : [ \"dev/ummoi/resources/example_using_env.py\" ]\n      }\n    }\n  }\n}")

(def edn-example
  "{:spec-file \"dev/ummoi/resources/example.tla\"\n :operators\n {\"TransferMoney\"\n  {:module \"example\"\n   :args [self account vars]\n   :run {:type :shell\n         :command [\"dev/ummoi/resources/example_using_env.py\"]}}}}")

(defn core-form
  [{:keys [:spec-file :config-file :operators]} {:keys [:verbose? :config-dir] :as opts}]
  (cond
    (nil? spec-file) (error "missing `spec-file` key (\"path/to/spec.tla\")")
    (or (nil? operators)
        (some nil? (mapcat (juxt :module :args :run) (vals operators))))
    (error (str "invalid `operators` key\n"
                "see examples below: \n\nEDN\n" edn-example
                "\n\nJSON\n\n" json-example)))
  (let [spec-path (.getCanonicalPath ^java.io.File (io/file config-dir spec-file))
        config-path (or config-file
                        (str/replace (.getName ^java.io.File (io/file spec-file)) #"tla" "cfg"))]
    (when verbose?
      (deps/describe [[:spec-file spec-file]
                      [:spec-path spec-path]
                      [:config-file config-file]
                      [:config-path config-path]]))
    (->>
     `[(~'ns ummoi-runner.core
        ~'(:require
           [cheshire.core :as json]
           [clj-http.client :as http]
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
         (spec/run-spec ~spec-path ~config-path)
         (System/exit 0))]
     (map str)
     (str/join "\n"))))

(defn parse-command-line-args
  [command-line-args]
  (let [[config-file config-file-type]
        (or (->> (partition 2 1 command-line-args)
                 (some (fn [[opt path]]
                         (when (contains? #{"-c" "--config"} opt)
                           (let [file (io/as-file path)
                                 extension (fs/extension file)]
                             (if (and (.exists file) (contains? #{".edn" ".json"} extension))
                               [file ({".edn" :edn ".json" :json} extension)]
                               (error (str "configuration file does not exist "
                                           "or it's invalid (only .json or .edn files are accepted): "
                                           [opt path]))))))))
            (cond
              (.exists (io/as-file "ummoi.edn")) [(io/as-file "ummoi.edn") :edn]
              (.exists (io/as-file "ummoi.json")) [(io/as-file "ummoi.json") :json]
              :else (error "must exist a `ummoi.edn` or `ummoi.json` file")))]
    {:config-file config-file
     :config-file-type config-file-type
     :verbose? (some #(contains? #{"--verbose"} %) command-line-args)}))

(defn create-clj-project
  [ummoi-config opts-map]
  (let [path (.getPath ^java.io.File (fs/temp-dir "ummoi-"))
        tlc-overrides-path (.getPath ^java.io.File (fs/temp-file ""))
        _ (fs/mkdirs (str path "/src/ummoi_runner"))
        _ (fs/mkdirs (str path "/classes/tlc2/overrides"))
        deps-file (str path "/deps.edn")
        core-file (str path "/src/ummoi_runner/core.clj")]
    ;; create deps.edn and core.clj
    (spit deps-file (deps-config))
    (spit core-file (core-form ummoi-config opts-map))
    ;; copy TLCOverrides.class so you don't need to call ummoi-runner twice (the first
    ;; one would be for operators compilation)
    (io/copy (io/input-stream (io/resource "ummoi-runner/classes/tlc2/overrides/TLCOverrides.class"))
             (io/file tlc-overrides-path))
    (fs/copy tlc-overrides-path (str path "/classes/tlc2/overrides/TLCOverrides.class"))
    path))

(defn -main
  [& [which :as command-line-args]]
  (if (= which "deps.exe")
    (apply deps/-main (rest command-line-args))
    (let [{:keys [:config-file :config-file-type :verbose?]} (parse-command-line-args command-line-args)
          ummoi-config (if (= config-file-type :edn)
                         (clojure.edn/read-string (slurp config-file))
                         (json/parse-string (slurp config-file) keyword))
          config-dir (.getParent ^java.io.File (.getCanonicalFile ^java.io.File config-file))
          ummoi-path (deps/where "um")
          path (create-clj-project ummoi-config {:config-dir config-dir :verbose? verbose?})]
      (when verbose?
        (println "Project created at" path)
        (deps/describe [[:ummoi-path ummoi-path]
                        [:config-dir config-dir]
                        [:config-file config-file]
                        [:config-file-type config-file-type]
                        [:verbose? verbose?]
                        [:ummoi-config ummoi-config]
                        [:command-line-args command-line-args]]))
      ;; call the generated project using ummoi itself
      (cond
        (System/getProperty "sun.java.command")
        (deps/shell-command (->> ["cd" path "&&"
                                  "deps.exe"
                                  "-m" "ummoi-runner.core"]
                                 (str/join " ")
                                 (conj ["bash" "-c"]))
                            {:to-string? false
                             :throw? true
                             :show-errors? true})

        (empty? ummoi-path) (error "ummoi is not at your PATH, please add it")
        :else (deps/shell-command (->> ["cd" path "&&"
                                        ummoi-path "deps.exe"
                                        "-m" "ummoi-runner.core"]
                                       (str/join " ")
                                       (conj ["bash" "-c"]))
                                  {:to-string? false
                                   :throw? true
                                   :show-errors? true}))))
  (System/exit 0))
