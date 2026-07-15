(ns steelworks.governor
  "Steelworks Governor -- the independent compliance layer
  that earns the Steelworks Advisor the right to commit. The LLM has no
  notion of mill-rules law, whether a heat's own
  measured dimensional tolerance actually stays within its own
  recorded spec bounds, whether an quality-detected defect against the
  heat has actually stayed unresolved, or when an act stops being
  a draft and becomes a real-world robot heat dispatch or
  mill-cert issuance, so this MUST be a separate system
  able to *reject* a proposal and fall back to HOLD -- the steelworks-
  manufacturer analog of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated class spec-basis, incomplete evidence, a robot
  tensile-test simulation that independently re-checks out-of-
  tolerance, an out-of-spec heat, an unresolved quality defect, or a
  double dispatch/evidence-issuance). The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `steelworks.phase`: for `:stake :actuation/
  dispatch-assembly`/`:actuation/issue-mill-cert` (a real
  safety-critical act) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the requirements proposal cite
                                       an OFFICIAL source (`steelworks.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/dispatch-
                                       assembly`/`:actuation/issue-
                                       mill-cert`, has the
                                       heat actually been verified
                                       with a full CAE-simulation-
                                       report/CFD-verification-report/
                                       quality-chain-of-custody-record/
                                       material-certification-record
                                       evidence checklist on file?
    3. Tensile-test load
       independently out of
       tolerance                     -- for `:actuation/dispatch-heat`
                                       (ADR-2607999600): INDEPENDENTLY
                                       recompute, FRESH every call,
                                       whether the heat's own recorded
                                       `:coupon-mass-kg` yields a REAL
                                       `physics-2d`-simulated peak
                                       tensile load (`steelworks.
                                       robotics/run-tensile-test`, a
                                       genuine time-stepped rigid-body
                                       simulation of an ASTM A370/ISO
                                       6892 steel-coupon tensile test)
                                       below the real disclosed minimum
                                       required tensile load
                                       (`steelworks.robotics/
                                       simulation-out-of-tolerance?`) --
                                       never trusts any self-reported
                                       checklist string for the
                                       material-certification-record's
                                       mechanical-property half. An
                                       unrelated QA domain (mechanical
                                       tensile-load qualification) to
                                       check 4 below (chemical
                                       composition), folded into its
                                       OWN HARD check, ADDITIONAL to
                                       every other check -- never
                                       replacing any of them. A heat
                                       with no `:coupon-mass-kg` on
                                       file never triggers this check
                                       (missing telemetry != violation,
                                       see `steelworks.robotics` ns
                                       docstring).
    4. Heat tolerance out of
       range                         -- for `:actuation/dispatch-
                                       assembly`, INDEPENDENTLY
                                       recompute whether the
                                       block's own measured
                                       dimensional tolerance falls
                                       outside its own recorded spec
                                       bounds (`steelworks.registry/
                                       assembly-tolerance-out-of-
                                       range?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all. The FOURTH
                                       instance of this fleet's two-
                                       sided range check family
                                       (`testlab.governor/within-
                                       tolerance-violations`/
                                       `conservation.governor/body-
                                       condition-out-of-range-
                                       violations`/`water.governor/
                                       contaminant-level-out-of-range-
                                       violations` established the
                                       first three).
    5. quality defect unresolved        -- reported by THIS proposal itself
                                       (an `:quality/screen` that just
                                       found an unresolved defect), or
                                       already on file for the
                                       heat (`:quality/screen`/
                                       `:actuation/issue-class-
                                       evidence`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (twenty-six prior siblings)...
                                       established -- the TWENTY-
                                       SEVENTH distinct application of
                                       this exact discipline, and the
                                       FIRST specifically for an quality-
                                       defect concept. Like the
                                       sixteen most recent siblings'
                                       equivalent checks, this is
                                       exercised in tests/demo via
                                       `:quality/screen` DIRECTLY, not via
                                       an actuation op against an
                                       unscreened heat -- see this
                                       ns's own test suite.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       dispatch-assembly`/`:actuation/
                                       issue-mill-cert`
                                       (REAL safety-critical acts) ->
                                       escalate.

  Two more guards, double-dispatch/double-evidence-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-dispatched-violations`/`already-certified-violations`
  refuse to dispatch a heat action/issue class evidence
  for the SAME heat twice, off dedicated `:heat-dispatched?`/
  `:mill-certified?` facts (never a `:status` value) -- the
  SAME 'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-
  isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [steelworks.facts :as facts]
            [steelworks.registry :as registry]
            [steelworks.robotics :as robotics]
            [steelworks.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real robot heat action on a process-critical
  structure and issuing real class evidence are the two real-
  world actuation events this actor performs -- a two-member set,
  matching every prior dual-actuation sibling's shape."
  #{:actuation/dispatch-heat :actuation/issue-mill-cert})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:mill-rules/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  mill-rules requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:mill-rules/verify :actuation/dispatch-heat :actuation/issue-mill-cert} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案はミル規格要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/dispatch-heat`/`:actuation/issue-class-
  evidence`, the jurisdiction's required CAE-simulation-report/CFD-
  verification-report/quality-chain-of-custody-record/material-
  certification-record evidence must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/dispatch-heat :actuation/issue-mill-cert} op)
    (let [a (store/heat st subject)
          verification (store/requirements-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(CAEシミュレーション報告書/CFD検証報告書/品質検査連鎖記録/材料証明記録等)が充足していない状態での提案"}]))))

(defn- tensile-test-out-of-tolerance-violations
  "For `:actuation/dispatch-heat` (ADR-2607999600): INDEPENDENTLY
  recompute -- FRESH, every call, never a previously stored/
  self-reported value -- whether the heat's own recorded
  `:coupon-mass-kg` yields a REAL `physics-2d`-simulated peak tensile
  load (`steelworks.robotics/run-tensile-test`, a genuine time-stepped
  rigid-body simulation of an ASTM A370/ISO 6892 steel-coupon tensile
  test) below the real disclosed minimum required tensile load
  (`steelworks.robotics/simulation-out-of-tolerance?`). An unrelated QA
  domain (mechanical tensile-load qualification) to `heat-chemistry-
  out-of-range-violations` below (chemical composition), folded into
  its OWN HARD check, ADDITIONAL to every existing check -- never
  replacing any of them. A heat with no `:coupon-mass-kg` on file (no
  tensile-test coupon data yet) never triggers this check -- missing
  telemetry is never silently treated as a violation, see
  `steelworks.robotics` ns docstring."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-heat)
    (let [a (store/heat st subject)]
      (when (robotics/simulation-out-of-tolerance? a)
        [{:rule :tensile-test-out-of-tolerance
          :detail (str subject " の実測引張荷重が独立再検証で許容下限("
                       robotics/min-tensile-load-n "N)を下回る")}]))))

(defn- heat-chemistry-out-of-range-violations
  "For `:actuation/dispatch-heat`, INDEPENDENTLY recompute whether
  the heat's own dimensional tolerance falls outside its own
  recorded spec bounds via `steelworks.registry/assembly-tolerance-
  out-of-range?` -- needs no proposal inspection or stored-verdict
  lookup at all, since its inputs are permanent ground-truth fields
  already on the heat."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-heat)
    (let [a (store/heat st subject)]
      (when (registry/heat-chemistry-out-of-range? a)
        [{:rule :heat-chemistry-out-of-range
          :detail (str subject " の実測公差(" (:chemistry-deviation-actual a)
                      ")が仕様範囲[" (:chemistry-deviation-min a) "," (:chemistry-deviation-max a) "]を逸脱")}]))))

(defn- quality-defect-unresolved-violations
  "An unresolved quality-detected defect -- reported by THIS proposal (e.g.
  an `:quality/screen` that itself just found one), or already on file in
  the store for the heat (`:quality/screen`/`:actuation/issue-
  mill-cert`) -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        heat-id (when (contains? #{:quality/screen :actuation/issue-mill-cert} op) subject)
        hit-on-file? (and heat-id (= :unresolved (:verdict (store/quality-screen-of st heat-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :quality-defect-unresolved
        :detail "未解決の品質検査欠陥がある状態でのミル規格証拠発行提案は進められない"}])))

(defn- already-dispatched-violations
  "For `:actuation/dispatch-heat`, refuses to dispatch a heat
  action for the SAME heat twice, off a dedicated `:assembly-
  dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-heat)
    (when (store/heat-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既にブロック実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-mill-cert`, refuses to issue
  class evidence for the SAME heat twice, off a dedicated
  `:mill-certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-mill-cert)
    (when (store/heat-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既にミル規格証拠発行済み")}])))

(defn check
  "Censors an Steelworks Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (tensile-test-out-of-tolerance-violations request st)
                           (heat-chemistry-out-of-range-violations request st)
                           (quality-defect-unresolved-violations request proposal st)
                           (already-dispatched-violations request st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
