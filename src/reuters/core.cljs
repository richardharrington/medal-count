(ns reuters.core
  (:require
   [cljsjs.react]
   [cljs.reader :as reader]
   [sablono.core :as sab :include-macros true]))

(enable-console-print!)

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:sort-criterion "gold"
                          :element-id "app"}))


(defn element [type props & children]
  (js/React.createElement type (clj->js props) children))

(defn component [name render]
  (js/React.createClass
    #js {:displayName name
         :render (fn []
                   (this-as el
                     (render (js->clj (.-props el) :keywordize-keys true))))}))




(def downloaded-json
  [{
    :code "USA",
    :gold 9,
    :silver 7,
    :bronze 12
    }
   {
    :code "NOR",
    :gold 11,
    :silver 5,
    :bronze 10
    }
   {
    :code "RUS",
    :gold 13,
    :silver 11,
    :bronze 9
    }
   {
    :code "NED",
    :gold 8,
    :silver 7,
    :bronze 9
    }
   {
    :code "FRA",
    :gold 4,
    :silver 4,
    :bronze 7
    }
   {
    :code "SWE",
    :gold 2,
    :silver 7,
    :bronze 6
    }
   {
    :code "ITA",
    :gold 0,
    :silver 2,
    :bronze 6
    }
   {
    :code "CAN",
    :gold 10,
    :silver 10,
    :bronze 5
    }
   {
    :code "SUI",
    :gold 6,
    :silver 3,
    :bronze 2
    }
   {
    :code "BLR",
    :gold 5,
    :silver 0,
    :bronze 1
    }
   {
    :code "GER",
    :gold 8,
    :silver 6,
    :bronze 5
    }
   {
    :code "AUT",
    :gold 4,
    :silver 8,
    :bronze 5
    }
   {
    :code "CHN",
    :gold 3,
    :silver 4,
    :bronze 2
    }
   ])

(def add-totals
  (partial map (fn [{:keys [gold silver bronze] :as country-awards}]
                 (assoc country-awards :total (+ gold silver bronze)))))

(def with-totals (add-totals downloaded-json))

;; (def dummy-data with-totals)

(defn sort-by-with-fallback [primary-key fallback-key]
  (partial sort (comparator (fn [a b]
                              (if (not= (primary-key a) (primary-key b))
                                (> (primary-key a) (primary-key b))
                                (> (fallback-key a) (fallback-key b)))))))

(def sort-by-gold (sort-by-with-fallback :gold :silver))
(def sort-by-silver (sort-by-with-fallback :silver :gold))
(def sort-by-bronze (sort-by-with-fallback :bronze :gold))
(def sort-by-total (sort-by-with-fallback :total :gold))

(def dummy-data (sort-by-bronze with-totals))

(defn make-flag-pos-map [award-data flag-height]
  (let [codes (->> award-data (map :code) sort)
        positions (map (partial * -1 flag-height) (range (count codes)))]
    (prn codes)
    (zipmap codes positions)))

(def flag-pos-map
  (make-flag-pos-map dummy-data 17))

(prn flag-pos-map)

(def Row
  (component
    "Row"
    (fn [{:keys [idx code gold silver bronze total]}]
      (sab/html
        [:tr.row
         [:td.order (str (inc idx))]
         [:td.flag
          [:div {:style {"background-position"
                         (str "0 " (get flag-pos-map code) "px")}}]
          ""]
         [:td.code code]
         [:td.gold gold]
         [:td.silver silver]
         [:td.bronze bronze]
         [:td.total total]]))))

(def MainTable
  (component
    "MainTable"
    (fn [{:keys [rows]}]
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
  (let [node (.getElementById js/document "app")]
    (js/ReactDOM.render (element MainTable {:rows (take 10 dummy-data)}) node)))

(render)

(add-watch app-state :rerender (fn [_ _ _ _] (render)))



;; not currently used

(defn on-js-reload []
  #_(reset! app-state (initial-state)))
