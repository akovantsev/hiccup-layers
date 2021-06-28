(ns com.akovantsev.hiccup-layers.core-test
  (:require [com.akovantsev.hiccup-layers.core :as layers :refer [layers]]))


(add-watch layers/!warning ::prn (fn [k r o n] (prn n)))

(assert (= ['pk 10 :div.foo :bar]
          (layers/-form-ids [:div.foo {:id 10 :key :bar @layers/!primary-key 'pk} [:span {}]])))

(assert (= (layers
             [:div#id {}
              (for [idx (range 3)]
                [:span {:key idx} "x"])]

             :append-child
             {:div#id [:div {:key :ch1}]}

             :style
             {:div#id {:font-size 10}}

             :merge
             {:ch1 {:style   {:color "red"}
                    :onClick inc}}

             :style
             {idx  {:padding 5}
              typo {:opacity 0.6}}
             :style
             {'idx {:margin 7 :padding 7}})

          [:div#id {:style {:font-size 10}}
           '([:span {:key 0 :style {:padding 7 :margin 7}} "x"]
             [:span {:key 1 :style {:padding 7 :margin 7}} "x"]
             [:span {:key 2  :style {:padding 7 :margin 7}} "x"])
           [:div {:key     :ch1
                  :style   {:color "red"}
                  :onClick inc}]]))



(macroexpand-1
  '(layers
     [:div.co {:id "x" :data-key "dk"}
      [:div {:key :in} "yo"]
      [:div {}
       "bar"
       (for [idx (range 3)]
         [:span {:key idx} "goo"])
       (for [jdx (range 3)]
         [:span {:key jdx} "x"])]]

     ;; foo bar use case:
     :merge
     {:div.co {:style {:color "red"}}
      :in     {:onClick (fn [e] (.-target e))}}

     ;; baz:
     :style
     {:div.co {:position "absolute"}}

     :attrs
     {jdx {:onClick (fn [e] (inc e))}}

     ;; align against something
     :style
     {'idx {:padding-left 10}}

     ;; more space
     :style
     {idx {:margin 6}}

     ;; pseudoclasses in hiccup
     :append-child
     {jdx [:style {:key :s} ".foo:focused {color: red}"]}

     :prepend-child
     {:s ".bar {color: blue}"}))



(layers
  [:div {:key :inner}
   [:h1 {} "The Content"]
   [:p {} "Once upon a midnight dreary..."]]

  ;; vertically centering div: https://stackoverflow.com/a/6182661
  ;; since I dont care about those wrappers from business logic pov –
  ;; I turned them into layers. So if I no longer need to center damn div,
  ;; I can just comment out/delete relevant layers below, and get a nice git diff.
  :wrap
  {:inner [:div {:key :middle}]}
  :wrap
  {:middle [:div {:key :outer}]}
  ;; Here I can write at length why exactly styling a div requires me to modify DOM, etc.
  :style
  {:outer {:display  "table"
           :position "absolute"
           :top      0
           :left     0
           :height   "100%"
           :width    "100%"}
   :middle {:display        "table-cell"
            :vertical-align "middle"}
   :inner {:margin-left  "auto"
           :margin-right "auto"}}

  ;; now onward to the next requirement!
  ;;/* Whatever width you want */ said StackOverflow answer:
  :merge
  {:inner {:style   {:width "400px"}
           :onClick '(fn [e] (println "kappa"))}})


(assert
  (=
    (layers
      [:div {}
       (for [idx (range 2)]
         [:div {:key [:a idx]}])]

      :style
      {[:a idx] {:color "red"}})

    [:div {}
     '([:div {:key [:a 0], :style {:color "red"}}]
       [:div {:key [:a 1], :style {:color "red"}}])]))

(layers/-merge! {:a 1} {:a 2 :b 3} {:a 1} :op {:a 1} [] {:a 2 :b 3})
(layers/-merge! {:key 1 :style {:a 1}} {:style {:a 2 :b 3}} {:key 1 :style {:a 1}} :op {:key 1 :style {:a 1}} [] {:style {:a 2 :b 3}})
