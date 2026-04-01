# FailureLudo Live Rules (Current Implementation)

Last updated: 2026-03-28

This document describes the rules as currently implemented in the game engine.
Code remains the source of truth, but this file is the human-readable baseline for future rule changes.

## Source of Truth

- `game-engine/src/main/kotlin/com/failureludo/engine/GameEngine.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/GameRules.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/Board.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/PlayerColor.kt`

## Board and Seats

- Main track has 52 cells (`0..51`).
- Home column has 5 steps (`1..5`), then piece is `Finished`.
- Entry squares:
  - RED: `0`
  - BLUE: `13`
  - YELLOW: `26`
  - GREEN: `39`
- Home-column entry squares:
  - RED: `50`
  - BLUE: `11`
  - YELLOW: `24`
  - GREEN: `37`
- Safe squares (cannot be captured): `0, 8, 13, 21, 26, 34, 39, 47`.

## Game Setup

- Allowed player count: 2 to 4 active colors.
- Team mode requires exactly 4 active colors.
- All 4 player seats always exist internally; non-selected colors are marked inactive.
- Starting turn is the first color in `activeColors`.

## Turn Flow

1. Current player rolls.
2. If movable pieces exist: phase becomes piece selection.
3. If no movable pieces: phase becomes no-move, then auto-advance by caller.
4. After move:
   - Win check
   - Extra-roll check
   - Otherwise next player

## Dice and Entry Rules

- Dice is `1..6`.
- A piece leaves `HomeBase` only on `6`.
- If a player rolls three consecutive sixes in the same turn, that turn is forfeited and play advances.

## Movement Rules

- On main track, movement is clockwise by increasing index with wraparound.
- If movement reaches home-column branch exactly, piece enters home column.
- Home column must be exact:
  - landing exactly beyond step 5 -> `Finished`
  - overshoot is invalid

## Doubled / Triple Pawn Rules

- Stacking model:
  - 2 same-color pawns on one main-track cell are treated as a tied double.
  - 3 same-color pawns on one main-track cell are treated as `double + top single`.
  - In a triple stack, the top single is the most recently moved pawn.
- Locked double movement:
  - Locked doubles cannot move as separate pawns.
  - Only even dice can move a locked double.
  - Effective movement distance for the locked double is half the die value.
  - Odd dice means that locked pair has no legal move for that roll.
- Triple stack behavior on non-safe, non-entry cells:
  - The top single remains independently movable.
  - The locked pair remains constrained to tied-double rules.
  - When a tap lands on a stack where both options are legal, the UI asks the player to choose **Move single** or **Move pair**.
- Unlock behavior:
  - Lock is released on safe squares and on the color's home-column entry cell.
- Team-mode mixed doubles:
  - In Team mode, two same-team pawns of different colors on the same main-track cell are also treated as a tied double.
  - Same tied-double movement rules apply (even-only rolls, half-distance movement).
  - While such a mixed-color pair remains tied, it cannot enter a home path and continues on the shared main track.
  - This enforces that a tied mixed pair follows the non-entering route until untied (safe-square unlock).
- Barrier behavior:
  - Enemy single pawns cannot jump over an opponent double stack.
  - Enemy single pawns can land exactly on that barrier cell.
  - Double stacks can jump over other double stacks.
- Capture typing:
  - Single captures only singles.
  - Double captures only doubles.
  - In triple stacks, an enemy double captures only the double component.
  - The top single on own double is protected (virtual safe) from enemy single capture.
  - Enemy single on your double is not protected from your captures.
  - Special immediate pair capture:
    - If a player's single is already on top of an enemy double, and another single from the same side lands on that same cell, the two singles immediately form a pair and capture that enemy double immediately.
    - In Team mode, "same side" includes both teammate colors.

## Optional Home-Entry Deferral (Human Players)

- When a human-selected pawn on the main track would enter the home path (`HomeColumn` or direct `Finished`) for the current dice roll, a choice prompt appears.
- The player can choose:
  - **Enter Finish**: use normal home-path behavior.
  - **Keep Circulating**: defer home entry for this move and stay on main track.
- On **Keep Circulating**, the pawn still belongs to the same player and moves to the normal wrapped main-track destination for that dice value (no reset to starting square).
- This lets the pawn complete additional circulation before attempting home entry again.
- Bots do not use this prompt in the current implementation session.

## Capture Rules

- Capture applies only when landing on main track.
- Landing on a safe square never captures.
- Opponent piece(s) on the landing square are sent to `HomeBase`.
- Capturing grants an extra roll.

## Extra Roll Rules

Extra roll is granted when:

- the dice roll is `6`, or
- the move captures at least one opponent piece.

## Turn Order Direction (Dice Passing)

- Pawn path remains as currently implemented.
- Turn passing is right-hand direction from the current player seat.
- Implementation detail: next player index moves backward through seat list and skips inactive seats.

## Winning Conditions

### Free-for-all

- First active player with all 4 pieces finished wins.

### Team mode

- Team 0: RED + YELLOW
- Team 1: BLUE + GREEN
- A team wins when both active members have all pieces finished.

## Bot Piece Selection Priority

When bot must choose among movable pieces:

1. capture move
2. finishing move
3. most advanced active piece
4. otherwise first movable piece

## Notes for Future Rule Changes

- Update this file in the same PR whenever rules are changed in engine code.
- If UI text/hints depend on rules, update those strings and flows in `:app` accordingly.
