# Business Model: Manufacture of Basic Iron and Steel

## Classification
- Repository: `cloud-itonami-isic-2410`
- ISIC Rev.5: `2410` — manufacture of basic iron and steel — heat/batch fabrication, quality screening and mill certification
- Social impact: process-safety, supply-resilience, industrial-jobs

## Customer
- independent steelworks and mini-mills needing auditable mill-test and process-safety records
- contract melters producing slabs, billets and blooms for multiple customers
- downstream fabricators needing verifiable chemistry and quality history
- standards bodies and buyers needing verifiable mill certification evidence
- programs that cannot accept closed, unauditable MES / quality platforms

## Offer
- mill-rules and jurisdiction-scope version management
- robotics-assisted casting, inspection and quality screening records
- heat chemistry-deviation and quality chain-of-custody history
- mill-cert drafts and disclosure records
- role-based access and immutable audit ledger
- CSV/EDN audit package export for inspectors

## Revenue
- self-host setup fee
- managed hosting subscription per mill / production line
- support retainer with SLA
- process/quality robot integration and maintenance

## Trust Controls
- out-of-spec heats are blocked; mill cert is mandatory for release paths; heat history is immutable
- a robot action the governor refuses is never dispatched to hardware
- every dispatch, hold, approval and disclosure path is auditable
- sensitive process and production data stays outside Git
- a fabricated mill-rules citation, incomplete evidence, chemistry
  out of range, or unresolved quality defect -- each forces a hold,
  not an override
- mill-cert issuance is logged and escalated, and cannot be
  finalized twice for the same heat
