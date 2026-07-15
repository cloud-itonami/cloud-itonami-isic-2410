(ns steelworks.store
  "SSoT for the basic-iron-steel actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/steelworks/store_contract_test.clj), which is the whole point:
  the actor, the Steelworks Governor and the audit ledger
  never know which SSoT they run on.

  Like `telecom.store`'s dual number-provisioning/billing-suppression
  history and every other dual-actuation sibling before it, this actor
  has TWO actuation events (dispatching a heat action, issuing
  class evidence) acting on the SAME entity (a heat),
  each with its OWN history collection, sequence counter and dedicated
  double-actuation-guard boolean (`:heat-dispatched?`/
  `:mill-certified?`, never a `:status` value) -- the same
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which heat was
  screened for an unresolved quality defect, which heat action was
  dispatched, which class evidence was issued, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a community trusting an steelworks
  manufacturer needs, and the evidence a manufacturer needs if a
  dispatch or mill-cert decision is later disputed.

  `:upstream-ore-pedigree` (ADR-2607999970, the THIRD applied link of
  the ADR-2607999950 cross-actor supply-chain-linkage pattern) is an
  OPTIONAL heat field -- a `kotoba.pedigree` record an upstream
  `cloud-itonami-isic-0710` iron-ore production record issued via
  `ironops.export/pedigree-for-production-record`, attached via the
  SAME general-purpose `:heat/intake`+`:patch` mechanism every other
  heat field already uses (no new op/effect needed) -- the same
  convention `autoparts.store`'s own `:upstream-pedigree` field
  already established one link earlier. Absent on every heat that
  predates this ADR; `steelworks.governor`'s new check treats its
  absence as a no-op, so this is purely additive on both backends."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [steelworks.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (heat [s id])
  (all-heats [s])
  (quality-screen-of [s heat-id] "committed quality-defect screening verdict for a heat, or nil")
  (requirements-verification-of [s heat-id] "committed requirements verification, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only block-dispatch history (steelworks.registry drafts)")
  (evidence-history [s] "the append-only mill-cert history (steelworks.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-evidence-sequence [s jurisdiction] "next evidence-number sequence for a jurisdiction")
  (heat-already-dispatched? [s heat-id] "has this heat's action already been dispatched?")
  (heat-already-certified? [s heat-id] "has this heat's class evidence already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-heats [s heats] "replace/seed the heat directory (map id->heat)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained heat set covering both actuation
  lifecycles (dispatching a heat action, issuing class
  evidence) so the actor + tests run offline."
  []
  {:heats
   {"heat-1" {:id "heat-1" :unit-name "Sakura Double-Bottom Heat DB-04"
                  :chemistry-deviation-actual 0.05 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10
                  :quality-defect-unresolved? false
                  :heat-dispatched? false :mill-certified? false
                  :jurisdiction "JPN" :status :intake}
    "heat-2" {:id "heat-2" :unit-name "Atlantis Side-Shell Heat SS-12"
                  :chemistry-deviation-actual 0.05 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10
                  :quality-defect-unresolved? false
                  :heat-dispatched? false :mill-certified? false
                  :jurisdiction "ATL" :status :intake}
    "heat-3" {:id "heat-3" :unit-name "鈴木転炉ヒート H-2407"
                  :chemistry-deviation-actual 0.35 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10
                  :quality-defect-unresolved? false
                  :heat-dispatched? false :mill-certified? false
                  :jurisdiction "JPN" :status :intake}
    "heat-4" {:id "heat-4" :unit-name "田中電炉ヒート H-2403"
                  :chemistry-deviation-actual 0.05 :chemistry-deviation-min -0.10 :chemistry-deviation-max 0.10
                  :quality-defect-unresolved? true
                  :heat-dispatched? false :mill-certified? false
                  :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-heat!
  "Backend-agnostic `:heat/mark-dispatched` -- looks up the
  heat via the protocol and drafts the heat-dispatch record,
  and returns {:result .. :heat-patch ..} for the caller to
  persist."
  [s heat-id]
  (let [a (heat s heat-id)
        seq-n (next-dispatch-sequence s (:jurisdiction a))
        result (registry/register-heat-dispatch heat-id (:jurisdiction a) seq-n)]
    {:result result
     :heat-patch {:heat-dispatched? true
                      :dispatch-number (get result "dispatch_number")}}))

(defn- issue-mill-cert!
  "Backend-agnostic `:heat/mark-certified` -- looks up the
  heat via the protocol and drafts the mill-cert
  record, and returns {:result .. :heat-patch ..} for the caller
  to persist."
  [s heat-id]
  (let [a (heat s heat-id)
        seq-n (next-evidence-sequence s (:jurisdiction a))
        result (registry/register-mill-cert heat-id (:jurisdiction a) seq-n)]
    {:result result
     :heat-patch {:mill-certified? true
                      :evidence-number (get result "evidence_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (heat [_ id] (get-in @a [:heats id]))
  (all-heats [_] (sort-by :id (vals (:heats @a))))
  (quality-screen-of [_ id] (get-in @a [:quality-screens id]))
  (requirements-verification-of [_ heat-id] (get-in @a [:verifications heat-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (evidence-history [_] (:evidences @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-evidence-sequence [_ jurisdiction] (get-in @a [:evidence-sequences jurisdiction] 0))
  (heat-already-dispatched? [_ heat-id] (boolean (get-in @a [:heats heat-id :heat-dispatched?])))
  (heat-already-certified? [_ heat-id] (boolean (get-in @a [:heats heat-id :mill-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :heat/upsert
      (swap! a update-in [:heats (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :quality-screen/set
      (swap! a assoc-in [:quality-screens (first path)] payload)

      :heat/mark-dispatched
      (let [heat-id (first path)
            {:keys [result heat-patch]} (dispatch-heat! s heat-id)
            jurisdiction (:jurisdiction (heat s heat-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:heats heat-id] merge heat-patch)
                       (update :dispatches registry/append result))))
        result)

      :heat/mark-certified
      (let [heat-id (first path)
            {:keys [result heat-patch]} (issue-mill-cert! s heat-id)
            jurisdiction (:jurisdiction (heat s heat-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:evidence-sequences jurisdiction] (fnil inc 0))
                       (update-in [:heats heat-id] merge heat-patch)
                       (update :evidences registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-heats [s heats] (when (seq heats) (swap! a assoc :heats heats)) s))

(defn seed-db
  "A MemStore seeded with the demo heat set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :quality-screens {} :ledger [] :dispatch-sequences {}
                           :dispatches [] :evidence-sequences {} :evidences []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/quality-screen payloads, ledger facts,
  dispatch/evidence records) are stored as EDN strings so `langchain.
  db` doesn't expand them into sub-entities -- the same convention
  every sibling actor's store uses."
  {:heat/id                       {:db/unique :db.unique/identity}
   :verification/heat-id          {:db/unique :db.unique/identity}
   :quality-screen/heat-id            {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :dispatch/seq                      {:db/unique :db.unique/identity}
   :evidence/seq                      {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :evidence-sequence/jurisdiction    {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- block->tx [{:keys [id unit-name chemistry-deviation-actual chemistry-deviation-min chemistry-deviation-max
                             quality-defect-unresolved?
                             heat-dispatched? mill-certified?
                             upstream-ore-pedigree
                             jurisdiction status dispatch-number evidence-number]}]
  (cond-> {:heat/id id}
    unit-name                                  (assoc :heat/unit-name unit-name)
    chemistry-deviation-actual                (assoc :heat/chemistry-deviation-actual chemistry-deviation-actual)
    chemistry-deviation-min                   (assoc :heat/chemistry-deviation-min chemistry-deviation-min)
    chemistry-deviation-max                   (assoc :heat/chemistry-deviation-max chemistry-deviation-max)
    (some? quality-defect-unresolved?)              (assoc :heat/quality-defect-unresolved? quality-defect-unresolved?)
    (some? heat-dispatched?)                (assoc :heat/heat-dispatched? heat-dispatched?)
    (some? mill-certified?)            (assoc :heat/mill-certified? mill-certified?)
    (some? upstream-ore-pedigree)               (assoc :heat/upstream-ore-pedigree (enc upstream-ore-pedigree))
    jurisdiction                                (assoc :heat/jurisdiction jurisdiction)
    status                                      (assoc :heat/status status)
    dispatch-number                             (assoc :heat/dispatch-number dispatch-number)
    evidence-number                             (assoc :heat/evidence-number evidence-number)))

(def ^:private block-pull
  [:heat/id :heat/unit-name :heat/chemistry-deviation-actual
   :heat/chemistry-deviation-min :heat/chemistry-deviation-max
   :heat/quality-defect-unresolved? :heat/heat-dispatched? :heat/mill-certified?
   :heat/upstream-ore-pedigree
   :heat/jurisdiction :heat/status :heat/dispatch-number :heat/evidence-number])

(defn- pull->heat [m]
  (when (:heat/id m)
    {:id (:heat/id m) :unit-name (:heat/unit-name m)
     :chemistry-deviation-actual (:heat/chemistry-deviation-actual m)
     :chemistry-deviation-min (:heat/chemistry-deviation-min m)
     :chemistry-deviation-max (:heat/chemistry-deviation-max m)
     :quality-defect-unresolved? (boolean (:heat/quality-defect-unresolved? m))
     :heat-dispatched? (boolean (:heat/heat-dispatched? m))
     :mill-certified? (boolean (:heat/mill-certified? m))
     :upstream-ore-pedigree (dec* (:heat/upstream-ore-pedigree m))
     :jurisdiction (:heat/jurisdiction m) :status (:heat/status m)
     :dispatch-number (:heat/dispatch-number m) :evidence-number (:heat/evidence-number m)}))

(defrecord DatomicStore [conn]
  Store
  (heat [_ id]
    (pull->heat (d/pull (d/db conn) block-pull [:heat/id id])))
  (all-heats [_]
    (->> (d/q '[:find [?id ...] :where [?e :heat/id ?id]] (d/db conn))
         (map #(pull->heat (d/pull (d/db conn) block-pull [:heat/id %])))
         (sort-by :id)))
  (quality-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :quality-screen/heat-id ?aid] [?k :quality-screen/payload ?p]]
              (d/db conn) id)))
  (requirements-verification-of [_ heat-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/heat-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) heat-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (evidence-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :evidence/seq ?s] [?e :evidence/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-evidence-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :evidence-sequence/jurisdiction ?j] [?e :evidence-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (heat-already-dispatched? [s heat-id]
    (boolean (:heat-dispatched? (heat s heat-id))))
  (heat-already-certified? [s heat-id]
    (boolean (:mill-certified? (heat s heat-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :heat/upsert
      (d/transact! conn [(block->tx value)])

      :verification/set
      (d/transact! conn [{:verification/heat-id (first path) :verification/payload (enc payload)}])

      :quality-screen/set
      (d/transact! conn [{:quality-screen/heat-id (first path) :quality-screen/payload (enc payload)}])

      :heat/mark-dispatched
      (let [heat-id (first path)
            {:keys [result heat-patch]} (dispatch-heat! s heat-id)
            jurisdiction (:jurisdiction (heat s heat-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(block->tx (assoc heat-patch :id heat-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :heat/mark-certified
      (let [heat-id (first path)
            {:keys [result heat-patch]} (issue-mill-cert! s heat-id)
            jurisdiction (:jurisdiction (heat s heat-id))
            next-n (inc (next-evidence-sequence s jurisdiction))]
        (d/transact! conn
                     [(block->tx (assoc heat-patch :id heat-id))
                      {:evidence-sequence/jurisdiction jurisdiction :evidence-sequence/next next-n}
                      {:evidence/seq (count (evidence-history s)) :evidence/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-heats [s heats]
    (when (seq heats) (d/transact! conn (mapv block->tx (vals heats)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:heats ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [heats]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-heats s heats))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo heat set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
