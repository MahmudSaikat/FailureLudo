const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { loadEngine } = require('./engine-loader.cjs');
const engine = loadEngine('gameEngine');
const rules = loadEngine('gameRules');
const types = loadEngine('types');
const scenarios = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../game-engine/src/test/resources/live-rule-scenarios.json'), 'utf8'));

function position(value) {
  if (value === 'BASE') return types.HomeBase;
  if (value === 'FINISHED') return types.Finished;
  if (value.startsWith('T')) return types.MainTrack(Number(value.slice(1)));
  if (value.startsWith('H')) return types.HomeColumn(Number(value.slice(1)));
  throw new Error('Unknown position ' + value);
}
function piece(state, ref) {
  const [color, id] = ref.split(':');
  return state.players.find(p => p.color === color).pieces.find(p => p.id === Number(id));
}
function check(state, expected = {}) {
  if (expected.phase) assert.equal(state.turnPhase, expected.phase);
  if (expected.current) assert.equal(types.currentPlayer(state).color, expected.current);
  if (expected.count !== undefined) assert.equal(state.lastDice.rollCount, expected.count);
  if (expected.shared) assert.deepEqual([...state.sharedTeamDiceEnabled].sort(), expected.shared);
  const movable = new Set(state.movablePieces.map(p => `${p.color}:${p.id}`));
  for (const ref of expected.movable ?? []) assert.ok(movable.has(ref), `Expected movable ${ref}`);
  for (const ref of expected.notMovable ?? []) assert.ok(!movable.has(ref), `Unexpected movable ${ref}`);
  for (const [ref, pos] of Object.entries(expected.positions ?? {})) assert.deepEqual(piece(state, ref).position, position(pos), ref);
  for (const [ref, key] of Object.entries(expected.pairs ?? {})) assert.equal(piece(state, ref).pairKey ?? null, key, ref);
  for (const option of expected.legal ?? []) {
    const pawn = piece(state, option.piece);
    assert.equal(rules.canMove(pawn, option.dice, pawn.color, state.players, state.mode, option.defer), option.result, JSON.stringify(option));
  }
}
for (const scenario of scenarios) test(scenario.name, () => {
  let state = engine.newGame(types.ALL_COLORS, {}, scenario.mode ?? 'FREE_FOR_ALL');
  state.moveCounter = 100;
  state.currentPlayerIndex = types.ALL_COLORS.indexOf(scenario.currentColor ?? 'RED');
  state.sharedTeamDiceEnabled = new Set(scenario.shared ?? []);
  for (const owner of state.players) state.hasEnteredBoardAtLeastOnce[owner.id] = (scenario.entered ?? []).includes(owner.color);
  for (const setup of scenario.pieces) {
    const pawn = piece(state, `${setup.color}:${setup.id}`);
    Object.assign(pawn, { position: position(setup.position), lastMovedAt: setup.lastMovedAt ?? setup.id, pairKey: setup.pairKey ?? null });
  }
  check(state, scenario.initialExpect);
  scenario.steps.forEach((step, i) => {
    const apply = () => step.roll !== undefined ? engine.rollDice(state, step.roll)
      : step.move ? engine.selectPiece(state, piece(state, step.move), step.defer ?? false)
      : engine.advanceNoMoves(state);
    try {
      if (step.expectError) assert.throws(apply);
      else state = apply();
      check(state, step.expect);
    } catch (error) { throw new Error(`Step ${i}: ${JSON.stringify(step)}`, { cause: error }); }
  });
});

test('deterministic replay rejects the wrong actor and forged routes', () => {
  const s = engine.newGame(['RED', 'BLUE']);
  assert.throws(() => engine.applyDeterministicRollOnly(s, 2, 1));
  assert.throws(() => engine.applyDeterministicTurn(s, { actorId: 2, movingPlayerId: 1, pieceId: 0, diceValue: 6, deferHomeEntry: false }));
  assert.throws(() => engine.rollDice(s, 7));
});
