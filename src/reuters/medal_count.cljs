(ns reuters.medal-count
  (:require
   [ajax.core :as ajax]
   [sablono.core :as sab :include-macros true]
   [reuters.react-wrapper :as r]))

;; You can't really make things private in ClojureScript
;; but the annotations are just for guidance, so the
;; person reading it will know what is in the API


;; Config values

(def ^:private flag-height 17)
(def ^:private award-data-endpoint "https://s3-us-west-2.amazonaws.com/reuters.medals-widget/medals.json")


;; Global app-state atom, and updaters for it

(defonce ^:private app-state (atom {:award-data nil ;; will be downloaded
                                    :sort-criterion nil})) ;; will be passed in as an argument

(def ^:private update-award-data!
  (partial swap! app-state assoc :award-data))

(def ^:private update-sort-criterion!
  (partial swap! app-state assoc :sort-criterion))


;; Functions to augment data from the server
;; with totals and indexes by alphabetical order
;; (which will be used to position the flag sprite
;; in each row.)

(def ^:private add-alpha-indexes
  (partial map-indexed (fn [idx country-awards]
                         (assoc country-awards :alpha-index idx))))

(def ^:private add-totals
  (partial map (fn [{:keys [gold silver bronze] :as country-awards}]
                 (assoc country-awards :total (+ gold silver bronze)))))

(defn- augment-award-data [raw-award-data]
  (->> raw-award-data
       (sort-by :code)
       (add-alpha-indexes)
       (add-totals)))


;; Sorting functions. Each type of sort has a fallback
;; sort criterion, to break ties.

(defn- sort-by-with-fallback [primary-key fallback-key items]
  (sort (comparator (fn [a b]
                      (if (not= (primary-key a) (primary-key b))
                        (> (primary-key a) (primary-key b))
                        (> (fallback-key a) (fallback-key b)))))
        items))

(defn- sort-by-criterion [criterion award-data]
  (case criterion
    "gold" (sort-by-with-fallback :gold :silver award-data)
    "silver" (sort-by-with-fallback :silver :gold award-data)
    "bronze" (sort-by-with-fallback :bronze :gold award-data)
    "total" (sort-by-with-fallback :total :gold award-data)))


;; React components

(def ^:private Row
  (r/component
    "Row"
    (fn [{:keys [display-order code css-flag-pos gold silver bronze total]}]
      (sab/html
        [:tr
         [:td.display-order display-order]
         [:td.flag
          [:div {:style {"background-position" (str "0 " css-flag-pos "px")}}]]
         [:td.code code]
         [:td.gold gold]
         [:td.silver silver]
         [:td.bronze bronze]
         [:td.total total]]))))

(def ^:private HeaderCell
  (r/component
    "HeaderCell"
    (fn [{:keys [medal-class selected? update!]}]
      (let [class-name (str medal-class (if selected? " selected" ""))]
        (sab/html
          [:th {:class-name class-name :on-click update!}])))))

(def ^:private MainTable
  (r/component
    "MainTable"
    (fn [{:keys [rows sort-criterion update-sort-criterion!]}]
      (let [header-cell
            (fn [this-criterion]
              (r/element HeaderCell
                         {:medal-class this-criterion
                          :selected? (= this-criterion sort-criterion)
                          :update! #(update-sort-criterion! this-criterion)}))]
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
                           (r/element Row (merge
                                            row
                                            {:key idx
                                             :display-order (inc idx)
                                             :css-flag-pos (* alpha-index flag-height -1)})))
                         rows)]])))))

(def ^:private AppContainer
  (r/component
    "AppContainer"
    (fn [props]
      (sab/html
        [:div.app-container
         [:div.label "MEDAL COUNT"]
         (r/element MainTable props)]))))


;; Main render function

(defn- render [element-id]
  (let [{:keys [award-data sort-criterion]} @app-state
        container-node (.getElementById js/document element-id)]
    (r/render
      (r/element AppContainer {:rows (->> award-data
                                          (sort-by-criterion sort-criterion)
                                          (take 10))
                               :sort-criterion sort-criterion
                               :update-sort-criterion! update-sort-criterion!})
      container-node)))


;; Handlers for the Ajax call

(defn- handler [response]
  (update-award-data! (augment-award-data response)))

(defn- error-handler [{:keys [status status-text] :as error-response}]
  (js/alert (str "Something bad happened: " status " " status-text)))


;; Main function for export; takes an element id and an optional
;; initial sort criterion.

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


;; TODO:
;; 3. fix the sizes of the flags
;; 5. Write a readme
;; 6. Write tests. spec? Some integration tests?
;; 7. Compile and deploy to github
