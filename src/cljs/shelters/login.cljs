(ns shelters.login
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [secretary.core :as sec :refer-macros [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [shelters.core :as shelters]
            [shelters.settings :as settings]

            [shelters.devs :as devs]


            [cljs-time.format :as tf]
            [cljs-time.core :as tc]
            [cljs-time.coerce :as te]
            [cljs-time.local :as tl]

            [ajax.core :refer [GET POST]]
            [om-bootstrap.input :as i]
            [om-bootstrap.button :as b]
            [om-bootstrap.panel :as p]
	    [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [put! dropping-buffer chan take! <! >! timeout close!]]
  )
  (:import goog.History)
)

(enable-console-print!)

(def iconBase "/images/")
(def application
  (js/document.getElementById "app"))

(defn set-html! [el content]
  (aset el "innerHTML" content))


(sec/set-config! :prefix "#")

(let [history (History.)
      navigation EventType/NAVIGATE]
  (goog.events/listen history
                     navigation
                     #(-> % .-token sec/dispatch!))
  (doto history (.setEnabled true)))


(def ch (chan (dropping-buffer 2)))
(def jquery (js* "$"))
(defonce app-state (atom  {:error "" :modalText "Modal Text" :modalTitle "Modal Title" :state 0} ))


(defn setLoginError [error]
  (swap! app-state assoc-in [:modalTitle] 
    (str "Login Error")
  ) 

  (swap! app-state assoc-in [:modalText] 
    (str (:error error))
  ) 

  (swap! app-state assoc-in [:state] 0)
 
  ;;(.log js/console (str  "In setLoginError" (:error error) ))
  (jquery
    (fn []
      (-> (jquery "#loginModal")
        (.modal)
      )
    )
  )
)



(defn error-handler [{:keys [status status-text]}]
  (.log js/console (str "something bad happened: " status " " status-text))
)



(defn restartsocket []
  (if (not (nil? (:ws_channel @shelters/app-state))) (close! (:ws_channel @shelters/app-state)))
  (put! ch 45)
)


(defn receiveupdates []
  (go
    (let [
        {:keys [message error]} (<! (:ws_channel @shelters/app-state))
      ]
      (if error
        (js/console.log "Uh oh:" error)
        (let [
          tr1 (.log js/console (str "received update:" message))
          ]
          (if (not (nil? message))
            (let [
              type (get message "$type")
            ]
            
            (case type 
              "table_removed"
              (let [id (get message "id")
                    tables (:tables @shelters/app-state)
                    newtables (remove (fn [table] (if (= (:id table) id) true false)) tables)
                ]
                (.log js/console (str "removed table from server:" id))
                (swap! shelters/app-state assoc-in [:tables] newtables)
              )
              "table_added"
              (let [
                    table {:id (get (get message "table") "id") :name (get (get message "table") "name") :participants (get (get message "table") "participants")} 
                    tables (:tables @shelters/app-state)
                    newtables (conj tables table) 
                ]
                (.log js/console (str "table added from server: id=" (:id table) "; name=" (:name table) "; participants=" (:participants table)))
                (swap! shelters/app-state assoc-in [:tables] newtables)
              )
              "table_updated"
              (let [
                   table (map (fn [x] {:id (get x "id") :name (get x "name") :participants (get x "participants")}) (get message "table"))
                    tables (:tables @shelters/app-state)
                    deltable (remove (fn [x] (if (= (:id table) (:id x)) true false  )) tables)
                    newtables (into [] (conj deltable table) )
                ]
                (.log js/console (str "table updated from server: id=" (:id table) "; name=" (:name table)))
                (swap! shelters/app-state assoc-in [:tables] newtables)
              )
              (.log js/console "Unknown message from server")
            )

            )
            (setLoginError "Error update tables")
          )
        )
      )

      (receiveupdates)
    )
  )
)

(defn processtables [message]
  (let [
    type (get message "$type")
    tables (map (fn [table]
       {:id (get table "id") :name (get table "name") :participants (get table "participants")}
     ) (get message "tables"))
    ]
    (swap! shelters/app-state assoc-in [:tables] tables)
    (-> js/document .-location (set! "#/dashboard"))
    (receiveupdates)
  )
)

(defn receivetables []
  (go
    (let [
        {:keys [message error]} (<! (:ws_channel @shelters/app-state))
      ]
      (if error
        (js/console.log "Uh oh:" error)
        (let [
          tr1 (.log js/console (str "received tables"))
          ]
          (if (not (nil? message))
            (processtables message)
            (setLoginError "Error tables")
          )
        )
      )
    )
  )
)

(defn receivesocketmsg []
  (go
    (let [
        ;{:keys [ws-channel error]} (<! (ws-ch (str settings/socketpath) {:format :json}))
        {:keys [message error]} (<! (:ws_channel @shelters/app-state))
      ]
      (if error
        (js/console.log "Uh oh:" error)
        (let []
          (if (not (nil? message))
            (let [type (get message "$type")]
              (if (= type "login_successful")
                (let [
                  role (get message "user_type")
                  
                  ]
                  (swap! shelters/app-state assoc-in [:role] role)
                  (.log js/console (str "Login successfull, role: " role))
                  (>! (:ws_channel @shelters/app-state) {:$type "subscribe_tables"})
                  (receivetables)
                )
                
                (setLoginError "Error login")
              )
            )
            
          )
        )
      )
    )
  )
  ;; (try

  ;;   (catch js/Error e
  ;;     (.log js/console e)
  ;;   )
  ;; )
)


(defn initsocket []
  ;(if (not (nil? (:ws_channel @shelters/app-state))) (close! (:ws_channel @shelters/app-state)))
  (shelters/closesocket)
  (go
    (if (nil? (:ws_channel @shelters/app-state))
      (let [
          ;tr1 (.log js/console (:ws_channel @shelters/app-state))
          ;tr1 (if (not (nil? (:ws_channel @shelters/app-state))) (close! (:ws_channel @shelters/app-state)))
          {:keys [ws-channel error]} (<! (ws-ch (str settings/socketpath) {:format :json}))
          ;{:keys [message error]} (<! ws-channel)        
        ]
        ;(swap! app-state assoc-in [:ws_ch] ws-channel)
        ;(.log js/console (str "token to send in socket: " (:token (:token @shelters/app-state))))
        (if-not error
          (>! ws-channel {:$type "login" :username "user1234" :password "password1234"})
          (js/console.log "Error:" (pr-str error))
        )
        (swap! shelters/app-state assoc-in [:ws_channel] 
          ws-channel
        )
      )
    )
    (receivesocketmsg)
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
                   (dom/h4 {:className "modal-title"} (:modalTitle @app-state) )
                   )
          (dom/div {:className "modal-body"}
                   (dom/p (:modalText @app-state))
                   )
          (dom/div {:className "modal-footer"}
                   (b/button {:type "button" :className "btn btn-default" :data-dismiss "modal"} "Close")
          )
        )
      )
    )
  )
)

