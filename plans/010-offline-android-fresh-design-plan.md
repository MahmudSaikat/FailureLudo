# 010 — Fresh offline Android design plan

Status: proposal for the user to review; visual direction is not yet selected.
Scope: native Android only, on `feat/offline-improvements`.
Goal: [perfect, release, and play the offline app first](009-offline-android-redesign-goal.md).

## 1. Start from the desired experience

Treat this as a new game presentation. The current layouts, board renderer, pawn drawings,
colors, dice effects, sounds, and screen composition are replaceable. Existing component APIs
and file organization must not dictate the new design. Reuse a piece of presentation code only
if it serves the chosen design well.

Keep the game's identity in its rules and play: local friends, bots, teams, pairs, captures,
and meaningful choices. Preserve the rule contract in [game-rules-live.md](../docs/game-rules-live.md),
saves, history/replay, and undo/redo. A new rendering and animation system can sit above that
foundation. This is not a game-rule rewrite.

The first deliverable should be a convincing game-screen prototype, not a reskin of every screen.

## 2. Recommended direction: modern tactile tabletop

A straight overhead board with sculpted pieces and subtle material depth. The board should
feel like a carefully made physical object, while the interface stays light and readable.
Depth comes from controlled highlights, contact shadows, bevels, and movement. All track cells
remain equally legible and easy to touch.

| Direction | Character | Tradeoff |
| --- | --- | --- |
| **Modern tactile tabletop — recommended** | Ivory board, saturated enamel pawns, ink-colored surround, precise markings, soft shadows | Requires careful material and motion consistency |
| Playful arcade | Bright flat surfaces, rounded chunky pieces, springy motion, expressive effects | More energetic; effects need restraint during long games |
| Quiet tournament | Very clean board, flat tokens, minimal effects, compact information | Excellent clarity, less of a physical toy feeling |

The recommendation is a proposal, not an approved requirement. Build one representative
screen for the chosen direction before expanding. If uncertainty remains, compare two static
compositions of that screen; do not build three complete themes.

### Proposed art direction

- Deep ink/teal playing surface behind a light ceramic-like board.
- Distinct red, blue, amber, and green pawn families; tune all four together rather than
  copying the old palette. Use light/dark text variants according to the background.
- One consistent light source, soft contact shadows, restrained surface gradients.
- A small, consistent symbol for each player, repeated on pawn, player panel, and home area.
- Clear typography with a compact display treatment for the title and highly readable labels.
- A custom mark and icon family; emoji are not the primary branding or interface artwork.
- Decorative texture stays subtle and away from track markings and small text.

## 3. New gameplay composition

The board is the visual center. Players should immediately understand who is playing,
whose turn it is, the die result, and what they can interact with.

### Portrait phone

1. Slim header: match mode and a menu for secondary controls.
2. Compact upper player rail, aligned with the upper home areas.
3. Large square board with a modest frame.
4. Compact lower player rail, aligned with the lower home areas.
5. Stable action area containing the active player's die and one short status line.

Player panels show a name, color/symbol, human/bot identity, and finished-pawn count.
The active panel gains a clear turn marker. Inactive players remain readable. Team mode
shows the teammate relationship explicitly. Empty seats look intentionally unused.

The active die occupies a predictable place. Its accent and adjacent name identify its owner.
A die result remains attributed to the player who rolled it until the next turn presentation
is ready. No previous result should appear to belong to the next player.

### Landscape and larger screens

Put the board beside the player/status/control area when that preserves a larger usable board.
Size from both available width and height. Keep safe-area insets and system text scaling in mind.
Use extra space for breathing room and legibility, not extra decoration or a stretched board.
The camera stays stable during a match; do not rotate the board on every turn.

Undo, redo, rules, replay tools, and settings remain accessible without competing with the die.
Final placement should follow prototype testing, especially one-handed use and pass-and-play.

## 4. Board and pawn design

