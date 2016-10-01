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


(def add-totals
  (partial map (fn [{:keys [gold silver bronze] :as country-awards}]
                 (assoc country-awards :total (+ gold silver bronze)))))


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


(def make-code->css-flag-pos
  (memoize
    (fn [award-data flag-height]
      (let [codes (->> award-data (map :code) sort)
            positions (map (partial * -1 flag-height) (range (count codes)))
            code->pos-map (zipmap codes positions)]
        ;; cannot just return the map itself because the codes will
        ;; end up being keywordized by the 'component' function
        (partial get code->pos-map)))))


(def Row
  (component
    "Row"
    (fn [{:keys [order code css-flag-pos gold silver bronze total]}]
      (sab/html
        [:tr.row
         [:td.order order]
         [:td.flag
          [:div {:style {"background-position" (str "0 " css-flag-pos "px")}}]]
         [:td.code code]
         [:td.gold gold]
         [:td.silver silver]
         [:td.bronze bronze]
         [:td.total total]]))))


(def MainTable
  (component
    "MainTable"
    (fn [{:keys [rows sort-criterion code->css-flag-pos]}]
      (sab/html
        [:table.main-table
         [:thead
          [:tr
           [:th]
           [:th]
           [:th]
           [:th.gold "gold"]
           [:th.silver "silver"]
           [:th.bronze "bronze"]
           [:th.total "total"]]]
         [:tbody
          (map-indexed (fn [idx {:keys [code] :as row}]
                         (element Row (merge row {:key idx
                                                  :order (inc idx)
                                                  :css-flag-pos (code->css-flag-pos code)})))
                       rows)]]))))

;; render

(defn render []
  (let [{:keys [award-data element-id sort-criterion]} @app-state
        container-node (.getElementById js/document element-id)]
    (js/ReactDOM.render
      (element MainTable {:rows (->> award-data
                                     (sort-by-criterion sort-criterion)
                                     (take 10))
                          :sort-criterion sort-criterion
                          :code->css-flag-pos (make-code->css-flag-pos
                                                award-data
                                                flag-height)})
      container-node)))


(defn handler [response]
  (swap! app-state assoc :award-data (add-totals response)))


(defn error-handler [{:keys [status status-text] :as error-response}]
  (js/alert (str "Something bad happened: " status " " status-text)))


(ajax/GET "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json"
          {:handler handler
           :error-handler error-handler
           :response-format (ajax/json-response-format {:keywords? true})})


(add-watch app-state :rerender (fn [_ _ _ _] (render)))