(defn onMount [data owner]
  (.focus (om/get-node owner "txtUserName" ))
  (set! (.-title js/document) "Beeper Login")
)


(defcomponent login-page-view [data owner]
  (did-update [this prev-props prev-state]
    ;(.log js/console "starting login screen" ) 
    
  )
  (did-mount [_]
    ;(.log js/console "gg")
    (onMount data owner)
  )
  (render
    [_]
    (dom/div {:className "container" :style {:width "100%" :padding-top "10px" :margin-top "100px" :backgroundImage "url(images/LogonBack.jpg)" :backgroundSize "224px 105px" :backgroundRepeat "no-repeat" :backgroundPosition "top center"}  }
      ;(om/build t5pcore/website-view data {})
      ;(dom/h1 "Login Page")
      ;(dom/img {:src "images/LogonBack.jpg" :className "img-responsive company-logo-logon"})
      (dom/form {:className "form-signin" :style {:padding-top "150px"}}
        (dom/input #js {:type "text" :ref "txtUserName"
           :defaultValue  settings/demouser  :className "form-control" :placeholder "User Name" } )
        (dom/input {:className "form-control" :ref "txtPassword" :id "txtPassword" :defaultValue settings/demopassword :type "password"  :placeholder "Password"} )
        (dom/button {:className (if (= (:state @app-state) 0) "btn btn-lg btn-primary btn-block" "btn btn-lg btn-primary btn-block m-progress" ) :onClick (fn [e](initsocket)) :type "button"} "Login")
      )
      (addModal)
      (dom/div {:style {:margin-bottom "700px"}})
    )
  )
)


(defn gotomap [counter]
  (if (or (> counter 1) (and (> (count (:devices @shelters/app-state)) 0) (> (count (:groups @shelters/app-state)) 0)))

    (let []
      (-> js/document .-location (set! "#/map"))
      ;(aset js/window "location" "#/map")
      (swap! app-state assoc-in [:state] 0)
    )
    
    (go
      (<! (timeout 500))
      (.log js/console (str "count=" (count (:units @shelters/app-state))))
      (swap! app-state assoc-in [:state] 0)
      (gotomap (+ counter 1))
    )
  )
)

(defn setcontrols [value]
  (case value
    42 (gotomap 0)
    45 (initsocket)
  )
)

(defn initqueue []
  (doseq [n (range 1000)]
    (go ;(while true)
      (take! ch(
        fn [v] (
           setcontrols v
          )
        )
      )
    )
  )
)

(initqueue)



(defmulti website-view
  (
    fn [data _]
      (:view (if (= data nil) @shelters/app-state @data ))
  )
)

(defmethod website-view 0
  [data owner] 
  (login-page-view data owner)
)

(defmethod website-view 1
  [data owner] 
  (login-page-view data owner)
)

(sec/defroute login-page "/login" []
  (om/root login-page-view 
           app-state
           {:target (. js/document (getElementById "app"))}
  )
)


(defn main []
  (-> js/document
      .-location
      (set! "#/login"))

  ;;(aset js/window "location" "#/login")
)

(main)

