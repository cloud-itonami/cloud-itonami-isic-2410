# cloud-itonami-isic-2410

Open Business Blueprint for **ISIC Rev.5 2410**: building of ships and
floating structures -- steel-heat fabrication, casting/quality screening and
mill-cert issuance for a community steelworks.

This repository publishes a basic-iron-steel actor -- heat intake,
per-jurisdiction mill-rules verification, quality-defect screening, robot
block-dispatch and mill-cert finalization -- as an OSS business
that any qualified steelworks can fork, deploy, run, improve and sell,
so a mill keeps its own construction and class history instead of
renting a closed MES / quality SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Steelworks Advisor ⊣
Steelworks Governor**.

## Scope note: manufacturing, not ship operation

This repository is scoped to **building** ships and floating
structures (steel heats, modules, casting/quality, class evidence). It is
not a ship-operator vertical (navigation, crewing, commercial
voyage). Distinct from:

- `cloud-itonami-isic-3020` — railway rolling-stock **manufacturing**
- `cloud-itonami-isic-3030` — aircraft/aerospace **manufacturing**
- transport-operator ISICs (e.g. 5011 sea passenger / 5020 sea freight)

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (casting, fit-up,
inspection, quality scan) operate under an actor that proposes actions and
an independent **Steelworks Governor** that gates them. The governor
never issues class evidence itself; `:high`/`:safety-critical`
actions (`:actuation/dispatch-heat`, `:actuation/issue-mill-cert`)
require human sign-off.

## Core contract

```text
heat intake + mill-rules verify + quality screen
  -> Steelworks Advisor proposal
  -> Steelworks Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Dispatching a casting/fit-up robot and issuing class evidence produce
**unsigned draft records and ledger facts only**. This actor does not
talk to real mill control systems or mill-standards portals. Signature
and hardware dispatch are the steelworks's own acts.

## Ops

| Op | Effect |
|---|---|
| `:heat/intake` | normalize heat directory patch (phase 3 may auto-commit when clean) |
| `:mill-rules/verify` | per-jurisdiction class evidence checklist (always human) |
| `:quality/screen` | quality defect screen (HARD hold if unresolved) |
| `:actuation/dispatch-heat` | draft block-dispatch record (always human) |
| `:actuation/issue-mill-cert` | draft mill-cert record (always human) |

## Social / regulatory hand-off

```clojure
(require '[steelworks.store :as store]
         '[steelworks.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for class/flag hand-off
(export/package->csv-bundle db)     ;; CSV bundle (heats/ledger/dispatches/mill-cert)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-2410/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-2410
```

Writes CSV files under `out/audit-package/` (or the given directory).
