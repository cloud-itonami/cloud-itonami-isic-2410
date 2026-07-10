(ns steelworks.registry-test
  (:require [clojure.test :refer [deftest is]]
            [steelworks.registry :as r]))

;; ----------------------------- heat-chemistry-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/heat-chemistry-out-of-range? {:chemistry-deviation-actual 0.05 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10})))
  (is (not (r/heat-chemistry-out-of-range? {:chemistry-deviation-actual -0.10 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10})))
  (is (not (r/heat-chemistry-out-of-range? {:chemistry-deviation-actual 0.10 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/heat-chemistry-out-of-range? {:chemistry-deviation-actual -0.35 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10}))
  (is (r/heat-chemistry-out-of-range? {:chemistry-deviation-actual 0.35 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/heat-chemistry-out-of-range? {})))
  (is (not (r/heat-chemistry-out-of-range? {:chemistry-deviation-actual 0.35}))))

;; ----------------------------- register-heat-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-heat-dispatch "heat-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-heat-dispatch "heat-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-HET-000007"))
    (is (= (get-in result ["record" "heat_id"]) "heat-1"))
    (is (= (get-in result ["record" "kind"]) "heat-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-heat-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-heat-dispatch "heat-1" "" 0)))
  (is (thrown? Exception (r/register-heat-dispatch "heat-1" "JPN" -1))))

;; ----------------------------- register-mill-cert -----------------------------

(deftest evidence-is-a-draft-not-real-certification
  (let [result (r/register-mill-cert "heat-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest evidence-assigns-evidence-number
  (let [result (r/register-mill-cert "heat-1" "JPN" 3)]
    (is (= (get result "evidence_number") "JPN-MIL-000003"))
    (is (= (get-in result ["record" "heat_id"]) "heat-1"))
    (is (= (get-in result ["record" "kind"]) "mill-cert-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest evidence-validation-rules
  (is (thrown? Exception (r/register-mill-cert "" "JPN" 0)))
  (is (thrown? Exception (r/register-mill-cert "heat-1" "" 0)))
  (is (thrown? Exception (r/register-mill-cert "heat-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-heat-dispatch "heat-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-heat-dispatch "heat-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-HET-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-HET-000001" (get-in hist2 [1 "record_id"])))))
