(ns shelters.server
  (use (incanter core charts excel))
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [environ.core :refer [env]]
            [clj-http.client :as client]
            [clojure.data.json :as json]

            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clj-time.core :as t]

            [clojure.java.io :as io]

            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(def custom-formatter (f/formatter "dd/MM/yyyy"))
;;(def apipath "http://10.20.35.21:3000/")
(def apipath "http://localhost:3000/")
(def xlsdir "c:/DEV/Java/")

(def bloombergportdir "c:/DEV/output/")
;(def imagepath "C:/DEV/clojure/sberpb/sberstatic/resources/public/img/tradeidea/")

;; (defn copy-file [source-path dest-path]
;;   (io/copy (io/file source-path) (io/file dest-path)))


;; (defn on-upload-image [request]
;;   (let [tr1 (println request)

;;       filepath (.getAbsolutePath (:tempfile (:file (:params request))))
;;       newfilename (:filename (:file (:params request)))
;;       ;tr1 (println (str "Path to file: " filepath " File name: " newfilename))

;;       tr1 (copy-file (.getAbsolutePath (:tempfile (:file (:params request)))) (str imagepath newfilename))

;;       tr1 (io/delete-file filepath)
;;     ]
;;     {:body { :location (str "/" newfilename)  }}
;;   )
;; )


;; (defn on-save-html [request]
;;   (let [;tr1 (println request)
;;         ;url (str apipath "api/syssetting")
;;         tr1 1 ;(tradeidea/update-tradeidea request)
;;     ]
;;      (response/found "/tradeidea/1")
;;   )
;; )


(defn sort-portfs-by-name [portf1 portf2] 
  (let [
        name1 (first portf1)
        name2 (first portf2)
        ]
    
    (if (<  (compare name1  name2) 0)
    true
    false)
  )
)

(defn comp-positions [position1 position2]
  (let [
    ;tr1 (println (str "pos1= " position1 " pos2= " position2))
    ]
    (if (or
         (> (compare (:assettype position1) (:assettype position2))  0)
         (and (= (compare (:assettype position1) (:assettype position2)) 0) (> (compare (:currency position1) (:currency position2)) 0))
         (and (= (compare (:assettype position1) (:assettype position2)) 0 ) (= (:currency position1) (:currency position2)) (> (:usdvalue position1) (:usdvalue position2)))
      )        
        true
        false
    )
  )
)

(defn comp-deals
  [deal1 deal2]
  (let [;tr1 (println (str "deal1= " deal1 " deal2= " deal2))
            ]
    (if (or
         (> (:date deal1) (:date deal2))
         (and (= (:date deal1) (:date deal2)) (> (compare (:security deal1) (:security deal2)) 0 ))
          ;(> (c/to-long (f/parse custom-formatter (:date deal1))) (c/to-long (f/parse custom-formatter (:date deal2))))
          ;(and (= (c/to-long (f/parse custom-formatter (:date deal1))) (c/to-long (f/parse custom-formatter (:date deal2)))) (> (compare (:security deal1) (:security deal2)) 0 ))
          )
          true
          false
        )
  )
)

(defn map-deal [deal]
  (let [
        ;tr1 (println (str deal))
        trans (loop [result [] trans (get deal "transactions") ]
                (if (seq trans) 
                  (let [
                        tran (first trans)
                        ;tr1 (.log js/console (str "tran: " tran ))
                        ]
                    (recur (conj result {:security (get deal "security") :date (get tran "date" ) :direction (get tran "direction") :nominal (get tran "nominal") :wap (get tran "wap") :wapusd (get tran "wapusd") :waprub (get tran "waprub")})
                         (rest trans))
                  )
                  result)
        )        
        result trans
        
    ]
    ;

    result
  )
)


