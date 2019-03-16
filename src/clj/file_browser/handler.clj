(ns file-browser.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.cors :refer [wrap-cors]]
            [clojure.java.io :as io]))

(defn file-format [name]
  (-> name (clojure.string/split #"\.") last))

(defn handle-browse [req]
  (let [path ((:query-params req) "path")
        contents (->> (.listFiles (io/file path))
                      (map (fn [file]
                        (let [name (.getName file)
                              path (.getPath file)]
                          (if (.isDirectory file)
                            {:name name
                             :path path
                             :type :directory}
                            {:name name
                             :path path
                             :type :file
                             :format (file-format name)}))))
                      (sort-by :name))]

    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (-> contents vec prn-str)}))

(defn handle-preview [req]
  (let [path ((:query-params req) "path")]
    {:status 200
      :headers {"Content-Type" "text/plain"}
      :body (slurp path)}))

(defn handle-download [req]
  (let [path ((:query-params req) "path")
        file (io/file path)
        name (.getName file)]
    {:status 200
    :headers {"Content-Disposition" (str "filename=\"" name "\"")}
    :body (io/file path)}))


(defroutes app-routes
  (GET "/browse" [] handle-browse)
  (GET "/download" [] handle-download)
  (GET "/txt-preview" [] handle-preview)
  (route/not-found "Not Found"))

(def app
  (-> app-routes
    (wrap-defaults site-defaults)
    (wrap-cors :access-control-allow-origin [#"http://localhost:3449" #"127.0.0.1" #"0.0.0.0"]
               :access-control-allow-methods [:get :put :post :delete])))