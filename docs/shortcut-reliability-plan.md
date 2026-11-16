# Launcher3 shortcut reliability implementation ledger

Plan: `C:/git/android-launchers-shortcuts-fix/docs/superpowers/plans/2026-11-16-shortcut-reliability.md`; binding specification: `C:/git/android-launchers-shortcuts-fix/docs/brainstorm/android-launchers-shortcuts-fix_HANDOFF_STANDALONE_EN.md`.

Base: `c612e6ece389f21c40f8cb9cd9a4b44239f00009`; branch: `fix/preserve-unavailable-shortcuts`; only this isolated worktree may be changed. Coordinator owns independent review and publication.

## Concrete milestones

1. Reproduce the existing post-boot receiver deleting a missing application using its actual production Java class and narrow JVM Android/model boundary doubles. Add the matching processor regression to the canonical multivalent test source set. No production changes before observing the regression fail.
2. Add a versioned availability/removal journal with atomic replacement, stable profile/package keys, exact database/item/component protection, conservative unknown-storage decisions, and repeatable persistence/event-order tests. Integrate loading, package callback journaling before the unloaded-task gate, and final-load reconciliation.
3. Integrate inferred-removal protection and exact-component recovery into update tasks and post-boot handling. Retain existing specialized policies and real uninstall removal. Preserve favorites appearance, including low-resolution folder children, and implement foreground revalidation using the existing model lifecycle.
4. Run focused executable tests, inspect the complete diff, document canonical AOSP commands and limitations, and commit all non-regenerable tests/reports. Do not claim platform/end-to-end validation from JVM checks.

## Pre-flight interfaces

- Loader and update task share exact-component availability policy; callbacks must journal events before model task discard, while loader must consult that journal before final binding.
- Favorites remains authoritative for identity/placement/appearance. Journal must never recreate deleted rows; removal records must remain idempotent across interruption.
- Unknown or unreadable metadata preserves ordinary shortcuts; known internal applications outside recovery retain existing missing-target policy.

## Rulings and progress

- Ruling: maintain this durable, committed repo-local ledger instead of the skill's disposable shared-plan workspace scripts — the supplied plan spans three separately owned repositories and contains no task briefs or executable test commands; local milestones provide the required isolation and recoverability.
- Inspection: no applicable AGENTS.md found. Actual canonical tests are under `tests/multivalentTests`, not `tests/src`. This clone has Soong definitions only and no full Android tree or Gradle wrapper; platform tests cannot run directly on this Windows checkout.
- Reproduction pending: `SdCardAvailableReceiver` maps a negative query and failed SD classification to `onPackagesRemoved`; `PackageUpdatedTask` calls `appsRemoved` after a negative `isPackageEnabled`; `WorkspaceItemProcessor` deletes absent targets after boot and replaces unresolved components with another activity.
- Milestone 1 RED: `python tests/shortcutreliability/run_tests.py` compiled the unchanged production receiver and failed with `AssertionError: Boot complete is not uninstall evidence: removed=1` (exit 1). The harness substitutes only Android/model service boundaries; the receiver decision is production code. This is not a platform/model end-to-end test.
- Milestone 1 GREEN: the same command passed one receiver regression after converting post-boot checks into availability/unavailability notifications. `git diff --check` passed. Generated classes are under the approved `%LOCALAPPDATA%/Temp/opencode/launcher3-shortcut-tests-*` directories and are intentionally not committed or deleted.