### Board

- Draw a clean, exact track with deliberate line weights and spacing.
- Give home areas large simple shapes, four well-spaced pawn docks, and a player symbol.
- Clearly distinguish colored entry cells, safe cells, home lanes, and the finish area.
- Use a consistent safe-cell symbol and small direction marks where they clarify the route.
- Make the center a designed finish destination, with completed-pawn progress shown in player panels.
- Keep decoration subordinate to legal cell boundaries and pawn readability.
- Preserve the agreed topology and clockwise route regardless of visual treatment.

### Pawns

Design one recognizable silhouette: a rounded head, tapered body, stable base, and contact shadow.
Create a consistent set of states: resting, selectable, pressed, moving, captured, and finished.
Selectable pawns receive a clear base marker; avoid making every piece continually pulse.

Pairs must visibly read as linked. A pair plus an independent single must remain distinguishable.
Mixed-color teammate pairs need both identities visible. Crowded safe squares use a compact,
readable grouping and an expanded chooser when needed; never shrink pieces until they are useless.

Touch selection uses forgiving cell/nearest-piece resolution. Ambiguous touches open a chooser
with actual pawn/group previews and player names. Do not blindly enlarge overlapping hit circles.
Provide accessible action labels and an equivalent way to choose legal moves.

Keep manual destination counting unless the user changes that gameplay preference. Home-path
choice cards can illustrate the two routes without automatically revealing calculated landing cells.

## 5. Motion: design one coordinated turn sequence

Motion communicates the move, then gets out of the way. These are starting timing ranges to
validate on a device, not fixed requirements.

| Moment | Proposed treatment | Initial timing |
| --- | --- | --- |
| Die roll | Brief lift, convincing tumble, contact, readable settled face | 450–650 ms total |
| Pawn step | Smooth travel with a small hop and moving shadow; continuous corners | 80–120 ms per cell |
| Landing | Small settle, one synchronized contact sound | 80–120 ms |
| Capture | Contact first, brief impact, captured pawn returns to its dock | 250–400 ms after contact |
| Finish | Pawn arrives, progress updates, localized celebration | 350–550 ms |
| Turn handoff | Active marker transfers after the move resolves | 120–180 ms |

Use a distinct roll event to trigger dice animation, including repeated equal results.
The final die face comes from the engine. Animation must never select or modify the outcome.

Pawns travel continuously between coordinates. A tied pair moves as a group. Capture effects
happen at contact, and sound follows the visible action. Long returns can accelerate so they
do not make the player wait through an entire track again.

One presentation timeline coordinates dice, movement, sound, effects, and turn handoff.
Input, bot actions, and win navigation respect that timeline. Prevent repeated taps from submitting
multiple actions. Cancel stale effects after undo, restart, replay seek, or screen exit.
On background/resume, reconcile to the saved authoritative state; do not replay a backlog of effects.
Reduced-motion mode uses short transitions and clear static state changes.

## 6. Sound and haptics

Create a cohesive small sound pack for the new materials and mood. Choose or produce new assets
on their merits; there is no requirement to retain the existing pack.

- Dice: a short tumble with a definite settling contact.
- Pawn: a light, non-fatiguing tap, with restrained variation on repeated steps.
- Capture: a compact, satisfying impact; avoid a much louder surprise sound.
- Finish: a short ascending resolution.
- Extra roll and skipped turn: distinct, quiet cues.
- Victory: one brief musical resolution rather than a long blocking sequence.
- Interface: subtle confirmation only where useful.

Audio timing follows the visible sequence. Limit overlapping sounds and avoid clipping.
Keep sound effects and haptics independently controllable, with persistent volume settings.
Use short, purposeful haptics for roll contact, capture, and victory. Music is optional and
lower priority than getting effects right; start with it off if added.
Ship all assets locally. Validate on a phone speaker, headphones, muted audio, and disabled haptics.
Use assets with clear redistribution rights; record their sources or creation provenance.

