#!/usr/bin/env bb

;; Cheers to Bob Herrmann o/
;; Taken and modified to add request body parsing from https://github.com/bherrmann7/bb-common/blob/master/wee_httpd.bb

(ns wee-httpd)

(import (java.net ServerSocket))
(require '[clojure.string :as string]
         '[clojure.java.io :as io]
         '[cheshire.core :as json])

(defn create-worker-thread [client-socket request-handler]
  (.start
   (Thread.
    (fn []
      (with-open [out (io/writer (.getOutputStream client-socket))
                  in (io/reader (.getInputStream client-socket))]
        (loop []
          (let [[req-line & headers] (loop [headers []]
                                       (let [line (.readLine in)]
                                         (if (string/blank? line)
                                           headers
                                           (recur (conj headers line)))))]
            (when-not (nil? req-line)
              (let [headers-map (->> headers
                                     (map #(update (string/split % #":" 2) 0 string/lower-case))
                                     (into {}))
                    content-length (Integer/parseInt (string/trim (get headers-map "content-length")))
                    status 200
                    body (-> (loop [buf ""
                                    count 1]
                               (let [c (.read in)]
                                 (if (>= count content-length)
                                   (str buf (char c))
                                   (recur (str buf (char c)) (inc count)))))
                             json/parse-string
                             request-handler
                             json/generate-string)]
                (.write out (format "HTTP/1.1 %s OK\r\nContent-Length: %s\r\n\r\n%s"
                                    status
                                    (count (.getBytes body))
                                    body))
                (.flush out)
                (recur))))))))))

(defn start-web-server [request-handler]
  (println "Server started \\o/")
  (.start (Thread. #(with-open
                      [server-socket (new ServerSocket 4444)]
                      (while true
                        (let [client-socket (.accept server-socket)]
                          (create-worker-thread client-socket request-handler )))))))

(def vars-keys
  [:c1 :c2 :account :sender :receiver :money :pc])

(defn handler
  [{self "self" vars "vars"}]
  (let [vars (zipmap vars-keys vars)
        sender (get-in vars [:sender self])
        receiver (get-in vars [:receiver self])
        money (get-in vars [:money self])]
    (-> (:account vars)
        (update sender - money)
        (update receiver + money))))

(wee-httpd/start-web-server handler)

;; Keep the shell script from returning.  When evaling the whole buffer, comment this out.
(while true (Thread/sleep 1000))
