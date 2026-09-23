Run `npm test` in `ludo-web` for the agreed live-rule scenarios. The same JSON examples run in the Kotlin engine's `LiveRulesScenarioTest` via `./gradlew :game-engine:test` from the repository root.

To compare full games across engines, generate self-play with the current Kotlin engine, then replay it in TypeScript. From the repository root:

```sh
./gradlew :game-engine:generateSelfPlayDataset -Pepisodes=3 -PmaxPly=1500 -Pseed=42 -Ppolicy=random -Pmode=FREE_FOR_ALL -Poutput=/tmp/ludo-ffa-parity.jsonl
./gradlew :game-engine:generateSelfPlayDataset -Pepisodes=3 -PmaxPly=1500 -Pseed=42 -Ppolicy=random -Pmode=TEAM -Poutput=/tmp/ludo-team-parity.jsonl
node ludo-web/tests/replay-parity.cjs /tmp/ludo-ffa-parity.jsonl /tmp/ludo-team-parity.jsonl
```

The comparison checks every exported state and legal move list, including pair identities, six streaks, capture results, shared dice, and stack-limit invariants. Outputs are temporary files, not repository fixtures.
