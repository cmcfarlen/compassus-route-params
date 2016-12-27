(ns route-params.core
  (:require
   [goog.dom :as gdom]
   [om.dom :as dom]
   [om.next :as om :refer-macros [defui ui]]
   [compassus.core :as compassus]
   [bidi.bidi :as bidi]
   [cljs.reader :as edn]
   [pushy.core :as pushy]
   [cemerick.url :as url]
   ))

(enable-console-print!)

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

(defui ItemDetailsPage
  static om/IQuery
  (query [this]
    [{:item (om/get-query Item)}])
  Object
  (render [this]
    (let [props (om/props this)]
      (println "render ItemView" props)
      (dom/div nil
        (dom/h1 nil "Item Details Page")
        (item (:item props))
        ))))

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
        (dom/h3 nil "Items")
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

(defmethod local-mutate 'app/set-params
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

(defn merge-fn
  [a b]
  (if (map? a)
    (merge-with merge-fn a b)
    b))

(defn query-params
  [href]
  (let [u (url/url href)]
    (reduce-kv (fn [c k v]
                 (assoc c (keyword k) v)) {}  (:query u))))

(defn wrapper [{:keys [owner factory props]}]
  (dom/div nil
    (dom/h1 nil "Wrapper")
    (factory props)))

(def bidi-routes
  ["/" {"" :item-list-page
        "item/" {[:item-id] :item-details}}])

(def routes
  {:item-list-page ItemListPage
   :item-details ItemDetailsPage})

(defn create-app
  [{:keys [routes index-route bidi-routes wrapper init-state]
    :or {init-state {}
         index-route :index}}]
  (let [history (atom nil)
        remote-parser (om/parser {:read pretend-remote-read})
        reconciler (om/reconciler
                    {:state init-state
                     :normalize true
                     :pathopt true
                     :shared {:navigate (fn [path] (pushy/set-token! @history path))}
                     :send (fn [{:keys [remote]} cb]
                             (println "query for remote: " remote)
                             (let [{:keys [query rewrite]} (om/process-roots remote)
                                   remote-result (remote-parser {} query)
                                   rewritten (rewrite remote-result)]
                               (println "remote-result: " remote-result " rewritten: " rewritten)
                               (cb rewritten remote)))
                     :parser (compassus/parser {:read local-read :mutate local-mutate :route-dispatch false})
                     :merge-tree merge-fn})]
    (compassus/application
     {:routes routes
      :index-route (or (:handler (bidi/match-route bidi-routes (.. js/window -location -pathname)))
                       index-route)
      :reconciler reconciler
      :mixins (cond->
                [(compassus.core/will-mount
                  (fn [c]
                    (let [h (pushy/pushy (fn [r]
                                           (compassus/set-route! c
                                                                 (:handler r)
                                                                 {:tx [(list 'app/set-params
                                                                             (assoc r
                                                                                    :query-params
                                                                                    (query-params (.. js/window -location -href))))]}))
                                         (fn [m]
                                           (bidi/match-route bidi-routes m)))]
                      (reset! history h)
                      (pushy/start! h))))
                 (compassus.core/will-unmount #(pushy/stop! @history))]

                wrapper
                (conj (compassus/wrap-render wrapper)))
      })))

(defonce app (create-app {:routes routes
                          :index-route :item-list-page
                          :bidi-routes bidi-routes
                          :wrapper wrapper}))

(defn main
  []
  (compassus/mount! app (gdom/getElement "app")))

(defonce done-main (main))

(comment

 (deref (compassus/get-reconciler app))

 (let [reconciler (compassus/get-reconciler app)
       parser (-> reconciler :config :parser)
       st (-> reconciler :config :state)]
   (parser {:state st} (om/get-query (om/app-root reconciler))))

 (let [navigate (:navigate (om/shared (om/app-root (compassus/get-reconciler app))))]
   (navigate "/item/1"))

 )

