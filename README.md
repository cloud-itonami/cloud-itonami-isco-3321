# cloud-itonami-isco-3321

Open Occupation Blueprint for **ISCO-08 3321**: Insurance Representatives.

This repository designs a forkable OSS business for an independent insurance brokerage practice: a document-handling and policy-binder robot manages policy documents under a governor-gated actor, so the practice keeps its own policy and claims records instead of renting a closed insurance-agency SaaS.

**Maturity: `:implemented`.** `src/insurance/` implements the
`InsuranceBrokerageActor` as a `langgraph.graph/state-graph`
(`insurance.actor`) wired to an `Insurance Advisor` (`insurance.advisor`)
and an independent `InsuranceBrokerageGovernor` (`insurance.governor`),
following the itonami actor pattern (ADR-2607011000): `:intake -> :advise
-> :govern -> :decide -+-> :commit (:ok?) +-> :request-approval (:escalate?,
human-in-the-loop interrupt) +-> :hold (:hard?)`. 14 tests / 29 assertions
green (`clojure -M:test`). HARD invariants (always hold, never
overridable): client provenance, no-actuation (`:effect` must be
`:propose`), a registered application basis for any policy-binding
proposal, the proposed coverage amount not exceeding the application's
registered coverage-limit ceiling (binding coverage beyond it is
unauthorized underwriting, not generous coverage), and an attached
risk disclosure before any policy can be bound (binding without one is
undisclosed coverage, not efficient service). Always-escalate ops
(human sign-off regardless of confidence, mapping this repo's Trust
Controls in [`docs/business-model.md`](docs/business-model.md)):
`:approve-over-limit-binding` and `:approve-claims-settlement`.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a document-handling and policy-binder robot performs policy printing, binder assembly and physical archival under an actor that proposes
actions and an independent **Insurance Brokerage Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
policy binding above the client's registered coverage-limit ceiling) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
client application + risk profile + coverage request
        |
        v
Insurance Advisor -> Insurance Brokerage Governor -> bind policy/quote, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3321`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
