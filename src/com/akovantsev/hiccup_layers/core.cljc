(ns com.akovantsev.hiccup-layers.core
  (:require
   [clojure.walk :as walk]))


;; This is an extension point. It allows you to use any attributes' key
;; as form id you want patches to be applied to:
(def !primary-key (atom :data-key))


;; Warnings for better dev experience. Lets you know there are unused patches.
;; Which usually means either typo in form id, or leftovers from old code.
;; I'd use clojure.core/tap>, but it'd force you to use clojure/script 1.10+.
;; for tap, just do (add-watch !warning ::tap (fn [k r o n] (tap> n))
(def !warning (atom nil))


(defn -unquote [form]
  (if (and (seqable? form) (-> form first (= 'quote)))
    (second form)
    form))


(defn -unquote-keys [m]
  (reduce-kv #(assoc %1 (-unquote %2) %3) {} m))

(defn -merge! [a b op shape form path patch]
  (let [path (or path [])]
    (cond
      (= a b)  a
      (nil? a) b
      (nil? b) a

      (and (seqable? a) (empty? a))
      b

      (and (map? a) (map? b))
      (if (empty? a)
        b
        (reduce-kv
          (fn [m k bv]
            (assoc m k (-merge! (get a k) bv shape op form (conj path k) patch)))
          a b))

      :else
      (do (reset! !warning {:warning "Replaced value."
                            :in form :op op :replaced a :with b :at path :merging patch :shape shape})
          b))))



(defn -form-ids [form]
  (when (vector? form)
    (let [[tag attrs] form]
      ;; accepting only [tag {} ...]
      ;; might have a problem when attrs map is not literal. oh well.
      (when (map? attrs)
        ;; in order of priority:
        [;; custom key, in case :key is so nasty you'd rather have another one used:
         (get attrs @!primary-key)
         (get attrs :id)
         ;; if you prefer using tags with id/css-class backed in :div#id.class1.class2:
         tag
         ;; most likely you already have a :key, for react:
         (get attrs :key)]))))


(defn -patch-op-dispatch [shape op form v] op)
(defmulti patch-op -patch-op-dispatch)
(defmethod patch-op :attrs           [shape op form v] (update form 1 -merge! v shape op form [1] v))
(defmethod patch-op :style           [shape op form v] (update form 1 update :style -merge! v shape op form [1 :style] v))
(defmethod patch-op :merge           [shape op form v] (update form 1 -merge! v shape op form [1] v))
(defmethod patch-op :wrap            [shape op form v] (conj v form))
(defmethod patch-op :prepend-child   [shape op form v] (let [[t a & ch] form] (into [t a v] ch)))
(defmethod patch-op    :cons-child   [shape op form v] (let [[t a & ch] form] (into [t a v] ch)))
(defmethod patch-op  :append-child   [shape op form v] (conj form v))
(defmethod patch-op    :conj-child   [shape op form v] (conj form v))
;; next 4 rely on [:div {} 1 2] = [:div {} [1 2]]:
(defmethod patch-op :prepend-sibling [shape op form v] [v form])
(defmethod patch-op    :cons-sibling [shape op form v] [v form])
(defmethod patch-op  :append-sibling [shape op form v] [form v])
(defmethod patch-op    :conj-sibling [shape op form v] [form v])


(defn -patch [shape [op patch]]
  (assert (keyword? op))
  (assert (map? patch))
  (let [patch   (-unquote-keys patch)
        !unused (atom (-> patch keys set))
        step    (fn step [form]
                  (if-some [ids (-form-ids form)]
                    (if-let [k (first (filter patch ids))]
                      (let [v (get patch k)]
                        (swap! !unused disj k)
                        (patch-op shape op form v))
                      form)
                    form))
        shape   (walk/postwalk step shape)
        unused  @!unused]
    (when-not (empty? unused)
      (reset! !warning {:warning "Unused keys in patch." :op op :unused unused :patch patch :shape shape}))
    shape))


(defn -layers [shape patches]
  (reduce -patch shape patches))


;; it is a macro to apply layers before any loops would expand to e.g. 10000 table rows:
(defmacro layers [shape & patches]
  (-layers shape (partition-all 2 patches)))

