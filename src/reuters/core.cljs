(ns reuters.core
  (:require
   [cljsjs.react]
   [ajax.core :as ajax]
   [sablono.core :as sab :include-macros true]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:award-data nil ;; will be downloaded
                          :flag-pos-map nil ;; will be calculated
                          :sort-criterion "gold"
                          :element-id "app"}))


(defn element [type props & children]
  (js/React.createElement type (clj->js props) children))


(defn component [name render]
  (js/React.createClass
    #js {:displayName name
         :render (fn []
                   (this-as el
                     (render (js->clj (.-props el) :keywordize-keys true))))}))


(def add-totals
  (partial map (fn [{:keys [gold silver bronze] :as country-awards}]
                 (assoc country-awards :total (+ gold silver bronze)))))


(defn sort-by-with-fallback [primary-key fallback-key]
  (partial sort (comparator (fn [a b]
                              (if (not= (primary-key a) (primary-key b))
                                (> (primary-key a) (primary-key b))
                                (> (fallback-key a) (fallback-key b)))))))


(def sort-by-medal-map
  {"gold" (sort-by-with-fallback :gold :silver)
   "silver" (sort-by-with-fallback :silver :gold)
   "bronze" (sort-by-with-fallback :bronze :gold)
   "total" (sort-by-with-fallback :total :gold)})


(defn make-flag-pos-map [award-data flag-height]
  (let [codes (->> award-data (map :code) sort)
        positions (map (partial * -1 flag-height) (range (count codes)))]
    (prn codes)
    (zipmap codes positions)))


;; TODO: don't access app-state directly here for :flag-pos-map

(def Row
  (component
    "Row"
    (fn [{:keys [idx code gold silver bronze total]}]
      (sab/html
        [:tr.row
         [:td.order (str (inc idx))]
         [:td.flag
          [:div {:style {"background-position"
                         (str "0 " (get (:flag-pos-map @app-state) code) "px")}}]
          ""]
         [:td.code code]
         [:td.gold gold]
         [:td.silver silver]
         [:td.bronze bronze]
         [:td.total total]]))))


(def MainTable
  (component
    "MainTable"
    (fn [{:keys [rows sort-criterion]}]
      (sab/html
        [:table.main-table
         [:thead
          [:tr
           [:th ""]
           [:th ""]
           [:th ""]
           [:th.gold "gold"]
           [:th.silver "silver"]
           [:th.bronze "bronze"]
           [:th.total "total"]]]
         [:tbody
          (map-indexed (fn [idx row]
                         (element Row (merge row {:idx idx :key idx})))
                       rows)]]))))

;; render


(defn render []
  (let [{:keys [award-data element-id sort-criterion]} @app-state
        node (.getElementById js/document element-id)
        sort-by-medal (sort-by-medal-map sort-criterion)]
    (js/ReactDOM.render (element MainTable {:rows (->> award-data
                                                       (sort-by-medal)
                                                       (take 10))
                                            :sort-criterion sort-criterion})
                        node)))


(defn handler [response]
  (let [award-data (add-totals response)]
    (swap! app-state merge {:award-data award-data
                            :flag-pos-map (make-flag-pos-map award-data 17)})))


(defn error-handler [{:keys [status status-text] :as error-response}]
  (js/alert (str "Something bad happened: " status " " status-text)))


(ajax/GET "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json"
          {:handler handler
           :error-handler error-handler
           :response-format (ajax/json-response-format {:keywords? true})})


(add-watch app-state :rerender (fn [_ _ _ _] (render)))
