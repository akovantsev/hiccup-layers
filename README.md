#### Installation
For now – use `sha` + `deps`:
https://clojure.org/guides/deps_and_cli#_using_git_libraries

#### What
A macro to apply patches and transformations to hiccup forms.

#### Why
HTML is all about semantics!
```clojure
[:button {:onClick submit!} "submit"]
```
Then React complains about missing `:key`:
```clojure
[:button {:key "submit" :onClick submit!} "submit"]
```
Then you need to add a gap under your button, because it touches footer or something:
```clojure
[:button {:key "submit" :onClick submit! :style {:padding-bottom 10}} "submit"]
```
It's ok. Often CSS styles are written inline in hiccup in rum/reagent components.
This is good for code locality.

You look at that one `:padding-bottom` now occupying half a button, say "ugh" and move on.
What the next day will bring you? Colors? Round corners? Shadows!

Things get ugly and unreadable very fast.
Much of the ugliness is just a matter of taste. 
You press a hotkey to vertical align values in maps to win some legibility back.

Those are all cosmetics, and are mostly independent from each other.
So the only bad thing is how far button label is pushed away from `:button` tag.
```clojure
[:button {:key     "submit"
          :onClick submit!
          :style   {:padding-bottom     10
                    :color              "red"
                    :text-transform     "uppercase"
                    :background         "#ed3330"
                    :border-radius      "5px"
                    :-webkit-box-shadow "0px 5px 40px -10px rgba(0,0,0,0.57)"
                    :-moz-box-shadow    "0px 5px 40px -10px rgba(0,0,0,0.57)"
                    :box-shadow         "5px 40px -10px rgba(0,0,0,0.57)"}}
 "submit"]
```

But what happens when you need to layout things?
If you ever tried to e.g. "center `div` vertically" – 
you know that now style attributes:
- are interdependent, and
- spread across a bunch of DOM nodes.

This brings about a bunch of problems:
- you will not remember a thing about how it works even tomorrow: which attributes are relevant to centering, and which are  independent cosmetics.
- you can't sign all attributes with a single comment, so you write the same one all over the place, and now it is unreadable, and even easier to break.
- you can't sign them with a single commit message: next fix tomorrow gonna be outside that commit.

CSS!

I'm gonna add `:class` and put styles in `.css` file (or sibling `[:style {} "..."]` tag), grouped by class names, and write comments!

That is better solution, but:
- locality is lost – gotta browse those `.css` files (Are these classes in `.css` still used anywhere? Is that class in `hiccup` still has (ever had) any definitions?).
- you will misspell classnames in `.css` or in `hiccup` – no warnings.
- you/others will overwrite attributes in the same file, or, better, in a different one, or in inline style. – no warnings. 

This kind of debug sessions is not fun at all.

#### Remedy
To alleviate the pain, consider `layers` macro, which delivers on visual promise of neat stackoverflow answers and more!

It helps you:
  - to separate component's DOM structure from all the noise you inevitably have to pepper it with.
  - to group node/style attributes and other manipulations by use case/requirement.
  - to keep those layers and their descriptions close to each other.
  - to have cleaner commit histories and diffs*.

*terms and conditions may apply
  

```clojure
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
```
```clojure
;=>
[:div {:key   :outer
       :style {:display  "table"
               :position "absolute"
               :top      0
               :left     0
               :height   "100%"
               :width    "100%"}}
 [:div {:key   :middle
        :style {:display        "table-cell"
                :vertical-align "middle"}}
  [:div {:key     :inner
         :style   {:margin-left  "auto"
                   :margin-right "auto"
                   :width        "400px"}
         :onClick (fn [e] (println "kappa"))}
   [:h1 {} "The Content"]
   [:p {} "Once upon a midnight dreary..."]]]]
``` 

#### Usage
 
```clojure
(layers shape
  op1 {form-id1 v1, form-id2 v2}
  op2 {...}
  ...)
```

`layers` macro walks `shape` and looks for `forms` representing DOM nodes: `[tag attrs & children]`.
Then, for each matching form it looks in 4 places and collects ids, if any, in a particular order of priority.
Then, if any of ids is present in patch map (`k`) - macro applies transformation (`op`) with `form` and `v` as args.


##### 4 places it looks for ids 
In order of priority (1st found in patch map is considered a match):
- `@!primary-key` - custom key, you can `reset!` to whatever you need, if following 3 are not suitable for you.
- css `:id` in attrs map (second element of hiccup form).
- tag (first element of hiccup form), handy, if you prefer using tags with css-id/css-class backed in e.g. `:div#id.class1.class2`
- react's `:key` in attrs map.

Ids can be anything (I think):
```clojure
(layers
  [:div {}
   (for [idx (range 2)]
     [:div {:key [:a idx]}])]

  :style
  {[:a idx] {:color "red"}})

;=>
[:div {}
 ([:div {:key [:a 0], :style {:color "red"}}]
  [:div {:key [:a 1], :style {:color "red"}}])]
```

```clojure
(macroexpand-1
  '(layers
     [:div {}
      (for [idx (range 2)]
        [:div {:key [:a idx]}])]
  
     :style
     {[:a idx] {:color "red"}}))
;=> 
[:div {} 
  (for [idx (range 2)]
    [:div {:key   [:a idx]
           :style {:color "red"}}])]

```

Being a macro it allows you:
- to apply transformations to a single form in `for` loop before it expands into 100 table `:tr` rows.
- to use symbols as ids, like `idx`.
 

`layers` uses `patch-op` multimethod, so you could add your own ops, if you fancy.

Built-in operations are (better see source): 
- `:style` - merges :style attribute with provided patch
- `:attrs` - shallow-merges entire attributes map with patch.
- `:merge` - deep-merges (see source) entire attributes map with patch.

and, hopefully, self-explanatory: 
- `:wrap`
- `:prepend-child (:cons-child)`
- `:append-child  (:conj-child)`
- `:prepend-sibling (:cons-sibling)`
- `:append-sibling  (:conj-sibling)`

#### warnings

`!warning` atom gets `reset!` every time `layers` detects:
- unused form id in patch
- attribute gets overridden while applying patch

Just `(add-watch !warning ::pr (fn [k r o n] (js/console.log n))` and stay informed!

```clojure
{:warning  "Replaced value."
 :in       [:span {:key idx :style {:padding 5}} "x"]
 :op       :style
 :replaced 5
 :with     7
 :at       [1 :style :padding]
 :merging  {:margin 7, :padding 7}
 :shape    [:div#id {:style {:font-size 10}}
            (for [idx (range 3)]
              [:span {:key idx :style {:padding 5}} "x"])
            [:div {:key     :ch1
                   :style   {:color "red"}
                   :onClick inc}]]}
```

```clojure
{:warning "Unused keys in patch."
 :op      :style
 :unused  #{typo}
 :patch   {idx  {:padding 5}
           typo {:opacity 0.6}}
 :shape   [:div#id {:style {:font-size 10}}
           (for [idx (range 3)]
             [:span {:key idx :style {:padding 5}} "x"])
           [:div {:key     :ch1
                  :style   {:color "red"}
                  :onClick inc}]]}
```

In both warnings `:shape` is *before* any walking (at the start of applying the layer).

