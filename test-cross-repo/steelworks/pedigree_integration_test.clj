(ns steelworks.pedigree-integration-test
  "ADR-2607999970's critical end-to-end proof: a GENUINE cross-repo
  call into `cloud-itonami-isic-0710`'s OWN `ironops.store`/`ironops.
  export` -- never a hand-written EDN literal that merely mimics what
  those functions would produce. `real-upstream-ore-pedigree` below
  actually writes a production record into a real `ironops.store`
  MemStore, actually reads it back out, and calls `ironops.export/
  pedigree-for-production-record` (both required from `cloud-itonami-
  isic-0710`'s own source, via this repo's `:cross-repo-test` alias --
  see deps.edn) to produce the ore pedigree this actor's governor then
  independently re-verifies, and (the genuine 3-hop proof) that
  `steelworks.export/pedigree-for-heat` then embeds as its OWN
  pedigree's `:pedigree/upstream`.

  Run with `clojure -M:dev:cross-repo-test` -- kept OUT of the default
  `:test` alias (this file lives in `test-cross-repo/`, a separate
  source root) because it requires a same-org sibling checkout of
  `cloud-itonami-isic-0710` (`:local/root \"../cloud-itonami-isic-
  0710\"`, the SAME workspace-sibling convention this repo's own
  `io.github.kotoba-lang/langgraph`/`robotics` deps already use one
  org level up, and `cloud-itonami-isic-2930`'s own `:cross-repo-test`
  alias already established one link earlier in this chain) that a
  casual fork of just THIS repo would not have. Still no live network
  call between actors at runtime: this is a build-time classpath
  dependency exercised by tests, same category as every other
  `:local/root` dependency in this fleet."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [ironops.export :as ore-export]
            [ironops.store :as ore-store]
            [steelworks.export :as export]
            [steelworks.robotics :as robotics]
            [steelworks.governor :as governor]
            [steelworks.store :as store]
            [steelworks.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :mill-metallurgist :phase 3})

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

(defn- real-upstream-ore-pedigree
  "THE genuine cross-repo call: writes a real production record into
  a real `cloud-itonami-isic-0710` `ironops.store` MemStore, reads it
  back out via that repo's OWN store protocol, and packages it via
  that repo's OWN `ironops.export/pedigree-for-production-record` --
  never a hand-typed EDN literal."
  [record-id site-id grade-actual quantity-tonnes issued-at]
  (let [st (ore-store/mem-store)
        st' (ore-store/add-production-record st record-id
                                              {:site-id site-id
                                               :grade-actual grade-actual
                                               :grade-min 0.0 :grade-max 100.0
                                               :quantity-tonnes quantity-tonnes})
        rec (ore-store/production-record st' record-id)]
    (ore-export/pedigree-for-production-record rec issued-at)))

(deftest real-cross-repo-ore-pedigree-is-shape-valid
  (testing "a pedigree built from a REAL cloud-itonami-isic-0710 store round-trip passes kotoba.pedigree/valid?"
    (let [p (real-upstream-ore-pedigree "prod-strong" "iron-site-001" 65.0 6000.0 "2026-07-16")]
      (is (some? p))
      (is (true? (pedigree/valid? p)))
      (is (= "cloud-itonami-isic-0710" (:pedigree/issuing-actor p)))
      (testing "the claim is the REAL recorded reading, not invented -- independently re-reading the same production record yields the identical number"
        (is (= 65.0 (pedigree/claim-value p :grade-actual))
            "documents the actual real-record value, for a human reader's sanity")))))

(deftest genuine-3-hop-chain-is-shape-valid-end-to-end
  (testing "a heat pedigree built with a REAL embedded upstream ore pedigree (both hops genuine cross-repo/cross-store calls) is shape-valid end-to-end, and the chain is genuinely 2 levels deep from this repo's own vantage point (ore -> heat)"
    (let [ore (real-upstream-ore-pedigree "prod-strong" "iron-site-001" 65.0 6000.0 "2026-07-16")
          heat (merge {:id "heat-strong" :coupon-mass-kg 5.0 :upstream-ore-pedigree ore}
                      (robotics/tensile-test-telemetry-for {:coupon-mass-kg 5.0}))
          heat-pedigree (export/pedigree-for-heat heat "2026-07-16")]
      (is (true? (pedigree/valid? ore)))
      (is (true? (pedigree/valid? heat-pedigree)))
      (is (= ore (:pedigree/upstream heat-pedigree))
          "the REAL isic-0710 pedigree is embedded verbatim, not summarized/flattened")
      (testing "each hop's own claim stays independently readable"
        (is (= 65.0 (pedigree/claim-value (:pedigree/upstream heat-pedigree) :grade-actual)))
        (is (= (:sim-tensile-load-n heat) (pedigree/claim-value heat-pedigree :tensile-test-load-n)))))))

(deftest real-cross-repo-ore-pedigree-genuinely-clears-steelworks-governor
  (testing "a rich-enough real ore production record's pedigree genuinely clears steelworks.governor's independent acceptance check end-to-end, and a real heat dispatches"
    (let [ore-pedigree (real-upstream-ore-pedigree "prod-strong" "iron-site-001" 65.0 6000.0 "2026-07-16")
          _ (is (>= (pedigree/claim-value ore-pedigree :grade-actual) governor/min-upstream-ore-grade-pct)
                "sanity: this record's REAL recorded grade actually clears steelworks' own disclosed floor")
          db (store/seed-db)
          actor (op/build db)
          _ (store/commit-record! db {:effect :heat/upsert :value {:id "heat-1" :coupon-mass-kg 5.0}})]
      (verify! actor "e1pre" "heat-1")
      (attach-ore-pedigree! actor "e1pre2" "heat-1" ore-pedigree)
      (is (= ore-pedigree (:upstream-ore-pedigree (store/heat db "heat-1")))
          "the REAL cross-repo ore pedigree landed on the heat record unmodified")
      (let [res (exec-op actor "e1" {:op :actuation/dispatch-heat :subject "heat-1"} operator)]
        (is (= :interrupted (:status res))
            "governor's independent re-verification found no violation from the real ore pedigree -- escalates for human approval, same as any clean dispatch")
        (let [r2 (approve! actor "e1")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:heat-dispatched? (store/heat db "heat-1")))))))))

(deftest real-cross-repo-ore-pedigree-genuinely-fails-steelworks-governor
  (testing "a too-lean real ore production record's pedigree genuinely fails steelworks.governor's independent acceptance check end-to-end -- HARD hold, derived from a REAL cloud-itonami-isic-0710 store round-trip, never a hand-crafted failing fixture"
    (let [ore-pedigree (real-upstream-ore-pedigree "prod-weak" "iron-site-002" 40.0 800.0 "2026-07-16")
          _ (is (< (pedigree/claim-value ore-pedigree :grade-actual) governor/min-upstream-ore-grade-pct)
                "sanity: this record's REAL recorded grade actually falls short of steelworks' own disclosed floor")
          db (store/seed-db)
          actor (op/build db)
          _ (store/commit-record! db {:effect :heat/upsert :value {:id "heat-1" :coupon-mass-kg 5.0}})]
      (verify! actor "e2pre" "heat-1")
      (attach-ore-pedigree! actor "e2pre2" "heat-1" ore-pedigree)
      (let [res (exec-op actor "e2" {:op :actuation/dispatch-heat :subject "heat-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:upstream-ore-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
        (is (empty? (store/dispatch-history db)))))))
