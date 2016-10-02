(ns reuters.core
  (:require
   [cljsjs.react]
   [ajax.core :as ajax]
   [sablono.core :as sab :include-macros true]))

(enable-console-print!)

(defn element [type props & children]
  (js/React.createElement type (clj->js props) children))


(defn component [name render]
  (js/React.createClass
    #js {:displayName name
         :render (fn []
                   (this-as el
                     (render (js->clj (.-props el) :keywordize-keys true))))}))


;; define your app data so that it doesn't get over-written on reload

(def flag-height 17)
(def award-data-endpoint "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json")

(defonce app-state (atom {:award-data nil ;; will be downloaded
                          :sort-criterion nil})) ;; will be passed in as an argument

(def update-award-data!
  (partial swap! app-state assoc :award-data))

(def update-sort-criterion!
  (partial swap! app-state assoc :sort-criterion))

(def add-alpha-indexes
  (partial map-indexed (fn [idx country-awards]
                         (assoc country-awards :alpha-index idx))))

(def add-totals
  (partial map (fn [{:keys [gold silver bronze] :as country-awards}]
                 (assoc country-awards :total (+ gold silver bronze)))))

(defn augment-award-data [raw-award-data]
  (->> raw-award-data
       (sort-by :code)
       (add-alpha-indexes)
       (add-totals)))

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
    (fn [{:keys [header-text selected? update!]}]
      (let [class-name (if selected? "selected" "")]
        (sab/html
          [:th {:class-name class-name :on-click update!}
           header-text])))))


(def MainTable
  (component
    "MainTable"
    (fn [{:keys [rows sort-criterion update-sort-criterion!]}]
      (let [header-cell
            (fn [header-text]
              (element HeaderCell
                       {:header-text header-text
                        :selected? (= header-text sort-criterion)
                        :update! #(update-sort-criterion! header-text)}))]
        (sab/html
          [:table.main-table
           [:thead
            [:tr
             [:th]
             [:th]
             [:th]
             (header-cell "gold")
             (header-cell "silver")
             (header-cell "bronze")
             (header-cell "total")]]
           [:tbody
            (map-indexed (fn [idx {:keys [alpha-index] :as row}]
                           (element Row (merge
                                          row
                                          {:key idx
                                           :display-order (inc idx)
                                           :css-flag-pos (* alpha-index flag-height -1)})))
                         rows)]])))))

;; render

(defn render [element-id]
  (let [{:keys [award-data sort-criterion]} @app-state
        container-node (.getElementById js/document element-id)]
    (js/ReactDOM.render
      (element MainTable {:rows (->> award-data
                                     (sort-by-criterion sort-criterion)
                                     (take 10))
                          :sort-criterion sort-criterion
                          :update-sort-criterion! update-sort-criterion!})
      container-node)))


(defn handler [response]
  (update-award-data! (augment-award-data response)))


(defn error-handler [{:keys [status status-text] :as error-response}]
  (js/alert (str "Something bad happened: " status " " status-text)))

(defn place-widget!
  ([element-id]
   (place-widget! element-id "gold"))
  ([element-id sort-criterion]
   (update-sort-criterion! sort-criterion)
   (ajax/GET award-data-endpoint
             {:handler handler
              :error-handler error-handler
              :response-format (ajax/json-response-format {:keywords? true})})
   (add-watch app-state :rerender (fn [_ _ _ _] (render element-id)))))

(place-widget! "app" "silver")


;; TODO:
;; 1. Change award to medal, in naming,
;; 2. Make everything private except place-widget!,
;; 3. Make it look like the screenshot.
;; 4. Write comments
;; 5. Write a readme
;; 6. Write tests. spec? Some integration tests?
;; 7. Compile and deploy to github
