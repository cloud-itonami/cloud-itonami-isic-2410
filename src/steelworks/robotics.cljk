(ns steelworks.robotics
  "Robot-executed steel-coupon tensile-test verification -- the
  concrete, actor-level realization of ADR-2607011000's robotics
  premise and ADR-2607142800's fleet-wide robotics-process-simulation
  pattern (established by `cloud-itonami-isic-2910`'s `automotive.
  robotics`, extended to a design-library-less weld/fastener pull test
  by `autoparts.robotics`/ADR-2607152000, and to a design-library-less
  direct-approach test by `deviceassembly.robotics`/ADR-2607991500),
  applied here (ADR-2607999600) to THIS actor's own `steelworks.facts`
  material-certification-record evidence field: a real steel mill's
  material certificate (a mill test certificate, EN 10204 3.1-class /
  ASTM A370-class) is not just a chemistry-analysis report -- it ALSO
  reports the heat's own tested MECHANICAL properties (yield strength,
  ultimate tensile strength, elongation). Until this ns existed,
  `material-certification-record` in `steelworks.facts`' evidence
  checklist could only ever be satisfied by a SELF-REPORTED checklist
  string (the advisor claiming the document is on file); this ns
  closes the mechanical-property half of that gap with an actual
  physics-derived reading, ADDITIVE alongside (never replacing) the
  existing self-reported checklist gate and the existing
  `steelworks.registry/heat-chemistry-out-of-range?` ground-truth
  check (an unrelated QA domain: mechanical tensile-load qualification
  vs. chemical composition).

  A genuine, real, extremely standard steel-mill QA procedure: the
  STEEL COUPON TENSILE TEST (ASTM A370 / ISO 6892). A machined test
  coupon cut from the heat is gripped at both ends and pulled apart at
  a controlled rate until its ultimate tensile load is reached; that
  peak load is compared against the heat's own required minimum
  tensile-load spec. This vertical has no design-library sibling repo
  (unlike automotive's `kami-engine-vehicle-designer` pairing), so the
  physics module is built DIRECTLY in this ns, taking a real
  git-coordinate dependency on `kotoba-lang/physics-2d` alone (see
  deps.edn) -- the same shape `autoparts.robotics`'s weld-joint/
  fastener pull test and `deviceassembly.robotics`'s connector-mating
  test already established for a design-library-less vertical.

  HONEST REINTERPRETATION TECHNIQUE (mirrors `autoparts.robotics`'s
  disclosed 'reaching end-of-tether, not literally crashing into a
  barrier' trick, the SAME technique a tensile test needs): `physics-
  2d`'s `world-step` ONLY natively resolves bodies that are
  APPROACHING/colliding -- it has no notion of a body SEPARATING under
  tension, so there is no direct way to simulate 'pull the coupon
  apart until its load peaks' with this engine's collision-only
  impulse resolver. This ns reframes the SAME physical event as an
  approach instead: a `:jaw` (the moving test-rig grip) starts right
  beside a `:fixture` (a static body anchoring the coupon's OTHER grip
  end) and moves steadily AWAY from it at a real, controlled
  crosshead-equivalent pull rate -- but a THIRD, static
  `:limit-boundary` body is placed exactly `travel-to-peak-load-m` (the
  coupon's own real plastic-extension distance to reach its ultimate
  tensile load, before necking/fracture) beyond the jaw's start. As the
  jaw travels, it is really the COUPON running out of extensibility
  before its ultimate load is reached -- `physics-2d` only knows how to
  render that as the jaw's leading face reaching the limit-boundary's
  near face, at which point its native inelastic (restitution 0)
  collision resolution zeroes the jaw's velocity in a SINGLE tick --
  exactly the 'load rises, then peaks/arrests' event a real tensile
  test exhibits at its ultimate-load point. The peak deceleration read
  off that tick, times the heat's own recorded effective participating
  mass (`:coupon-mass-kg` -- the moving jaw + the locally-engaged
  coupon gauge-length material, the SAME 'effective participating
  mass' framing `autoparts.robotics`'s `:joint-mass-kg` uses), is
  `:sim-tensile-load-n` (Newtons) -- REAL, derived from the actual
  simulated trajectory, never invented.

  Disclosed engineering priors (this ns's own, not measured facts --
  same discipline as `autoparts.robotics`'s pull-test constants):

  - `test-speed-mps` models a genuine, established test category --
    high-strain-rate/dynamic tensile-property qualification of
    structural steel (per the ISO 26203 family: dynamic tensile
    testing of steel sheet, used where a grade's tensile properties
    must be characterized at crash/seismic-relevant loading rates),
    run at a representative low-single-digit m/s rate -- NOT the
    mm/min quasi-static crosshead speed ASTM A370's own baseline
    method uses. The SAME honest disclosure `autoparts.robotics`'s
    `test-speed-mps` makes for its own weld/fastener pull test applies
    here identically: this single-tick 'boxcar' technique can only
    honestly render a meaningful force reading at a genuinely fast/
    dynamic rate (peak-decel = test-speed^2 / travel-to-peak-load
    scales with the SQUARE of speed, so a slow quasi-static rate is
    the wrong physical regime for a discrete-collision technique).
  - `travel-to-peak-load-m` is a representative low-single-digit-
    millimeter plastic-extension distance a standard sub-size ASTM
    A370/ISO 6892 tensile coupon (short gauge length, e.g. 50 mm)
    travels before reaching its ultimate tensile load -- a real,
    disclosed order of magnitude (mild/structural steel commonly
    reaches UTS at roughly 8-15% engineering strain on a 50 mm gauge
    length, i.e. low-single-digit mm of extension).
  - `initial-grip-slack-m` is a small, real, disclosed test-fixture
    grip-seating/alignment slack the jaw travels BEFORE the coupon
    itself begins to bear load -- present only so the simulated
    trajectory captures a real pre-load approach phase, not just the
    single stopping tick (mirrors `autoparts.robotics`'s
    `initial-grip-slack-m` / `deviceassembly.robotics`'s
    `initial-approach-slack-m`).
  - `min-tensile-load-n` is a newly-defined, clearly-disclosed
    real-world floor (the SAME allowance ADR-2607152000 gave
    `autoparts.robotics/min-proof-load-n`, applied here to the same
    kind of reading) -- a plausible minimum acceptable peak tensile
    load for a standard sub-size structural/mild-steel test coupon
    (cross-sections on the order of tens of mm^2, minimum required
    tensile strength on the order of 400 MPa for a common basic
    structural steel grade), placing the floor in the low-single-digit
    kN range -- NOT a literal transcription of one specific named
    standard's exact figure for one specific coupon geometry/grade.

  Like `autoparts.robotics`'s weld/fastener proof load, the quantity
  reported HERE is a FORCE (Newtons), so `:coupon-mass-kg` DOES
  directly scale `:sim-tensile-load-n` (force = mass x deceleration)
  -- intentional, not an oversight: a real load-cell reading
  legitimately depends on the physical scale of the coupon/fixture
  under test, not an accident of chosen units.

  `tensile-test-out-of-tolerance?` is a pure comparator: it reads
  `:sim-tensile-load-n` off whatever map it is given (mirrors
  `autoparts.robotics/proof-load-out-of-tolerance?`). `simulation-out-
  of-tolerance?` is the governor-facing entry point -- UNLIKE
  `autoparts.robotics`'s same-named fn (which reads a PREVIOUSLY
  mission-run-computed field already stored on the part-lot), this ns's
  `simulation-out-of-tolerance?` recomputes the REAL simulation FRESH
  from the heat's own permanent `:coupon-mass-kg` field on EVERY call
  -- this actor has no separate robot-mission-run/store-write step
  wired into `steelworks.operation` yet (`simulate-tensile-test-cell`
  below exists for API parity with sibling actors' robotics namespaces
  and future advisor wiring), so `steelworks.governor` calls this
  always-fresh recompute directly, needing no proposal inspection or
  stored-verdict lookup at all -- the SAME shape `steelworks.registry/
  heat-chemistry-out-of-range?` already established for this actor,
  extended to a physics-derived reading. A heat with no
  `:coupon-mass-kg` on file (no tensile-test coupon data yet) is NEVER
  silently treated as a violation -- the same disclosed 'missing
  telemetry != violation' discipline `deviceassembly.robotics/
  connector-mating-force-out-of-tolerance?` establishes.

  Pure data + pure functions -- no real robot I/O, no network.
  `physics-2d/world-step` is itself a pure, fixed-timestep integrator
  (no wall-clock/IO), so this stays exactly as offline/deterministic as
  every other sibling namespace in this fleet -- tests run without a
  network.

  Honest scope (mirrors `autoparts.robotics`/`deviceassembly.
  robotics`): this DOES model a real time-stepped `physics-2d`
  rigid-body trajectory for the tensile-test event. It does NOT model:
  the coupon's own material/stiffness (`physics-2d` has no
  force-deflection/spring model at all -- the coupon's own plastic
  'give' is encoded purely as a travel DISTANCE, not a stress-strain
  curve), 3D geometry (2D projection only, the same disclosed limit
  every sibling states), a real load-cell/extensometer/DAQ connection,
  or a real robot controller -- still simulation, not control, the
  same 'policy, not control' boundary `kotoba.robotics`'s docstring
  already establishes."
  (:require [kotoba.robotics :as robotics]
            [physics-2d :as p2d]))

;; ---------------------------------------------------------------------------
;; Platform shims (mirrors physics-2d's own private sqrt*/abs*/signum* style
;; and `autoparts.robotics`'s/`deviceassembly.robotics`'s identical shims,
;; keeping this ns portable .cljc -- a raw Math/ceil + Math/abs would be
;; JVM-only and break a ClojureScript consumer).
;; ---------------------------------------------------------------------------

(defn- abs* [x] (if (neg? x) (- x) x))

(defn- ceil* [x]
  #?(:clj  (Math/ceil (double x))
     :cljs (js/Math.ceil x)))

(def mission-actions
  "The three-step coupon-prep/grip/pull verification mission a heat
  walks through for tensile-test qualification. All :sense/:actuate at
  :none/:low safety -- coupon-machining/grip-seating/tensile-pull QA
  sensing on a stationary test coupon, not the heat-dispatch actuation
  that is `:actuation/dispatch-heat` itself (always :safety-critical --
  see `steelworks.governor`)."
  [{:step :coupon-machining-dimensional-check :kind :sense   :safety :none}
   {:step :grip-seating-check                 :kind :actuate :safety :low}
   {:step :tensile-pull-test                  :kind :actuate :safety :low}])

;; ---------------------- real tensile-test physics constants -----------------

(def ^:const test-speed-mps
  "Controlled jaw pull-rate (m/s) -- see ns docstring: a representative
  dynamic/high-rate tensile-property test speed for structural steel
  (ISO 26203-class dynamic tensile qualification), not a literal
  quasi-static crosshead mm/min transcription (which this single-tick
  'boxcar' technique cannot honestly render as a meaningful force
  reading -- see docstring)."
  2.0)

(def ^:const travel-to-peak-load-m
  "The coupon's own real plastic-extension distance (m) to reach its
  ultimate tensile load, before necking/fracture -- see ns docstring: a
  representative low-single-digit-millimeter prior for a standard
  sub-size 50 mm gauge-length tensile coupon."
  0.0025)

(def ^:const initial-grip-slack-m
  "Test-fixture grip-seating/alignment slack (m) the jaw travels before
  the coupon itself begins to bear load -- present only so the
  trajectory captures a real pre-load approach phase, mirroring
  `autoparts.robotics`'s `initial-grip-slack-m`."
  0.0005)

(def ^:const jaw-half-w-m
  "Jaw AABB half-width along the pull axis (m) -- a small, fixed
  test-rig-grip-scale footprint, not a per-heat CAD input (this ns has
  no CAD/BREP pipeline, unlike automotive's envelope-solid bridge)."
  0.01)

(def ^:const jaw-half-h-m
  "Jaw AABB half-height (m), lateral -- a representative sub-size
  coupon gauge-width-scale figure (standard sub-size specimen gauge
  width is commonly 12.5 mm; half of that is 6.25 mm)."
  0.00625)

(def ^:const fixture-half-w-m
  "Coupon-far-end fixture AABB half-width (m) -- static anchor, never
  actually collides with anything (the jaw moves AWAY from it), present
  purely as a real Body2D so the simulated world honestly contains both
  grip ends of the coupon being pulled apart."
  0.01)

(def ^:const fixture-half-h-m 0.00625)

(def ^:const limit-boundary-half-w-m
  "Virtual limit-boundary AABB half-width (m) -- the 'end of tether'
  wall the jaw's approach is reframed against; see ns docstring. This
  body has no physical counterpart at all -- it is a pure math device
  standing in for the coupon running out of extensibility at its
  ultimate load."
  0.01)

(def ^:const limit-boundary-half-h-m 0.00625)

(def ^:const settle-ticks
  "Extra ticks appended after the jaw is expected to reach the
  limit-boundary, so the trajectory also captures post-contact
  settling. `physics-2d`'s positional correction removes 80% of any
  remaining overlap per tick (`resolve-contact`'s `0.8` factor), so
  residual overlap after `settle-ticks` further ticks is `0.2^settle-
  ticks` of whatever it was at first contact -- 15 ticks converges to
  ~3e-11 (same rationale/constant as `autoparts.robotics`'s /
  `deviceassembly.robotics`'s `settle-ticks`, a genuine physics-2d
  engine property, not re-derived here)."
  15)

(def ^:const min-tensile-load-n
  "Real, disclosed minimum acceptable peak tensile load (N) for a
  standard sub-size structural/mild-steel test coupon -- see ns
  docstring. 4000 N (4 kN) sits in the plausible low-single-digit-kN
  range for a coupon cross-section on the order of tens of mm^2 at a
  common basic structural steel grade's minimum required tensile
  strength (order of 400 MPa); a newly-defined bound, not a literal
  transcription of one specific named standard's number (the same
  allowance ADR-2607152000 gave `autoparts.robotics/min-proof-load-n`)."
  4000.0)

;; ------------------------------ real simulation ------------------------------

(defn run-tensile-test
  "Time-steps a REAL `physics-2d` world for the steel-coupon tensile
  test and returns:

    {:trajectory [{:tick :position :velocity} ...]   ; jaw body only
     :sim-peak-decel-mps2 n :sim-tensile-load-n n
     :ticks n :dt n :test-speed-mps n :travel-to-peak-load-m n}

  `coupon-mass-kg` is the heat's own recorded effective participating
  mass (moving jaw + locally-engaged coupon gauge-length material -- a
  bare number, the same 'effective participating mass' framing
  `autoparts.robotics`'s `:joint-mass-kg` uses). opts (all optional,
  for tuning/testing): `:test-speed-mps`, `:travel-to-peak-load-m`,
  `:initial-grip-slack-m`, `:dt` overrides (each defaults to this ns's
  own constant of the same name).

  `:sim-peak-decel-mps2` is the PEAK magnitude of tick-to-tick velocity
  change (along the pull axis) divided by `dt` -- derived from the
  actual simulated velocity trajectory, not invented. `:sim-tensile-
  load-n` is `:sim-peak-decel-mps2 * coupon-mass-kg` (Newtons) -- see
  ns docstring for why mass legitimately scales this reading."
  [coupon-mass-kg & [{v-opt :test-speed-mps travel-opt :travel-to-peak-load-m
                       slack-opt :initial-grip-slack-m dt-opt :dt}]]
  (let [v      (double (or v-opt test-speed-mps))
        travel (double (or travel-opt travel-to-peak-load-m))
        slack  (double (or slack-opt initial-grip-slack-m))
        dt     (double (or dt-opt (/ travel v)))
        fixture-x 0.0
        jaw-x0 (+ fixture-x fixture-half-w-m jaw-half-w-m)
        limit-boundary-x (+ jaw-x0 slack travel jaw-half-w-m limit-boundary-half-w-m)
        approach-m (+ slack travel)
        ticks (long (+ settle-ticks (long (ceil* (/ approach-m (* v dt))))))
        fixture (p2d/make-body {:position [fixture-x 0.0]
                                 :velocity [0.0 0.0]
                                 :mass 0.0
                                 :restitution 0.0
                                 :friction 0.0
                                 :collider (p2d/make-aabb-collider fixture-half-w-m fixture-half-h-m)
                                 :user-data :fixture})
        jaw (p2d/make-body {:position [jaw-x0 0.0]
                             :velocity [v 0.0]
                             :mass (double coupon-mass-kg)
                             :restitution 0.0
                             :friction 0.0
                             :collider (p2d/make-aabb-collider jaw-half-w-m jaw-half-h-m)
                             :user-data :jaw})
        limit-boundary (p2d/make-body {:position [limit-boundary-x 0.0]
                                        :velocity [0.0 0.0]
                                        :mass 0.0
                                        :restitution 0.0
                                        :friction 0.0
                                        :collider (p2d/make-aabb-collider limit-boundary-half-w-m limit-boundary-half-h-m)
                                        :user-data :limit-boundary})
        w0 (p2d/world-new [0.0 0.0])
        [w1 _fixture-id] (p2d/world-add w0 fixture)
        [w2 jaw-id] (p2d/world-add w1 jaw)
        [w3 _limit-id] (p2d/world-add w2 limit-boundary)
        worlds (reductions (fn [w _] (p2d/world-step w dt)) w3 (range ticks))
        trajectory (mapv (fn [tick world]
                            (let [b (nth (:bodies world) jaw-id)]
                              {:tick tick :position (:position b) :velocity (:velocity b)}))
                          (range (count worlds)) worlds)
        vxs (mapv (comp first :velocity) trajectory)
        peak-decel-mps2 (->> (map (fn [va vb] (abs* (/ (- vb va) dt))) vxs (rest vxs))
                              (reduce max 0.0))]
    {:trajectory trajectory
     :sim-peak-decel-mps2 peak-decel-mps2
     :sim-tensile-load-n (* peak-decel-mps2 (double coupon-mass-kg))
     :ticks (count trajectory)
     :dt dt
     :test-speed-mps v
     :travel-to-peak-load-m travel}))

(defn tensile-test-telemetry-for
  "Runs the REAL `run-tensile-test` `physics-2d` simulation for
  `heat`'s own recorded `:coupon-mass-kg` and returns the actual
  simulated trajectory telemetry: `{:sim-tensile-load-n n
  :sim-peak-decel-mps2 n :ticks n :dt n :test-speed-mps n
  :travel-to-peak-load-m n}`. Pure, deterministic -- the same
  `:coupon-mass-kg` always reproduces the same telemetry."
  [heat]
  (select-keys (run-tensile-test (:coupon-mass-kg heat))
               [:sim-tensile-load-n :sim-peak-decel-mps2 :ticks :dt
                :test-speed-mps :travel-to-peak-load-m]))

(defn tensile-test-out-of-tolerance?
  "Pure comparator: does `m`'s own `:sim-tensile-load-n` (already
  present on the map -- typically merged in from `tensile-test-
  telemetry-for`) fall below `min-tensile-load-n`? Mirrors
  `autoparts.robotics/proof-load-out-of-tolerance?`'s shape exactly.
  Missing/non-numeric telemetry is never silently treated as a
  violation."
  [{:keys [sim-tensile-load-n]}]
  (and (number? sim-tensile-load-n)
       (< sim-tensile-load-n min-tensile-load-n)))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `heat`'s
  OWN recorded `:coupon-mass-kg`, via a REAL `run-tensile-test`
  `physics-2d` simulation recomputed FRESH right here (never a
  previously stored/self-reported value), yield a peak tensile load
  below `min-tensile-load-n`? Needs no mission run or proposal
  inspection -- like `steelworks.registry/heat-chemistry-out-of-
  range?`, its only input is a permanent field already on the heat
  record. A heat with no `:coupon-mass-kg` on file (no tensile-test
  coupon data yet) never triggers this check -- see ns docstring."
  [{:keys [coupon-mass-kg] :as heat}]
  (and (number? coupon-mass-kg)
       (tensile-test-out-of-tolerance? (merge heat (tensile-test-telemetry-for heat)))))

(defn simulate-tensile-test-cell
  "Run the robot coupon-machining/grip/tensile-pull verification
  mission for `heat-id` (`heat` is the full heat record, incl.
  `:coupon-mass-kg`). Actually runs the REAL engine: `tensile-test-
  telemetry-for` -- the actual `physics-2d`-stepped jaw/fixture/
  limit-boundary collision trajectory (`:sim-tensile-load-n`/
  `:sim-peak-decel-mps2`).

  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :sim-tensile-load-n n :sim-peak-decel-mps2 n}. Deterministic:
  :passed? is derived from the heat's OWN recorded `:coupon-mass-kg`
  via the REAL simulated trajectory (`tensile-test-out-of-
  tolerance?`), never invented or randomized. This function exists for
  API parity with sibling actors' robotics namespaces and future
  `steelworks.steelworksadvisor`/`steelworks.operation` wiring --
  `steelworks.governor`'s independent recheck (`simulation-out-of-
  tolerance?` above) does NOT depend on this mission ever having run;
  it always recomputes fresh from `:coupon-mass-kg` directly."
  [heat-id heat]
  (let [telemetry (tensile-test-telemetry-for heat)
        out-of-range? (tensile-test-out-of-tolerance? (merge heat telemetry))
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" heat-id "-tensile-test")
                                   :robot/tensile-test-cell-1
                                   :tensile-load-verification
                                   :boundaries {:station "mill-lab-tensile-test-cell"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :heat-id heat-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)
     :sim-tensile-load-n (:sim-tensile-load-n telemetry)
     :sim-peak-decel-mps2 (:sim-peak-decel-mps2 telemetry)}))
