(ns file-browser.core
    (:require [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [cljs.reader :refer [read-string]]
              [cljs.core.async :refer [chan <! >!]] [cljs-http.client :as http])
    (:require-macros [cljs.core.async.macros :refer [go]]))

(declare root node-view)

(enable-console-print!)

(def api "http://localhost:3000")

(defn close-dir [dir]
  (om/transact! dir (fn [d] (assoc d :open? false))))

(defn open-dir [dir]
  (go (let [response (<! (http/get (str api "/browse")
                                   {:with-credentials? false
                                    :query-params {"path" (:path @dir)}}))
            contents (map (fn [datum]
                        (if (= (:type datum) :directory)
                          (assoc datum :open? false)
                          datum))
                      (:body response))]
      (om/transact! dir (fn [d]
                          (assoc d :open? true
                                   :contents (vec contents)))))))

(defn show-preview [file]
  (go (let [response (<! (http/get (str api "/txt-preview")
                                   {:with-credentials? false
                                    :query-params {"path" (:path @file)}}))
            text (:body response)]
        (om/update! root [:txt-preview] text))))

(def app-state (atom {:root "/"
                      :txt-preview ""
                      :dir {:path "/"
                            :name ""
                            :type :directory
                            :open? false
                            :contents []}}))

(def root (om/root-cursor app-state))

(open-dir (:dir root))

(defn path->dir [path]
  {:path path
   :name (-> path (clojure.string/split #"/") last)
   :type :directory
   :open? false
   :contents []})

(defn set-new-root [path]
  (om/update! root [:root] path)
  (om/update! root [:dir] (path->dir path))
  (open-dir (:dir root)))

(defn handle-root-change [e owner]
  (om/set-state! owner :value (.. e -target -value)))

(defn root-edit-view [root owner]
  (reify
    om/IInitState
    (init-state [_]
      {:value root})
    om/IRenderState
    (render-state [_ state]
      (dom/div nil
        (dom/input #js {:type "text" :value (:value state) :onChange #(handle-root-change % owner)})
        (dom/button #js {:onClick #(set-new-root (:value state))}
                    "set")))))
 
(defn open-dir-view [dir owner]
  (om/component
    (dom/li nil
            (dom/i #js {:className "far fa-folder-open"})
            (dom/a #js {:className "node" :onClick #(close-dir dir)}
                   (dir :name))
            (dom/ul nil (om/build-all node-view (dir :contents) {:key :path})))))

(defn closed-dir-view [dir owner]
  (om/component
    (dom/li nil
            (dom/i #js {:className "far fa-folder"})
            (dom/a #js {:className "node" :onClick #(open-dir dir)}
                   (dir :name)))))

(defn txt-view [file owner]
  (om/component
    (dom/li #js {:onClick #(show-preview file)}
            (dom/i #js {:className "fas fa-file-alt"})
            (dom/a #js {:className "node"}
                   (file :name)))))

(defn bin-view [file owner]
  (om/component
    (dom/li nil
            (dom/i #js {:className "far fa-file"})
            (dom/a #js {:className "node"}
                   (file :name)))))

(defmulti dir-view (fn [dir _] (:open? dir)))
(defmethod dir-view true
  [dir owner] (open-dir-view dir owner))
(defmethod dir-view false
  [dir owner] (closed-dir-view dir owner))

(defmulti file-view (fn [file _] (:format file)))
(defmethod file-view "txt"
  [file owner] (txt-view file owner))
(defmethod file-view :default
  [file owner] (bin-view file owner))

(defmulti node-view (fn [node _] (:type node)))
(defmethod node-view :directory
  [dir owner] (dir-view dir owner))
(defmethod node-view :file
  [file owner] (file-view file owner))


(om/root
  (fn [data owner]
    (reify om/IRender
      (render [_]
        (dom/div nil
                 (om/build root-edit-view (data :root))
                 (dom/div #js {:className "tree"}
                          (dom/ul nil
                                 (om/build node-view (data :dir))))
                 (dom/div #js {:className "preview"}
                          (data :txt-preview))))))
  app-state
  {:target (. js/document (getElementById "app"))})

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
