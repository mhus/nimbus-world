// 003 - Normalize numeric BlockEffect values to their enum name
//
// Background
//   BlockEffect is deliberately numeric in TypeScript (NONE=0, UNDULATION=1,
//   WIND=2), so imported assets could end up storing the raw number while
//   everything written through Java stores the name. Both are readable - Spring
//   converts Integer to enum by ordinal and the client's normalizeEffect() accepts
//   number, numeric string and name - so this is about consistency, not repair.
//
//   Covers every effect field below publicData, at any nesting depth: the modifier
//   level (visibility.effect) as well as per-texture (visibility.textures.N.effect).
//
// Idempotent: once no effect is stored as a number, this does nothing.
// Set DRY_RUN = true to preview without writing.

const DRY_RUN = false;

const NAMES = { 0: 'NONE', 1: 'UNDULATION', 2: 'WIND' };

// collect every numeric "effect" with its full path
function findNumericEffects(node, path, out) {
  if (node === null || typeof node !== 'object') return;
  if (Array.isArray(node)) { node.forEach((v, i) => findNumericEffects(v, path + '.' + i, out)); return; }
  for (const [k, v] of Object.entries(node)) {
    const p = path + '.' + k;
    if (k === 'effect' && typeof v === 'number') out.push({ path: p, from: v, to: NAMES[v] });
    findNumericEffects(v, p, out);
  }
}

const plan = [];
db.w_blocktypes.find({}).forEach(d => {
  const out = [];
  findNumericEffects(d.publicData, 'publicData', out);
  out.forEach(o => plan.push(Object.assign({ _id: d._id, name: d.name, worldId: d.worldId }, o)));
});

print((DRY_RUN ? '[DRY-RUN] ' : '') + '003_normalize_block_effect');
print('  numeric effect values: ' + plan.length);
plan.forEach(p => print('       ' + p.name + ' (' + p.worldId + ') ' + p.path + ': ' + p.from + ' -> "' + p.to + '"'));
const unmapped = plan.filter(p => !p.to);
if (unmapped.length) {
  print('  unmappable (untouched): ' + unmapped.length);
  unmapped.forEach(p => print('       ?? ' + p.name + ' effect=' + p.from));
}

if (!DRY_RUN) {
  let n = 0;
  plan.filter(p => p.to).forEach(p => {
    n += db.w_blocktypes.updateOne({ _id: p._id }, { $set: { [p.path]: p.to } }).modifiedCount;
  });
  print('  written: ' + n + ' textures/modifiers');
  const rest = [];
  db.w_blocktypes.find({}).forEach(d => { const o = []; findNumericEffects(d.publicData, 'publicData', o); o.forEach(() => rest.push(d.name)); });
  print('  check - remaining numeric effects: ' + rest.length);
}
