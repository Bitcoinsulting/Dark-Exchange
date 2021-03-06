(ns darkexchange.model.trust-score
  (:require [clj-record.boot :as clj-record-boot]
            [clojure.contrib.logging :as logging]
            [darkexchange.model.identity :as identity])
  (:use darkexchange.model.base))

(clj-record.core/init-model
  (:associations (belongs-to scorer :fk scorer_id :model identity)
                 (belongs-to target :fk target_id :model identity)))

(defn find-trust-score
  ([target-identity] (find-trust-score (identity/current-user-identity) target-identity))
  ([scorer-identity target-identity]
    (find-record { :scorer_id (:id scorer-identity) :target_id (:id target-identity) })))

(defn find-all-trust-scores [target-identity]
  (find-records { :target_id (:id target-identity) }))

(defn add-trust-score
  ([target-identity basic-score] (add-trust-score (identity/current-user-identity) target-identity basic-score))
  ([scorer-identity target-identity basic-score]
    (if-let [trust-score-id (:id (find-trust-score scorer-identity target-identity))]
      (do
        (update { :id trust-score-id :basic basic-score })
        trust-score-id)
      (insert { :scorer_id (:id scorer-identity) :target_id (:id target-identity) :basic basic-score }))))

(defn find-or-create-trust-score [target-identity]
  (if-let [trust-score (find-trust-score target-identity)]
    trust-score
    (get-record (add-trust-score target-identity 0.0))))

(defn set-trust-score [target-identity basic-trust-score]
  (update (assoc (find-or-create-trust-score target-identity) :basic basic-trust-score)))

(defn calculate-single-chain [trust-score]
  (if-let [scorer-score (find-trust-score { :id (:scorer_id trust-score) })]
    (* (:basic scorer-score) (:basic trust-score))
    0.0))

(defn calculate-combined-score [target-identity]
  (let [trust-scores (find-all-trust-scores target-identity)
        single-chain-scores (map calculate-single-chain trust-scores)]
    (reduce + single-chain-scores)))

(defn update-combined-score [target-identity]
  (let [trust-score-id (:id (find-or-create-trust-score target-identity))]
    (update { :id trust-score-id :combined (calculate-combined-score target-identity) })
    trust-score-id))

(defn my-scores []
  (find-by-sql ["SELECT basic FROM trust_scores WHERE scorer_id = ?" (:id (identity/current-user-identity))]))

(defn percent-float [float-num]
  (if float-num
    (* float-num 100)
    0.0))

(defn basic-percent [trust-score]
  (percent-float (:basic trust-score)))

(defn combined-percent [trust-score]
  (percent-float (:combined trust-score)))

(defn basic-percent-int [trust-score]
  (.intValue (basic-percent trust-score)))

(defn combined-percent-int [trust-score]
  (.intValue (combined-percent trust-score)))