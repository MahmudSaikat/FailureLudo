// Usage: node tests/replay-parity.cjs /path/to/kotlin-self-play.jsonl [...]
// Generate inputs with :game-engine:generateSelfPlayDataset using the current engine.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { loadEngine } = require('./engine-loader.cjs');
const E = loadEngine('gameEngine');
const R = loadEngine('gameRules');
const T = loadEngine('types');
const position = p => p.type === 'HomeBase' ? {type:'HOME_BASE'}
  : p.type === 'MainTrack' ? {type:'MAIN_TRACK',index:p.index}
  : p.type === 'HomeColumn' ? {type:'HOME_COLUMN',step:p.step} : {type:'FINISHED'};
function comparable(s) {
  return {
    moveCounter:s.moveCounter, currentPlayerIndex:s.currentPlayerIndex, turnPhase:s.turnPhase,
    lastDice:s.lastDice, sharedTeamDiceEnabled:[...s.sharedTeamDiceEnabled].sort(), winners:s.winners ?? [],
    players:s.players.map(p=>({id:p.id,color:p.color,isActive:p.isActive,pieces:p.pieces.map(pc=>({
      id:pc.id,position:position(pc.position),lastMovedAt:pc.lastMovedAt,pairKey:pc.pairKey ?? null,
    }))})),
    diceByPlayer:Object.entries(s.diceByPlayer).map(([id,dice])=>({playerId:Number(id),dice})),
    enteredBoardFlags:Object.entries(s.hasEnteredBoardAtLeastOnce).map(([id,entered])=>({playerId:Number(id),entered})),
    movablePieces:s.movablePieces.map(p=>({color:p.color,pieceId:p.id})),
  };
}
let count=0;
for (const file of process.argv.slice(2)) {
  let state, episode=-1;
  for (const line of fs.readFileSync(file,'utf8').trim().split('\n')) {
    const row=JSON.parse(line), expected=row.state;
    if (row.episodeIndex !== episode) {
      episode=row.episodeIndex;
      state=E.newGame(expected.players.filter(p=>p.isActive).map(p=>p.color),{},row.mode);
    }
    const actual=comparable(state);
    const wanted={...expected,players:expected.players.map(({id,color,isActive,pieces})=>({id,color,isActive,pieces}))};
    delete wanted.mode;delete wanted.currentPlayerId;
    assert.deepEqual(actual,wanted,`${file}: episode ${episode}, ply ${row.ply}`);
    if (row.actionType==='MOVE') {
      const rolled=E.rollDice(state,row.diceValue);
      const legal=rolled.movablePieces.flatMap(p=>{
        const entry={movingPlayerId:rolled.players.find(owner=>owner.color===p.color).id,pieceId:p.id,deferHomeEntry:false};
        return R.canDeferHomeEntry(p,row.diceValue,rolled.players,rolled.mode) ? [entry,{...entry,deferHomeEntry:true}] : [entry];
      });
      assert.deepEqual(legal,row.legalMoveOptions,`Legal choices differ at episode ${episode}, ply ${row.ply}`);
      state=E.applyDeterministicTurn(state,{actorId:row.actorId,diceValue:row.diceValue,...row.action});
    } else state=E.applyDeterministicRollOnly(state,row.actorId,row.diceValue);
    // The limit and persistent-pair invariants must hold after every actual move.
    const groups=new Map(),pairs=new Map();
    for(const owner of state.players) for(const pc of owner.pieces) {
      if(pc.position.type!=='MainTrack'||loadEngine('board').isSafeSquare(pc.position.index)) {
        assert.equal(pc.pairKey ?? null,null);
        continue;
      }
      const side=state.mode==='TEAM' ? loadEngine('board').teamIndex(owner.color) : owner.color;
      const key=`${side}:${pc.position.index}`;
      groups.set(key,(groups.get(key)??0)+1);
      if(pc.pairKey) pairs.set(pc.pairKey,[...(pairs.get(pc.pairKey)??[]),pc.position.index]);
    }
    for(const size of groups.values()) assert.ok(size<=3);
    for(const indices of pairs.values()) {assert.equal(indices.length,2);assert.equal(indices[0],indices[1]);}
    count++;
  }
}
assert.ok(count>0,'Pass one or more Kotlin self-play JSONL files');
console.log(`${count} Kotlin turns replayed identically in TypeScript; states, legal choices, pair membership and stack limits match.`);