## 7. Supporting offline screens

- **Home:** strong game identity, New Game, conditional Resume, and quieter History/Settings actions.
- **Setup:** convenient friends/bots presets, 2–4 seats, visible teams, names, colors/symbols,
  and an obvious summary before starting. Avoid exposing development notes to players.
- **Pause/settings:** sound, volume, haptics, reduced motion, rules, restart, and exit.
- **Results:** winner identity, team-aware celebration, understandable progress, Play Again,
  Change Players, and Home. Let the final move finish before presenting results.
- **History/replay:** match the new visual language; replay controls stay specific to replay mode.

No online buttons, accounts, room codes, or connection-dependent startup in the offline release.
A fresh installation must launch and play in airplane mode.

## 8. Build strategy and deliverables

Use native Android presentation tools as the starting point: Compose for screens and controls,
and a dedicated drawing/rendering layer for the board and pieces. Prototype the visual result
before settling the renderer structure. A 2.5D look can be drawn without a full 3D scene; adopt
3D only if an approved visual requirement actually needs it.

Exact board geometry, interactive pawns, and die faces should be scalable, deterministic artwork.
Raster art can support backgrounds, branding, or concepts; it must not determine playable geometry.
Build a small design specification covering palette, typography, spacing, materials, icons,
pawn states, timing, and sound cues. Keep these decisions independent of existing UI components.

### Phase A — Visual prototype

- [ ] Choose the art direction using a full gameplay composition.
- [ ] Deliver portrait and landscape compositions, including a crowded midgame state.
- [ ] Show pawn/stack states and a die study at real phone size.
- [ ] Review the result with the user before expanding the style across the app.

### Phase B — Playable presentation slice

- [ ] Build the new board, player panels, pawns, die, and touch selection.
- [ ] Demonstrate one complete roll → selection → move → handoff sequence.
- [ ] Include a capture, a pair move, a home-path choice, and a finish sequence.
- [ ] Synchronize sound/haptics and provide mute/reduced-motion behavior.
- [ ] Make the slice playable against the existing rules, not just an animation demo.

### Phase C — Complete offline app

- [ ] Apply the design to home, setup, settings, results, and replay/history.
- [ ] Preserve save/resume, undo/redo, teams, bots, and user settings.
- [ ] Isolate offline startup/navigation from network services and hide online entry points.
- [ ] Remove superseded offline presentation and assets once their replacements are verified.

### Phase D — Device polish and release

- [ ] Play complete games on a compact phone and a larger screen; validate landscape and large text.
- [ ] Check crowded stacks, mixed-color pairs, repeated die values, rapid taps, interruptions,
  undo during transitions, process recreation, and game-over sequencing.
- [ ] Check airplane-mode fresh launch, resume, bots, local history, and all bundled assets.
- [ ] Measure frame pacing on a representative lower-end device, targeting smooth 60 fps during
  normal play; keep event effects within the same frame budget and avoid wasteful idle animation.
- [ ] Run relevant engine/interaction regression checks and build the Android release candidate.
- [ ] Prepare release assets/versioning, release through the user's chosen channel, then play it.
- [ ] Incorporate real-play feedback before revisiting online development.

## 9. Completion criteria

The redesign is ready when the board and pieces look deliberate at actual phone size, every
turn is understandable, selection is reliable, motion and sound feel coordinated, and a complete
game remains comfortable to play. Offline launch/resume, settings, bots, teams, and replay must
work reliably. The user should be happy to install, release, and play this version.

Do not broaden this effort into online work, theme stores, cosmetic economies, or multiplayer
architecture. Choose one strong visual direction and finish it.

## Next action

Create the native Android gameplay visual prototype. The recommended starting concept is
modern tactile tabletop. The user has requested a fresh plan; they have not yet selected the
visual direction or approved a prototype. No redesign implementation is claimed by this document.
