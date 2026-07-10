# Operator Guide

## First Deployment
1. Register mill metallurgists, mills, heats, personnel and robots.
2. Import historical heat / quality / class records.
3. Run read-only validation and robot mission dry-runs.
4. Configure mill-standards evidence checklists and human sign-off paths.
5. Publish a dry-run audit export.

## Minimum Production Controls
- governor gate on every robot action before dispatch
- human sign-off for `:high`/`:safety-critical` robot actions (e.g. casting on process-critical heats, mill-cert issuance)
- audit export for every dispatch, sign-off and disclosure
- backup manual process

## Certification
Certified operators must prove robot-safety integrity, evidence-backed
records and human review for safety-affecting actions.

## Operating states
intake : mill-rules-verify : quality-screen : approve : dispatch-heat : issue-mill-cert : audit

## Audit export (social operation)

After a production session, export the append-only package for class
surveyors or internal compliance:

```clojure
(require '[steelworks.export :as export])
(export/audit-package store)        ; EDN maps
(export/package->csv-bundle store)  ; CSV files as string map
```

Drafts remain **unsigned** — signing and submission to a mill-standards body
are the steelworks's own acts (see README Actuation honesty).

Static UI sample: `docs/samples/operator-console.html`.
