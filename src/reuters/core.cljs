(ns reuters.core
  (:require
   [cljsjs.react]
   [ajax.core :as ajax]
   [sablono.core :as sab :include-macros true]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(def flag-height 17)

(defonce app-state (atom {:award-data nil ;; will be downloaded
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


(def add-total
  (partial map (fn [{:keys [gold silver bronze] :as country-awards}]
                 (assoc country-awards :total (+ gold silver bronze)))))

(def add-alpha-index
  (partial map-indexed (fn [idx country-awards]
                         (assoc country-awards :alpha-index idx))))


(defn sort-by-with-fallback [primary-key fallback-key items]
  (sort (comparator (fn [a b]
                      (if (not= (primary-key a) (primary-key b))
                        (> (primary-key a) (primary-key b))
                        (> (fallback-key a) (fallback-key b)))))
        items))


(defn sort-by-criterion [criterion award-data]
  (case criterion
    "gold" (sort-by-with-fallback :gold :silver award-data)
    "silver" (sort-by-with-fallback :silver :gold award-data)
    "bronze" (sort-by-with-fallback :bronze :gold award-data)
    "total" (sort-by-with-fallback :total :gold award-data)))


(def Row
  (component
    "Row"
    (fn [{:keys [display-order code css-flag-pos gold silver bronze total]}]
      (sab/html
        [:tr.row
         [:td.display-order display-order]
         [:td.flag
          [:div {:style {"background-position" (str "0 " css-flag-pos "px")}}]]
         [:td.code code]
         [:td.gold gold]
         [:td.silver silver]
         [:td.bronze bronze]
         [:td.total total]]))))

(def HeaderCell
  (component
    "HeaderCell"
    (fn [{:keys [header-text sort-criterion]}]
      (let [class-name (if (= header-text sort-criterion)
                         "selected"
                         "")]
        (sab/html
          [:th {:class-name class-name} header-text])))))


(def MainTable
  (component
    "MainTable"
    (fn [{:keys [rows sort-criterion]}]
      (sab/html
        [:table.main-table
         [:thead
          [:tr
           [:th]
           [:th]
           [:th]
           (element HeaderCell {:header-text "gold" :sort-criterion sort-criterion})
           (element HeaderCell {:header-text "silver" :sort-criterion sort-criterion})
           (element HeaderCell {:header-text "bronze" :sort-criterion sort-criterion})
           (element HeaderCell {:header-text "total" :sort-criterion sort-criterion})]]
         [:tbody
          (map-indexed (fn [idx {:keys [alpha-index] :as row}]
                         (element Row (merge
                                        row
                                        {:key idx
                                         :display-order (inc idx)
                                         :css-flag-pos (* alpha-index flag-height -1)})))
                       rows)]]))))

;; render

(defn render []
  (let [{:keys [award-data element-id sort-criterion]} @app-state
        container-node (.getElementById js/document element-id)]
    (js/ReactDOM.render
      (element MainTable {:rows (->> award-data
                                     (sort-by-criterion sort-criterion)
                                     (take 10))
                          :sort-criterion sort-criterion})
      container-node)))


(defn handler [response]
  (->> response
       (sort-by :code)
       (add-alpha-index)
       (add-total)
       (swap! app-state assoc :award-data)))


(defn error-handler [{:keys [status status-text] :as error-response}]
  (js/alert (str "Something bad happened: " status " " status-text)))


(ajax/GET "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json"
          {:handler handler
           :error-handler error-handler
           :response-format (ajax/json-response-format {:keywords? true})})


(add-watch app-state :rerender (fn [_ _ _ _] (render)))
