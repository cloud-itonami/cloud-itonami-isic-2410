(ns steelworks.upstream-ore-pedigree-test
  "ADR-2607999970's cross-actor supply-chain-linkage check
  (`steelworks.governor/upstream-ore-pedigree-claims-out-of-tolerance-
  violations`), exercised with HAND-BUILT `kotoba.pedigree` records
  (via the real `kotoba.pedigree/claim` constructor -- never a raw
  map literal that merely LOOKS like a pedigree). The genuine
  cross-repo proof -- an actual call into `cloud-itonami-isic-0710`'s
  `ironops.export/pedigree-for-production-record` -- lives in
  `test-cross-repo/steelworks/pedigree_integration_test.clj` (a
  separate alias, see deps.edn); this file only proves the GOVERNOR
  check itself is correct in isolation, independent of which upstream
  actor produced the pedigree. Mirrors `autoparts.upstream-pedigree-
  test`, one link earlier in this chain."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [steelworks.governor :as governor]
            [steelworks.store :as store]
            [steelworks.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :mill-metallurgist :phase 3})

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify! [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :mill-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- attach-ore-pedigree! [actor tid-prefix subject pedigree]
  (exec-op actor (str tid-prefix "-pedigree")
           {:op :heat/intake :subject subject
            :patch {:id subject :upstream-ore-pedigree pedigree}}
           operator))

(defn- clean-pedigree []
  (pedigree/claim "PEDIGREE-prod-1" "prod-1" "cloud-itonami-isic-0710"
                   {:grade-actual (+ governor/min-upstream-ore-grade-pct 5.0)
                    :quantity-tonnes 5200.0}
                   :evidence-basis ["ironops.store/production-record"]
                   :issued-at "2026-07-16"))

(defn- weak-pedigree []
  (pedigree/claim "PEDIGREE-prod-2" "prod-2" "cloud-itonami-isic-0710"
                   {:grade-actual (- governor/min-upstream-ore-grade-pct 5.0)
                    :quantity-tonnes 1000.0}
                   :evidence-basis ["ironops.store/production-record"]
                   :issued-at "2026-07-16"))

(defn- with-tensile-clearance
  "heat-1's seed data carries no :coupon-mass-kg -- give it one that
  clears steelworks.robotics's own real physics-2d tensile-test floor
  so the pre-existing tensile-test-out-of-tolerance check never
  interferes with these tests, which are only about the upstream-ore
  check."
  [db]
  (store/commit-record! db {:effect :heat/upsert :value {:id "heat-1" :coupon-mass-kg 5.0}}))

(deftest absent-upstream-ore-pedigree-is-a-no-op
  (testing "a heat with no :upstream-ore-pedigree dispatches exactly as before this ADR -- no new violation"
    (let [[db actor] (fresh)
          _ (with-tensile-clearance db)
          _ (verify! actor "t1pre" "heat-1")
          res (exec-op actor "t1" {:op :actuation/dispatch-heat :subject "heat-1"} operator)]
      (is (nil? (:upstream-ore-pedigree (store/heat db "heat-1"))))
      (is (= :interrupted (:status res)) "still escalates for human approval, same as before -- no HARD hold introduced")
      (let [r2 (approve! actor "t1")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:heat-dispatched? (store/heat db "heat-1"))))))))

(deftest valid-in-tolerance-upstream-ore-pedigree-dispatches-normally
  (testing "a shape-valid pedigree whose claim clears the acceptance floor does not block dispatch"
    (let [[db actor] (fresh)
          _ (with-tensile-clearance db)
          _ (verify! actor "t2pre" "heat-1")
          _ (attach-ore-pedigree! actor "t2pre2" "heat-1" (clean-pedigree))
          res (exec-op actor "t2" {:op :actuation/dispatch-heat :subject "heat-1"} operator)]
      (is (some? (:upstream-ore-pedigree (store/heat db "heat-1"))))
      (is (= :interrupted (:status res)) "still escalates for human approval -- actuation is never auto")
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:heat-dispatched? (store/heat db "heat-1"))))))))

(deftest upstream-ore-pedigree-claims-out-of-tolerance-is-held
  (testing "a shape-valid pedigree whose claim falls below the acceptance floor -> HARD hold, independent of chemistry/quality being otherwise clean"
    (let [[db actor] (fresh)
          _ (with-tensile-clearance db)
          _ (verify! actor "t3pre" "heat-1")
          _ (attach-ore-pedigree! actor "t3pre2" "heat-1" (weak-pedigree))
          res (exec-op actor "t3" {:op :actuation/dispatch-heat :subject "heat-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-ore-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest upstream-ore-pedigree-invalid-shape-is-held
  (testing "an attached map that fails kotoba.pedigree/valid? (e.g. a non-numeric claim, mimicking a self-reported string) -> HARD hold, never trusted at face value"
    (let [[db actor] (fresh)
          bad-pedigree (assoc (clean-pedigree) :pedigree/claims {:grade-actual "high"})
          _ (with-tensile-clearance db)
          _ (verify! actor "t4pre" "heat-1")
          _ (attach-ore-pedigree! actor "t4pre2" "heat-1" bad-pedigree)
          res (exec-op actor "t4" {:op :actuation/dispatch-heat :subject "heat-1"} operator)]
      (is (false? (pedigree/valid? bad-pedigree)) "sanity: the fixture really is shape-invalid")
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-ore-pedigree-invalid-shape} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest upstream-ore-pedigree-check-scoped-to-dispatch-heat-op
  (testing "the check only fires for :actuation/dispatch-heat -- an out-of-tolerance pedigree already on file does not block an unrelated op"
    (let [[_db actor] (fresh)
          _ (attach-ore-pedigree! actor "t5pre" "heat-1" (weak-pedigree))
          res (exec-op actor "t5" {:op :mill-rules/verify :subject "heat-1"} operator)]
      (is (= :interrupted (:status res)) "mill-rules/verify is unaffected by an out-of-tolerance upstream ore pedigree")
      (let [r2 (approve! actor "t5")]
        (is (= :commit (get-in r2 [:state :disposition])))))))
