(ns steelworks.registry
  "Pure-function block-dispatch + mill-cert record
  construction -- an append-only steelworks book-of-record
  draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for an block-dispatch or
  mill-cert reference number -- every manufacturer/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `steelworks.facts` uses.

  `heat-chemistry-out-of-range?` is the FOURTH instance of this
  fleet's two-sided range check family (`testlab.registry/within-
  tolerance?` established the first, `conservation.registry/body-
  condition-out-of-range?` the second, `water.registry/contaminant-
  level-out-of-range?` the third), applying the SAME lo/hi bounds-
  comparison shape to a heat's own measured dimensional
  tolerance against the heat's own recorded spec bounds.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real fab/assembly-line control system. It builds the
  RECORD a manufacturer would keep, not the act of dispatching the
  robot heat action or issuing the class evidence itself
  (that is `steelworks.operation`'s `:actuation/dispatch-heat`/
  `:actuation/issue-mill-cert`, always human-gated -- see
  README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  manufacturer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn heat-chemistry-out-of-range?
  "Does `heat`'s own `:chemistry-deviation-actual` fall outside
  its own `[:chemistry-deviation-min :chemistry-deviation-max]`
  recorded spec-bounds? A pure ground-truth check against the
  block's own permanent fields -- no upstream comparison needed.
  The FOURTH instance of this fleet's two-sided range check family
  (see ns docstring)."
  [{:keys [chemistry-deviation-actual chemistry-deviation-min chemistry-deviation-max]}]
  (and (number? chemistry-deviation-actual) (number? chemistry-deviation-min) (number? chemistry-deviation-max)
       (or (< chemistry-deviation-actual chemistry-deviation-min)
           (> chemistry-deviation-actual chemistry-deviation-max))))

(defn register-heat-dispatch
  "Validate + construct the ASSEMBLY-DISPATCH registration DRAFT --
  the manufacturer's own act of dispatching a real robot fastening/
  layup/quality action to complete an steel heat. Pure function --
  does not touch any real fab/assembly-line control system; it builds
  the RECORD a manufacturer would keep. `steelworks.governor`
  independently re-verifies the heat's own dimensional-tolerance
  sufficiency against its own spec bounds, and heats a double-
  dispatch for the same heat, before this is ever allowed to
  commit."
  [heat-id jurisdiction sequence]
  (when-not (and heat-id (not= heat-id ""))
    (throw (ex-info "block-dispatch: heat_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "block-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "block-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-HET-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "heat-dispatch-draft"
                "heat_id" heat-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "HeatDispatch" dispatch-number dispatch-number)}))

(defn register-mill-cert
  "Validate + construct the AIRWORTHINESS-EVIDENCE registration DRAFT
  -- the manufacturer's own act of issuing real class evidence
  certifying a heat as mill-cert-worthy. Pure function -- does not
  touch any real fab/assembly-line control system; it builds the
  RECORD a manufacturer would keep. `steelworks.governor` independently
  re-verifies the heat's own quality-defect resolution status, and
  heats a double-issuance for the same heat, before this is ever
  allowed to commit."
  [heat-id jurisdiction sequence]
  (when-not (and heat-id (not= heat-id ""))
    (throw (ex-info "mill-cert: heat_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "mill-cert: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "mill-cert: sequence must be >= 0" {})))
  (let [evidence-number (str (str/upper-case jurisdiction) "-MIL-" (zero-pad sequence 6))
        record {"record_id" evidence-number
                "kind" "mill-cert-draft"
                "heat_id" heat-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "evidence_number" evidence-number
     "certificate" (unsigned-certificate "MillCert" evidence-number evidence-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
