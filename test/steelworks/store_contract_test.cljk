(ns steelworks.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [steelworks.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Double-Bottom Heat DB-04" (:unit-name (store/heat s "heat-1"))))
      (is (= "JPN" (:jurisdiction (store/heat s "heat-1"))))
      (is (= 0.05 (:chemistry-deviation-actual (store/heat s "heat-1"))))
      (is (= -0.10 (:chemistry-deviation-min (store/heat s "heat-1"))))
      (is (= 0.10 (:chemistry-deviation-max (store/heat s "heat-1"))))
      (is (false? (:quality-defect-unresolved? (store/heat s "heat-1"))))
      (is (= 0.35 (:chemistry-deviation-actual (store/heat s "heat-3"))))
      (is (true? (:quality-defect-unresolved? (store/heat s "heat-4"))))
      (is (false? (:heat-dispatched? (store/heat s "heat-1"))))
      (is (false? (:mill-certified? (store/heat s "heat-1"))))
      (is (= ["heat-1" "heat-2" "heat-3" "heat-4"]
             (mapv :id (store/all-heats s))))
      (is (nil? (store/quality-screen-of s "heat-1")))
      (is (nil? (store/requirements-verification-of s "heat-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/evidence-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-evidence-sequence s "JPN")))
      (is (false? (store/heat-already-dispatched? s "heat-1")))
      (is (false? (store/heat-already-certified? s "heat-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :heat/upsert
                                 :value {:id "heat-1" :unit-name "Sakura Double-Bottom Heat DB-04"}})
        (is (= "Sakura Double-Bottom Heat DB-04" (:unit-name (store/heat s "heat-1"))))
        (is (= 0.05 (:chemistry-deviation-actual (store/heat s "heat-1"))) "unrelated field preserved"))
      (testing "verification / quality-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["heat-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/requirements-verification-of s "heat-1")))
        (store/commit-record! s {:effect :quality-screen/set :path ["heat-1"]
                                 :payload {:heat-id "heat-1" :verdict :resolved}})
        (is (= {:heat-id "heat-1" :verdict :resolved} (store/quality-screen-of s "heat-1"))))
      (testing "heat dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :heat/mark-dispatched :path ["heat-1"]})
        (is (= "JPN-HET-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "heat-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:heat-dispatched? (store/heat s "heat-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/heat-already-dispatched? s "heat-1")))
        (is (false? (store/heat-already-dispatched? s "heat-2"))))
      (testing "class evidence drafts a record and advances the sequence"
        (store/commit-record! s {:effect :heat/mark-certified :path ["heat-1"]})
        (is (= "JPN-MIL-000000" (get (first (store/evidence-history s)) "record_id")))
        (is (= "mill-cert-draft" (get (first (store/evidence-history s)) "kind")))
        (is (true? (:mill-certified? (store/heat s "heat-1"))))
        (is (= 1 (count (store/evidence-history s))))
        (is (= 1 (store/next-evidence-sequence s "JPN")))
        (is (true? (store/heat-already-certified? s "heat-1")))
        (is (false? (store/heat-already-certified? s "heat-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/heat s "nope")))
    (is (= [] (store/all-heats s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/evidence-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-evidence-sequence s "JPN")))
    (store/with-heats s {"x" {:id "x" :unit-name "n" :chemistry-deviation-actual 0.05
                                   :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10
                                   :quality-defect-unresolved? false
                                   :heat-dispatched? false :mill-certified? false
                                   :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:unit-name (store/heat s "x"))))))
