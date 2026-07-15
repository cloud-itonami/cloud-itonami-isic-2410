(ns steelworks.export
  "Audit-package export for social / regulatory hand-off.

  Produces plain EDN maps and CSV strings over a `steelworks.store/Store`
  snapshot -- the same append-only ledger, block-dispatch drafts and
  mill-cert drafts the governor writes. Pure data transforms only:
  no I/O, no network, no signature. The mill's own act is to sign and
  file the package; this namespace only materializes the package body.

  This is the honest delivery of the industry-stack `:export?` contract
  (robotics / audit-ledger capabilities) for ISIC 2410.

  `pedigree-for-heat` (ADR-2607999950, extended by ADR-2607999970) is
  a SEPARATE kind of export: not a social/regulatory hand-off bundle,
  but a cross-actor supply-chain-linkage record (`kotoba.pedigree`) a
  downstream actor (pilot: `cloud-itonami-isic-2930`) can
  independently re-verify. Still the same discipline as everything
  else in this ns: a pure data transform over data already on file,
  never a live network call and never an invented claim."
  (:require [clojure.string :as str]
            [kotoba.pedigree :as pedigree]
            [steelworks.store :as store]))

(defn- csv-escape [v]
  (let [s (str (if (nil? v) "" v))]
    (if (re-find #"[,\"\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [cols]
  (str/join "," (map csv-escape cols)))

(defn ledger-rows
  "Normalize ledger facts into flat row maps suitable for CSV."
  [st]
  (mapv (fn [i f]
          {:seq i
           :t (:t f)
           :op (str (:op f))
           :actor (:actor f)
           :subject (:subject f)
           :disposition (str (:disposition f))
           :basis (pr-str (:basis f))
           :summary (:summary f)})
        (range)
        (store/ledger st)))

(defn dispatch-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :heat_id (get r "heat_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/dispatch-history st)))

(defn evidence-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :heat_id (get r "heat_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/evidence-history st)))

(defn heats-snapshot [st]
  (mapv (fn [b]
          (select-keys b [:id :unit-name :jurisdiction :status
                          :chemistry-deviation-actual
                          :chemistry-deviation-min
                          :chemistry-deviation-max
                          :quality-defect-unresolved?
                          :heat-dispatched?
                          :mill-certified?
                          :dispatch-number
                          :evidence-number]))
        (store/all-heats st)))

(defn audit-package
  "Full audit package for a store snapshot -- the body a steelworks would
  hand to mill inspectors, standards-body inspectors or internal compliance.
  `:format` is always `:edn-maps` for the nested package; use
  `package->csv-bundle` for CSV strings."
  [st]
  {:isic "2410"
   :business-id "cloud-itonami-isic-2410"
   :format :edn-maps
   :heats (heats-snapshot st)
   :ledger (vec (store/ledger st))
   :dispatches (vec (store/dispatch-history st))
   :mill-cert (vec (store/evidence-history st))
   :counts {:heats (count (store/all-heats st))
            :ledger (count (store/ledger st))
            :dispatches (count (store/dispatch-history st))
            :mill-cert (count (store/evidence-history st))}})

(defn rows->csv
  "Render a seq of flat maps as CSV using `header` column order."
  [header rows]
  (let [lines (into [(csv-row (map name header))]
                    (map (fn [r] (csv-row (map #(get r %) header))) rows))]
    (str (str/join "\n" lines) (when (seq lines) "\n"))))

(defn package->csv-bundle
  "CSV bundle for spreadsheet hand-off. Keys are filenames; values are
  CSV body strings."
  [st]
  {"heats.csv" (rows->csv [:id :unit-name :jurisdiction :status
                            :chemistry-deviation-actual
                            :heat-dispatched? :mill-certified?
                            :dispatch-number :evidence-number]
                           (heats-snapshot st))
   "ledger.csv" (rows->csv [:seq :t :op :actor :subject :disposition :basis :summary]
                           (ledger-rows st))
   "dispatches.csv" (rows->csv [:seq :record_id :kind :heat_id :jurisdiction]
                               (dispatch-rows st))
   "mill-cert.csv" (rows->csv [:seq :record_id :kind :heat_id :jurisdiction]
                                   (evidence-rows st))})

#?(:clj
(defn write-csv-bundle!
  "Write `package->csv-bundle` files under `dir` (created if missing).
  Returns the absolute path of `dir`. JVM-only I/O seam for social
  hand-off scripts; pure package construction stays in `package->csv-bundle`."
  [st dir]
  (let [d (java.io.File. (str dir))
        _ (.mkdirs d)
        bundle (package->csv-bundle st)]
    (doseq [[name body] bundle]
      (spit (java.io.File. d (str name)) body))
    (.getAbsolutePath d))))

;; ---------------------------------------------------------------------------
;; Cross-actor supply-chain-linkage export (ADR-2607999950)
;; ---------------------------------------------------------------------------

(defn pedigree-for-heat
  "Builds a `kotoba.pedigree` record (a material-pedigree/mill-
  certificate-of-conformance-equivalent EDN interchange record --
  ADR-2607999950's cross-actor supply-chain-linkage pilot,
  isic-2410 -> isic-2930) for `heat`, a heat record that ALREADY
  carries its own real, already-simulated tensile-test telemetry on
  file (`:sim-tensile-load-n`, from `steelworks.robotics/tensile-
  test-telemetry-for` -- ADR-2607999600's real `physics-2d` time-
  stepped steel-coupon tensile-test simulation). This fn does NOT run
  that simulation itself -- it only packages a reading already on the
  heat map, mirroring how every other fn in this ns only ever
  materializes a package body over data already on file, never
  computes new evidence.

  `issued-at` (an ISO date string) is a caller-supplied argument, not
  a wall-clock read -- this fn stays pure/deterministic, the same
  discipline `kotoba.robotics/telemetry-proof`'s caller-supplied
  `:timestamp` already establishes.

  `:pedigree/claims` reports `:tensile-test-load-n` -- a FORCE
  reading in Newtons, honestly named: `steelworks.robotics`'s
  simulation derives a peak tensile LOAD (mass x deceleration), not a
  stress in MPa -- this actor has no cross-sectional-area/stress
  model at all (see `steelworks.robotics` ns docstring), so reporting
  a stress figure here would require an INVENTED area; a claim this
  library's own docstring forbids. `:pedigree/evidence-basis` cites
  the real simulation function that derived the reading, never a
  self-reported checklist string.

  Returns nil (never a fabricated pedigree) when `heat` carries no
  real `:sim-tensile-load-n` on file -- the SAME disclosed 'missing
  telemetry != inventable' discipline `steelworks.robotics` ns
  docstring / `simulation-out-of-tolerance?` already establish.

  Genuine 3-hop chaining (ADR-2607999970, the third applied link):
  when `heat` itself already carries an `:upstream-ore-pedigree` (a
  `kotoba.pedigree` record an upstream `cloud-itonami-isic-0710`
  iron-ore production record issued via `ironops.export/pedigree-
  for-production-record`, and this actor's OWN governor
  independently re-verified before ever letting the heat dispatch --
  see `steelworks.governor`'s `upstream-ore-pedigree-claims-out-of-
  tolerance-violations`), it is embedded here as `:pedigree/upstream`
  (`kotoba.pedigree/claim`'s `:upstream` option, ADR-2607999960),
  producing a genuine THREE-hop provenance chain (iron ore -> steel
  heat -> ...) `cloud-itonami-isic-2930`'s own governor can
  independently re-verify shape-wise via `kotoba.pedigree/valid?`'s
  recursive check -- never a bare id the receiver has to go look up,
  and never a second network fetch. When `heat` carries no
  `:upstream-ore-pedigree`, `:pedigree/upstream` is simply omitted --
  a single-hop heat pedigree is unaffected by this option's
  existence, the exact same additive shape `autoparts.export/
  pedigree-for-part-lot` already established for ITS OWN
  `:upstream-pedigree` one link earlier."
  [{:keys [id sim-tensile-load-n upstream-ore-pedigree]} issued-at]
  (when (and id (number? sim-tensile-load-n))
    (pedigree/claim
     (str "PEDIGREE-" id) id "cloud-itonami-isic-2410"
     {:tensile-test-load-n sim-tensile-load-n}
     :evidence-basis ["steelworks.robotics/run-tensile-test (physics-2d time-stepped rigid-body simulation, ASTM A370 / ISO 6892 steel-coupon tensile-test reinterpretation -- see ns docstring)"]
     :issued-at issued-at
     :upstream upstream-ore-pedigree)))
