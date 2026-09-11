(ns steelworks.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clea heat through
  intake -> requirements verification -> quality-defect screening ->
  block-dispatch proposal (always escalates) -> human approval ->
  commit, then through mill-cert proposal (always
  escalates) -> human approval -> commit, then shows five HARD holds
  (a jurisdiction with no spec-basis, an out-of-spec assembly
  tolerance, an unresolved quality defect screened directly via `:quality/
  screen` [never via an actuation op against an unscreened heat --
  see this actor's own governor ns docstring / the lesson
  `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s, `water`'s and `telecom`'s
  ADR-0001s already recorded], and a double block-dispatch/
  mill-cert-issuance of an already-processed heat)
  that never reach a human at all, and prints the audit ledger + the
  draft block-dispatch and mill-cert records."
  (:require [langgraph.graph :as g]
            [steelworks.export :as export]
            [steelworks.store :as store]
            [steelworks.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :mill-metallurgist :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== heat/intake heat-1 (JPN, clean; tolerance within spec, no quality defect) ==")
    (println (exec! actor "t1" {:op :heat/intake :subject "heat-1"
                                :patch {:id "heat-1" :unit-name "Sakura Double-Bottom Heat DB-04"}} operator))

    (println "== mill-rules/verify heat-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :mill-rules/verify :subject "heat-1"} operator))
    (println (approve! actor "t2"))

    (println "== quality/screen heat-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :quality/screen :subject "heat-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/dispatch-heat heat-1 (always escalates -- actuation/dispatch-heat) ==")
    (let [r (exec! actor "t4" {:op :actuation/dispatch-heat :subject "heat-1"} operator)]
      (println r)
      (println "-- human mill metallurgist approves --")
      (println (approve! actor "t4")))

    (println "== actuation/issue-mill-cert heat-1 (always escalates -- actuation/issue-mill-cert) ==")
    (let [r (exec! actor "t5" {:op :actuation/issue-mill-cert :subject "heat-1"} operator)]
      (println r)
      (println "-- human mill metallurgist approves --")
      (println (approve! actor "t5")))

    (println "== mill-rules/verify heat-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :mill-rules/verify :subject "heat-2" :no-spec? true} operator))

    (println "== mill-rules/verify heat-3 (escalates -- human approves; sets up the out-of-spec test) ==")
    (println (exec! actor "t7" {:op :mill-rules/verify :subject "heat-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/dispatch-heat heat-3 (0.35 outside [-0.10,0.10] tolerance -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/dispatch-heat :subject "heat-3"} operator))

    (println "== quality/screen heat-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :quality/screen :subject "heat-4"} operator))

    (println "== actuation/dispatch-heat heat-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/dispatch-heat :subject "heat-1"} operator))

    (println "== actuation/issue-mill-cert heat-1 AGAIN (double-issuance -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/issue-mill-cert :subject "heat-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft block-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft mill-cert records ==")
    (doseq [r (store/evidence-history db)] (println r))

    (println "== social hand-off: audit package counts ==")
    (println (:counts (export/audit-package db)))
    (println "== social hand-off: CSV bundle keys ==")
    (println (keys (export/package->csv-bundle db)))))