(defn create-client-report [client]
  (let [

    url (str apipath "api/security")

    ;tr1 (println (str "Trying to get securities"))
    securities (json/read-str (:body (client/get url {:headers {"authorization" (str "Bearer " (env :token))}})))


    ;tr1 (println (str "security = " (first (filter (fn [z] (if (= "RUB" (get z "acode")) true false)) securities) )  " acode=" (get (first securities) "acode")) ) 
    url (str apipath "api/position?client=" client )
    positions (filter (fn [x] (if (not= (get (second x) "amount") 0.0) true false)) (json/read-str (:body (client/get url {:headers {"authorization" (str "Bearer " (env :token))}}))))


    url (str apipath "api/client")
    theclient (first (filter (fn [x] (if (= (get x "code") client) true false)) (json/read-str (:body (client/get url {:headers {"authorization" (str "Bearer " (env :token))}})))))

    ;url (str apipath "api/deals?client=" client )
    ;deals (flatten (map (fn [x] (map-deal x)) (filter (fn [x] (if (= 1 1) true false)) (json/read-str (:body (client/get url {:headers {"authorization" "Bearer TheBearer"}}))))))
    ;tr1 (println (str "deals= " (first deals)))
    usdrate (get (first (filter (fn [sec] (if (= (get sec "acode") "USD") true false)) securities)) "price")

    eurrate (get (first (filter (fn [sec] (if (= (get sec "acode") "EUR") true false)) securities)) "price")

    gbprate (get (first (filter (fn [sec] (if (= (get sec "acode") "GBP") true false)) securities)) "price")


    newpositions (map (fn [x] (let [
      sec (first (filter (fn [sec] (if (= (get sec "id") (Long/parseLong (first x))) true false)) securities))
      isbond (if (and (= 5 (get sec "assettype"))
                                 ;(= "RU" (subs (:isin security) 0 2))
                                 )  true false)
      ;;isrusbond (if (and (= 5 (get sec "assettype")) (= "RU" (subs (get sec "isin") 0 2)))  true false)
      usdrate (get (first (filter (fn [z] (if (= "USD" (get z "acode")) true false)) securities) ) "price")

      ;tr1 (println (str "usdrate " usdrate))

      seccurrency (get sec "currency")
      assettype (get sec "assettype")
      multiple (get sec "multiple")
      anr (get sec "anr")
      target (get sec "target")
      yield (get sec "yield")
      dvddate (if (.contains (f/unparse custom-formatter (c/from-long (c/to-long (get sec "dvddate")))) "1900") nil (f/unparse custom-formatter (c/from-long (c/to-long (get sec "dvddate")))))
      putdate (if (.contains (f/unparse custom-formatter (c/from-long (c/to-long (get sec "putdate")))) "1900") nil (f/unparse custom-formatter (c/from-long (c/to-long (get sec "putdate")))))

      duration (get sec "duration")

      ;tr1 (println (str "seccurrency " seccurrency))
      secprice (if (nil? (get sec "price")) 0.0 (get sec "price"))
      multiple (get sec "multiple")
      ;tr1 (println (str "secprice " secprice))
      
      fxrate (get (first (filter (fn [z] (if (= (if (= seccurrency "GBX") "GBP" (if (= seccurrency "RUR") "RUB" seccurrency)) (get z "acode")) true false)) securities) ) "price")

      ;tr1 (println (str "fxrate " fxrate))
      newfxrate (if (= 0 (compare "GBX" seccurrency)) (/ fxrate 100.) fxrate)
      waprub (get (second x) "rubprice")
      wapusd (get (second x) "wapusd")


      ;tr1 (println (str "sec= " sec  "secprice= " secprice " newfxrate = " newfxrate " seccurrency=" seccurrency))
      currubprice (/ (* secprice newfxrate) 1.0)
      curusdprice (/ (* secprice newfxrate) usdrate)
      amount (get (second x) "amount")
      
      rubvalue (if (= isbond true) (/ (* currubprice amount multiple)  100.0)  (if (= assettype 15) (* multiple amount secprice (if (= seccurrency "USD") usdrate 1.0)) (* currubprice amount)))

      rubcosts (if (= isbond true) (/ (* waprub amount multiple)  100.0)  (if (= assettype 15) (* multiple amount (if (= seccurrency "USD") (* usdrate wapusd) waprub)) (* multiple waprub amount)))

      usdcosts (if (= isbond true) (/ (* wapusd amount multiple)  100.0)  (if (= assettype 15) (* multiple amount (if (= seccurrency "USD") wapusd (/  waprub usdrate))) (* multiple wapusd amount)))

      usdvalue (/ rubvalue usdrate)     

] {:security (get sec "bcode") :isin (get sec "isin") :price (get sec "price") :wap (get (second x) "price") :amount amount :usdvalue usdvalue :rubvalue rubvalue :usdcosts usdcosts :rubcosts rubcosts :assettype (case assettype 1 "Equity" 5 "Bond" 15 "Derivatives" "Other")  :currency seccurrency :anr anr :target target :duration duration :yield yield :dvddate dvddate :putdate putdate :multiple multiple})) positions)
    ;positions (sort (comp sort-portfs-by-name) portfs)

    ;; newdeals (map (fn [x] (let [sec (first (filter (fn [sec] (if (= (get sec "id") (:security x)) true false)) securities))]
    ;;   {:security (get sec "acode") :isin (get sec "isin") :date (+ (/ (c/to-long (f/parse custom-formatter (:date x))) (* 24 3600 1000)) 25569)   :direction (:direction x) :nominal (:nominal x) :wap (:wap x) :wapusd (:wapusd x) :waprub (:waprub x)}
    ;;   )) deals)

    ;tr1 (println (str "theclient = " theclient))
    positionswithcash1 (conj newpositions {:security "RUB Curncy" :isin "RUB Curncy" :price 1.0 :wap 1.0 :amount (get theclient "rub") :usdvalue (/ (get theclient "rub") usdrate) :rubvalue (get theclient "rub") :usdcosts 0.0 :rubcosts (get theclient "rub") :assettype "Cash" :currency "RUB" :anr 0.0 :target 1.0 :duration 0.0 :yield 0.0 :dvddate nil :putdate nil :multiple 1.0 })

    positionswithcash2 (conj positionswithcash1 {:security "USD Curncy" :isin "USD Curncy" :price 1.0 :wap 1.0 :amount (get theclient "usd") :usdvalue (get theclient "usd") :rubvalue (* usdrate (get theclient "usd")) :usdcosts (get theclient "usd") :rubcosts 0.0 :assettype "Cash" :currency "USD" :anr 0.0 :target 1.0 :duration 0.0 :yield 0.0 :dvddate nil :putdate nil :multiple 1.0 })

    positionswithcash3 (conj positionswithcash2 {:security "EUR Curncy" :isin "EUR Curncy" :price 1.0 :wap 1.0 :amount (get theclient "eur") :usdvalue (* eurrate (/ (get theclient "eur") usdrate))  :rubvalue (* eurrate (get theclient "eur")) :usdcosts 0.0 :rubcosts 0.0 :assettype "Cash" :currency "EUR" :anr 0.0 :target 1.0 :duration 0.0 :yield 0.0 :dvddate nil :putdate nil :multiple 1.0 })

    positionswithcash4 (conj positionswithcash3 {:security "GBP Curncy" :isin "GBP Curncy" :price 1.0 :wap 1.0 :amount (get theclient "gbp") :usdvalue (* gbprate (/ (get theclient "gbp") usdrate)) :rubvalue (* gbprate (get theclient "gbp")) :usdcosts 0.0 :rubcosts 0.0 :assettype "Cash" :currency "GBP" :anr 0.0 :target 1.0 :duration 0.0 :yield 0.0 :dvddate nil :putdate nil :multiple 1.0 })

    newpositions positionswithcash4
    ]
    (save-xls ["positions" (dataset [:security :isin :price :wap :amount :usdvalue :rubvalue :usdcosts :rubcosts :assettype :currency :anr :target :duration :yield :dvddate :putdate :multiple] (sort (comp comp-positions) newpositions)) 
               ;;"transactions" (dataset [:security :isin :direction :nominal :wap :wapusd :waprub :date] (sort (comp comp-deals) newdeals))
    ] (str xlsdir client ".xlsx"))
    "Success"
  )
)

