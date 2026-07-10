(ns steelworks.export-test
  "Audit-package export contract -- social/regulatory hand-off shape."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [langgraph.graph :as g]
            [steelworks.export :as export]
            [steelworks.operation :as op]
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
