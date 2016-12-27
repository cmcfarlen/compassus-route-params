# route-params

An example of using compassus for client side routing.

## Overview

This example is a simple item list, but it uses the url to steer the initial app state and focused item.  The pushy callback handler transacts the query parameters and the `app/set-params` mutation can be used to setup the app state for the particular "page".

``` clojure
(pushy/pushy
 (fn [r]
  (compassus/set-route! c
   (:handler r)
   {:tx [(list 'app/set-params
           (assoc r
            :query-params
            (query-params (.. js/window -location -href))))]}))
 (fn [m]
  (bidi/match-route bidi-routes m)))
```

Then the mutation can handle the route and query params to setup the view.

``` clojure
(defmethod local-mutate 'app/set-params
  [{:keys [state route]} k params]
  (when (= (:handler params) :item-details)
    {:action (fn []
               (swap! state assoc :focus-item (edn/read-string (:item-id (:route-params params)))))}))
```

The :focus-item key can then be used the om.next queries for app state:

``` clojure
(defmethod local-read :item
 [{:keys [target query state ast]} k params]
 (let [st @state
       focus-item (:focus-item st)
       item (get-in st [:item/by-id focus-item])]
  (if item
   {:value (om/db->tree query item st)}
   {target (assoc ast :query-root true :params {:item-id focus-item})})))
```

If the item is not currently in the app state then the `:remote` is invoked to fetch it from the server.

## Setup

Run `lein repl` and then:

```
user=> (start)
```

Browse to http://localhost:3449 or http://localhost:3449/item/1

## License

Copyright © 2014 Chris McFarlen

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