(defn create-excel-report [sec]
  (let [
    url (str apipath "api/portfolios?security=" sec )
    portfs (json/read-str (:body (client/get url {:headers {"authorization" (str "Bearer " (env :token))}})))

    positions (sort (comp sort-portfs-by-name) portfs)
    newpositions (into [] (map (fn [x] [(first x)   (get (second x) "amount") (get (second x) "price") (get (second x) "rubprice") (get (second x) "wapusd")]) positions))
    ]
    (save-xls ["sheet1" (dataset [:portf :amount :wapprice :rubprice :usdprice] newpositions)] (str xlsdir sec ".xlsx"))
    "Success"
    ;(first newpositions)
  )
)


(defroutes routes
  (GET "/" _
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (GET "/tradeidea/:token" [token]
    (let [
          file 1.0
    ]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/tradeidea.html"))}
    )
  )
  (GET "/secexcel/:sec" [sec]
    (let [
          file (create-excel-report sec)
    ]
    {:status 200 :headers {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Content-Disposition" (str "attachment;filename=" sec ".xlsx") } :body (io/input-stream (str xlsdir sec ".xlsx") )}
    )
  )
  (GET "/clientexcel/:client" [client]
    (let [
          file (create-client-report client)
    ]
    {:status 200 :headers {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Content-Disposition" (str "attachment;filename=" client ".xlsx")} :body (io/input-stream (str xlsdir client ".xlsx") )}
    )
  )
  (GET "/clientbloombergportf/:client" [client]
    (let [
      url (str apipath "api/bloomberg_portf?portf=" client)
      ;tr1 (println (str "Trying to get securities"))
      response (client/get url {:headers {"authorization" (str "Bearer " (env :token))}})


    ]
    {:status 200 :headers {"Content-Type" "text/plain;charset=utf-8", "Content-Disposition" (str "attachment;filename=" client ".txt")} :body (io/input-stream (str bloombergportdir client ".txt") )}
    )
  )
  (GET "/clientbloombergtrans/:client" [client]
    (let [
      url (str apipath "api/bloomberg_trans?portf=" client)
      response (client/get url {:headers {"authorization" (str "Bearer " (env :token))}})
    ]
    {:status 200 :headers {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;", "Content-Disposition" (str "attachment;filename=" client "_trans.xlsx") } :body (io/input-stream (str xlsdir client "_trans.xlsx") )}
    )
  )



  ;;(POST "/tradeidea/imageupload" request (on-upload-image request) )
  ;;(POST "/tradeidea/" request (on-save-html request) )


  (resources "/")
)


(def http-handler
  (-> routes
      (wrap-defaults api-defaults)
      wrap-with-logger
      wrap-gzip))

(defn -main [& [port]]
  (let [
    port (Integer. (or port (env :port) 10555))
    ;tr1 (println (str "token=" (env :token)))
    ]
    (run-jetty http-handler {:port port :join? false})
  )
)
