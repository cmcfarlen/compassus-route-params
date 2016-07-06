(ns route-params.core
  (:require
   [goog.dom :as gdom]
   [om.dom :as dom]
   [om.next :as om :refer-macros [defui ui]]
   [compassus.core :as compassus]
   [bidi.bidi :as bidi]
   [cljs.reader :as edn]
   [pushy.core :as pushy]))

(enable-console-print!)

(declare history)

(def bidi-routes
  ["/" {"" :item-list-page
        "item/" {[:item-id] :item-details}}])

(defui Item
  static om/Ident
  (ident [this {:keys [item/id]}]
    [:item/by-id id])
  static om/IQuery
  (query [this]
    [:item/name :item/id :item/description :item/other])
  Object
  (render [this]
    (let [props (om/props this)]
      (println "render Item" props)
      (dom/div nil
             (dom/h3 nil (str "Item: " (:item/name props)))
             (dom/p nil (:item/description props))
             (dom/input #js {:type "text"
                         :value (:item/description props)
                         :onChange (fn [e]
                                      (println "changed: " (.. e -target -value))
                                      (om/transact! this [(list 'item/update {:item/description (.. e -target -value)})]))})))))

(def item (om/factory Item {:keyfn :item/id}))

(defui RelatedInfo
  static om/IQuery
  (query [this]
    [:related/to {:related/info [:related/a :related/b]}])
  Object
  (render [this]
    (let [props (om/props this)]
      (println "render Related")
      (dom/div nil
        (dom/h4 nil (str "Related Info (to " (:related/to props) ")"))
        (dom/p nil (:related/a (:related/info props)))
        (dom/p nil (:related/b (:related/info props)))))))

(def related-info (om/factory RelatedInfo))

(defui ItemDetailsPage
  static om/IQuery
  (query [this]
    [{:item (om/get-query Item)}
     {:related (om/get-query RelatedInfo)}])
  Object
  (render [this]
    (let [props (om/props this)]
      (println "render ItemView" props)
      (dom/div nil
        (dom/h1 nil "Item Details Page")
        (item (:item props))
        (if-let [r (:related props)]
          (related-info r))))))

(defui ItemEntry
  static om/Ident
  (ident [this {:keys [item/id]}]
    [:item/by-id id])
  static om/IQuery
  (query [this]
    [:item/name :item/id])
  Object
  (render [this]
    (let [props (om/props this)
          {:keys [navigate]} (om/get-computed props)]
      (dom/li nil
              (dom/a #js {:href (str "/item/" (:item/id props))} (str "Item " (:item/name props)))))))

(def item-entry (om/factory ItemEntry {:keyfn :item/id}))

(defui ItemListPage
  static om/IQuery
  (query [this]
    [{:items (om/get-query ItemEntry)}])
  Object
  (render [this]
    (let [props (om/props this)]
      (println "render ItemListPage: " props)
      (dom/div nil
        (apply dom/ul nil (map item-entry (:items props)))))))

(defmulti local-read om/dispatch)
(defmulti local-mutate om/dispatch)

(defn recurse-parser
  [{:keys [parser target query state ast] :as env}]
  (let [q (parser env query target)]
    (println "recursive parser response: " q)
    (if (or (empty? q) (nil? target))
      {:value q}
      {target (assoc ast :query q)})))

(defmethod local-read :default
  [{:keys [query state]} k params]
  (println "reading " k @state query)
  (let [st @state]
    {:value (om/db->tree query st st)}))

(defmethod local-read :item
  [{:keys [target query state ast]} k params]
  (println "local read item: " query)
  (let [st @state
        focus-item (:focus-item st)
        item (get-in st [:item/by-id focus-item])]
    (println "focus-item: " focus-item " target: " target " data: " item)
    (if item
      {:value (om/db->tree query item st)}
      {target (assoc ast :query-root true :params {:item-id focus-item})})))

(defmethod local-read :related
  [{:keys [target query state ast]} k params]
  (println "local read related " query)
  nil
  #_(let [st @state
        focus-item (:focus-item st)
        item (get-in st [:item/by-id focus-item])
        related-to (:item/name item)
        related (om/db->tree query (:related st) st)
        current-related-to (:related/to related)]
    (println "related target: " target " item:  " related-to " current: " current-related-to)
    (if (and related
             related-to
             (= current-related-to related-to))
      {:value related}
      (if related-to ; only ask remote after we know the item
        {target (assoc ast :query-root true :params {:item/name related-to})}))))

(defmethod local-read :items
  [{:keys [query state target ast]} k param]
  (println "local read items: " query)
  (let [st @state
        items (:items st)]
    (if items
      {:value (om/db->tree query items st)}
      {target (assoc ast :query-root true)})))

(defmethod local-read :item-details
  [{:keys [query state ast target] :as env} k params]
  (recurse-parser env))

(defmethod local-read :item-list-page
  [{:keys [query state ast target] :as env} k params]
  (recurse-parser env))

(defmethod local-mutate 'route/set-params
  [{:keys [state route]} k params]
  (println "set-params: " params route)
  (when (= (:handler params) :item-details)
    {:action (fn []
               (println "setting state" @state)
               (swap! state assoc :focus-item (edn/read-string (:item-id (:route-params params)))))}))

(defmethod local-mutate 'item/update
  [{:keys [state route ref] :as env} k params]
  (println "item update: " ref " -> " params)
  {:action (fn []
             (swap! state update-in ref merge params))})

(def routes
  {:item-list-page (compassus/index-route ItemListPage)
   :item-details ItemDetailsPage})

(defn wrapper [{:keys [owner factory props]}]
  (dom/div nil
    (factory props)))

(declare app)

(def history
  (pushy/pushy #(do
                 (println "pushy: " %)
                 (om/transact! (compassus/get-reconciler app) [(list 'route/set-params %)])
                 (compassus/set-route! app (:handler %))) (partial bidi/match-route bidi-routes)))



(def item-data
  (reduce (fn [m v]
            (assoc m (:item/id v) v)) {}
          [{:item/name "item-1" :item/id 1 :item/description "This is item 1"}
           {:item/name "item-2" :item/id 2 :item/description "This is item 2"}
           {:item/name "item-3" :item/id 3 :item/description "This is item 3"}
           {:item/name "item-4" :item/id 4 :item/description "This is item 4"}
           {:item/name "item-5" :item/id 5 :item/description "This is item 5"}
           {:item/name "item-6" :item/id 6 :item/description "This is item 6"}
           {:item/name "item-7" :item/id 7 :item/description "This is item 7"}]))

(def related-data
  {"item-1" {:related/a "item-1 related a" :related/b "item-1 related b"}
   "item-3" {:related/a "item-3 related a" :related/b "item-3 related b"}
   "item-5" {:related/a "item-5 related a" :related/b "item-5 related b"}
   "item-7" {:related/a "item-7 related a" :related/b "item-7 related b"}})

(defmulti pretend-remote-read om/dispatch)

(defmethod pretend-remote-read :default
  [env k params]
  (println "remote-read: " k params)
  (let [new-id (-> params :item second)]
    {:value {[:item/by-id new-id] {:item/id new-id :item/name (str "item " new-id "!!!")}}}))

(defmethod pretend-remote-read :items
  [{:keys [query]} k params]
  (println "remote reading items: " query)
  {:value (vals item-data)})

(defmethod pretend-remote-read :item
  [{:keys [query]} k params]
  (println "remote reading item: " query params)
  {:value (get item-data (:item-id params))})

(defmethod pretend-remote-read :related
  [{:keys [query]} k params]
  (println "remote reading related: " query params)
  {:value {:related/to (:item/name params)
           :related/info (get related-data (:item/name params))}})

(defn merge-fn
  [a b]
  (if (map? a)
    (merge-with merge-fn a b)
    b))

(defonce app (let [remote-parser (om/parser {:read pretend-remote-read})]
               (compassus/application {:routes routes
                                       :wrapper wrapper
                                       :history {:setup #(pushy/start! history)
                                                 :teardown #(pushy/stop! history)}
                                       :reconciler-opts {:state (atom {})
                                                         :merge-tree merge-fn
                                                         :merge compassus/compassus-merge
                                                         :normalize true
                                                         :pathopt true
                                                         :send (fn [{:keys [remote]} cb]
                                                                 (println "query for remote: " remote)
                                                                 (let [{:keys [query rewrite]} (om/process-roots remote)
                                                                       remote-result (remote-parser {} query)
                                                                       rewritten (rewrite remote-result)]
                                                                   (println "remote-result: " remote-result " rewritten: " rewritten)
                                                                   (cb rewritten remote)))
                                                         :parser (om/parser {:read local-read :mutate local-mutate})} })))

(defn main
  []
  (compassus/mount! app (gdom/getElement "app")))

(defonce done-main (main))

(comment

 ((:parser (:config (compassus/get-reconciler app))) {:state (-> (compassus/get-reconciler app) :config :state)} (om/get-query (om/app-root (compassus/get-reconciler app))))

 (compassus/set-route! app :home)
 (compassus/set-route! app :about)
 (pushy/set-token! history "/")
 (pushy/set-token! history "/item/42")
 (pushy/set-token! history "/item/7")
 (pushy/set-token! history "/item/3")

 (pushy/get-token history)
 (println (pr-str (-> (compassus/get-reconciler app) :config :state)))
 )

