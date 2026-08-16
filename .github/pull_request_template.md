## What changed

<!-- One or two sentences. What does this PR do, and why? -->

## Why

<!-- The problem being solved. Link an issue if there is one. -->

## Checklist

- [ ] `mvn -B verify` passes locally (this runs the tests **and** the jacoco coverage gate)
- [ ] Coverage did not drop — the gate in `pom.xml` is a floor that should only ratchet upward
- [ ] No secrets, tokens, or real credentials added (these repos are **public**)
- [ ] Config changes were made in [clickkart-config-repository](https://github.com/kripals1199/clickkart-config-repository), on **every** environment branch that needs them (`dev`/`test`/`qa`/`prod`) — they are independent, not stacked
- [ ] If a new required env var was added, it has no default on `prod` (fail fast) and was added to the service's k8s manifest ConfigMap/Secret

## Risk

<!-- What could this break? Anything needing a coordinated deploy or a DB change? -->
