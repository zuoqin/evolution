(ns shelters.devs 
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use [net.unit8.tower :only [t]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [secretary.core :as sec :include-macros true]
            [shelters.core :as shelters]
            [ajax.core :refer [GET POST]]
            [cljs-time.core :as tc]
            [cljs-time.format :as tf] 
            [om-bootstrap.button :as b]
            [clojure.string :as str]
            [shelters.settings :as settings]
  )
  (:import goog.History)
)

(enable-console-print!)

(defonce app-state (atom  {:users [] }))

(defn printDevices []
  (.print js/window)
)

(def jquery (js* "$"))


(defn OnSendCommand [response]
   (.log js/console (str response)) 

)

(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text))
)


(defn handleChange [e]
  (let [
    ;tr1 (.log js/console (str (.. e -nativeEvent -target -id)))
    ]
  )
  (swap! shelters/app-state assoc-in [(keyword (.. e -nativeEvent -target -id))] (.. e -nativeEvent -target -value))
)




(defn deletetable [id] 
  (go
    (let [
        ;{:keys [ws-channel error]} (<! (ws-ch (str settings/socketpath) {:format :json}))
        ;{:keys [message error]} (<! (:ws_channel @shelters/app-state))
      ]
      (>! (:ws_channel @shelters/app-state) {:$type "remove_table" :id id})
    )
  )
)


(defn comp-tbls
  [tbl1 tbl2]
  (if (> (compare (:name tbl1) (:name tbl2)) 0)
      false
      true
  )
)

(defcomponent showvacancies [table]
  (render [_]
    (let []
      (dom/div {:style {:justify-content "space-evenly" :text-align "justify" :display "flex" :flex-wrap "wrap" :width "100%" :margin-top "0px"}}
        (map (fn [x]
          (dom/div
           (if (< x (:participants table)) 
             (dom/i {:className "fa fa-users" :style {:padding-left "5px"}})
             (dom/i {:className "fa fa-user" :style {:padding-left "6px" :padding-right "6px"}})
           )
           
          )
        ) (range 0 12))
      )
    )
  )
)

(defcomponent showdevices-view [data owner]
  (render
    [_]

    (dom/div {:style {:display "inline-block" :width (str (+ 50 (*  190 (count (:tables @shelters/app-state)))) "px")}}
         (map (fn [item]
           (let [

             ]
             (dom/div { :className "panel panel-primary device" :style {:display "inline-block" :white-space "nowrap" :border "1px solid #ddd" :margin-left "20px" :margin-top "20px" :max-width "170px" :margin-bottom "0px" :backgroundColor "white"}}
               (dom/div {:className "panel-heading" :style {:padding-top "3px" :padding-bottom "3px"}}
                 (dom/div {:className "row" :style {:max-width "140px" :text-align "center" :margin-left "0px" :margin-right "0px"}}
                   (dom/div {:style {:white-space "normal"}} (str (:name item)))
                 )

                 (om/build showvacancies item {})
                 ;; (dom/div {:className "row" :style {:max-width "290px" :text-align "center" :margin-left "0px" :margin-right "0px"}}
                 ;;   (dom/div {:style {:white-space "normal"}} (str "participants: " (:participants item)))
                 ;; )
                 (if (= (:role @shelters/app-state) "admin")
                   (dom/div {:className "row" :style {:max-width "290px" :text-align "center" :margin-left "0px" :margin-right "0px"}}
                     (b/button {:className "btn btn-block btn-primary" :style {:margin-top "0px" :font-size "12px"} :onClick (fn [e] (deletetable (:id item)))} "Delete")
                   )
                 )
               )
             )
           )
         ) (sort (comp comp-tbls) (filter (fn [x] (if (or (str/includes? (str/lower-case (if (nil? (:name x)) "" (:name x))) (str/lower-case (:search @data)))) true false)) (:tables @data)))
      )
    )
  )
)


(defn addModal []
  (dom/div
    (dom/div {:id "loginModal" :className "modal fade" :role "dialog"}
      (dom/div {:className "modal-dialog"} 
        ;;Modal content
        (dom/div {:className "modal-content"} 
          (dom/div {:className "modal-header"} 
                   (b/button {:type "button" :className "close" :data-dismiss "modal"})
                   (dom/h4 {:className "modal-title"} (:modalTitle @shelters/app-state) )
                   )
          (dom/div {:className "modal-body"}
                   (dom/p (:modalText @shelters/app-state))
                   )
          (dom/div {:className "modal-footer"}
                   (b/button {:type "button" :className "btn btn-default" :data-dismiss "modal"} "Close")
          )
        )
      )
    )
  )
)


(defn onMount [data]
  ; (getUsers data)
  (swap! shelters/app-state assoc-in [:current] 
    "Dashboard"
  )
  (set! (.-title js/document) "Dashboard")
  (swap! shelters/app-state assoc-in [:view] 8)
)



(defcomponent dashboard-view [data owner]
  (will-mount [_]
    (onMount data)
  )
  (render [_]
    (let [style {:style {:margin "10px" :padding-bottom "0px"}}
      styleprimary {:style {:margin-top "70px"}}
      ]
      (dom/div
        (dom/div {:className "container" :style {:margin-top "0px" :width "100%"}}


          (dom/div {:className "row" :style {:margin-right "15px"}}
            (dom/input {:id "search" :type "text" :placeholder "search" :style {:height "24px" :margin-top "12px"} :value  (:search @shelters/app-state) :onChange (fn [e] (handleChange e )) })
          )
          (om/build showdevices-view  data {})
          (addModal)
        )
        (dom/div {:className "panel panel-primary" :style {:margin-top "30px"}} ;;:onClick (fn [e](println e))
        
          ; (dom/div {:className "panel-heading"}
          ;   (dom/div {:className "row"}
          ;     ; (dom/div {:className "col-md-10"}
          ;     ;   (dom/span {:style {:padding-left "5px"}} "我的消息")
          ;     ; )
          ;     ; (dom/div {:className "col-md-2"}
          ;     ;   (dom/span {:className "badge" :style {:float "right" }} (str (:msgcount data))  )
          ;     ; )
          ;   )
          ; )          
        )
      ) 
    )
  )
)




(sec/defroute dashboard-page "/dashboard" []
  (om/root dashboard-view
           shelters/app-state
           {:target (. js/document (getElementById "app"))}))


