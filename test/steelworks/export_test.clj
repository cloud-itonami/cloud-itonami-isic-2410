(ns steelworks.export-test
  "Audit-package export contract -- social/regulatory hand-off shape,
  plus `pedigree-for-heat`'s cross-actor supply-chain-linkage export
  (ADR-2607999950)."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [steelworks.export :as export]
            [steelworks.operation :as op]
            [steelworks.robotics :as robotics]
            [steelworks.store :as store]))

(def operator {:actor-id "op-1" :actor-role :mill-metallurgist :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- seed-with-one-dispatch []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v" {:op :mill-rules/verify :subject "heat-1"})
    (approve! actor "v")
    (exec! actor "d" {:op :actuation/dispatch-heat :subject "heat-1"})
    (approve! actor "d")
    db))

(deftest audit-package-shape
  (let [db (seed-with-one-dispatch)
        pkg (export/audit-package db)]
    (is (= "2410" (:isic pkg)))
    (is (= "cloud-itonami-isic-2410" (:business-id pkg)))
    (is (= :edn-maps (:format pkg)))
    (is (pos? (get-in pkg [:counts :ledger])))
    (is (= 1 (get-in pkg [:counts :dispatches])))
    (is (some #(= "heat-1" (:id %)) (:heats pkg)))
    (is (true? (:heat-dispatched?
                (first (filter #(= "heat-1" (:id %)) (:heats pkg))))))))

(deftest csv-bundle-has-headers-and-rows
  (let [db (seed-with-one-dispatch)
        bundle (export/package->csv-bundle db)]
    (is (every? bundle ["heats.csv" "ledger.csv" "dispatches.csv" "mill-cert.csv"]))
    (is (str/starts-with? (get bundle "heats.csv") "id,unit-name,"))
    (is (re-find #"heat-1" (get bundle "heats.csv")))
    (is (re-find #"JPN-HET-000000" (get bundle "dispatches.csv")))
    (is (re-find #":actuation/dispatch-heat" (get bundle "ledger.csv")))))

(deftest empty-store-export-is-usable
  (let [db (store/seed-db)
        pkg (export/audit-package db)
        bundle (export/package->csv-bundle db)]
    (is (= 0 (get-in pkg [:counts :dispatches])))
    (is (= 4 (get-in pkg [:counts :heats])))
    (is (str/includes? (get bundle "ledger.csv") "seq,t,op"))))

;; ---------------------------------------------------------------------------
;; pedigree-for-heat (ADR-2607999950 cross-actor supply-chain linkage)
;; ---------------------------------------------------------------------------

(deftest pedigree-for-heat-builds-a-valid-pedigree-from-real-telemetry
  (testing "a heat carrying its own real, already-simulated tensile-test telemetry yields a shape-valid pedigree"
    (let [heat (merge {:id "heat-pedigree-1" :coupon-mass-kg 5.0}
                       (robotics/tensile-test-telemetry-for {:coupon-mass-kg 5.0}))
          p (export/pedigree-for-heat heat "2026-07-15")]
      (is (some? p))
      (is (true? (pedigree/valid? p)))
      (is (= "heat-pedigree-1" (:pedigree/subject-lot-id p)))
      (is (= "cloud-itonami-isic-2410" (:pedigree/issuing-actor p)))
      (is (= "2026-07-15" (:pedigree/issued-at p)))
      (testing "the claim value is the heat's OWN real simulated reading, not invented"
        (is (= (:sim-tensile-load-n heat)
               (pedigree/claim-value p :tensile-test-load-n)))
        (is (= (:sim-tensile-load-n (robotics/tensile-test-telemetry-for {:coupon-mass-kg 5.0}))
               (pedigree/claim-value p :tensile-test-load-n))))))
  (testing "a heavier coupon-mass-kg yields a proportionally larger pedigree claim -- proves the claim tracks the real simulated trajectory, not a fixed number"
    (let [light-heat (merge {:id "heat-light"} (robotics/tensile-test-telemetry-for {:coupon-mass-kg 2.0}))
          heavy-heat (merge {:id "heat-heavy"} (robotics/tensile-test-telemetry-for {:coupon-mass-kg 4.0}))
          light-p (export/pedigree-for-heat light-heat "2026-07-15")
          heavy-p (export/pedigree-for-heat heavy-heat "2026-07-15")]
      (is (< (pedigree/claim-value light-p :tensile-test-load-n)
             (pedigree/claim-value heavy-p :tensile-test-load-n))))))

(deftest pedigree-for-heat-never-fabricates-missing-telemetry
  (testing "a heat with no real :sim-tensile-load-n on file yields nil, never an invented pedigree"
    (is (nil? (export/pedigree-for-heat {:id "heat-x"} "2026-07-15")))
    (is (nil? (export/pedigree-for-heat {:id "heat-x" :coupon-mass-kg 5.0} "2026-07-15"))
        "coupon-mass-kg alone is not telemetry -- the simulation must actually have been run and merged in first")))
