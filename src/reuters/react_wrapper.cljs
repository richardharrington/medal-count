(ns reuters.react-wrapper
  (:require
   [cljsjs.react]))

(defn element [type props & children]
  (js/React.createElement type (clj->js props) children))


(defn component [name render]
  (js/React.createClass
    #js {:displayName name
         :render (fn []
                   (this-as el
                     (render (js->clj (.-props el) :keywordize-keys true))))}))

(def render js/ReactDOM.render)
