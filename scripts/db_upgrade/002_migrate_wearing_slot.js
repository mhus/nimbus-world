// 002 - Migrate legacy item parameter "wearableSlots" to "wearingSlot"
//
// Background
//   PlayerWearingController reads parameters.wearingSlot first and only falls back
//   to parameters.wearableSlots when that value is a List. In the data it is always
//   a String, so the fallback never applied: items carrying only the legacy value
//   got NO slot validation at all and could be worn in any slot.
//
//   The value is a WEARABLE_GROUP name (HAND, RING, ...), not a WEARABLE_SLOT.
//   "HANDS" is a legacy spelling of "HAND"; every other legacy value is already a
//   valid group name.
//
// Effect
//   A) wearableSlots present, wearingSlot missing -> set wearingSlot, drop legacy
//   B) both present and equal                     -> drop legacy only
//   C) both present and different                 -> untouched, reported
//   D) value not a valid group                    -> untouched, reported
//
// Idempotent: once no document carries wearableSlots any more, this does nothing.
// Set DRY_RUN = true to preview without writing.

const DRY_RUN = false;

const VALID_GROUPS = ['HEAD', 'BODY', 'LEGS', 'FEET', 'NECK', 'RING', 'HAND', 'ARMS'];
const RENAME = { HANDS: 'HAND' };
const P = 'publicData.parameters.';

const plan = { setAndDrop: [], dropOnly: [], conflict: [], unmappable: [] };

db.w_items.find({ [P + 'wearableSlots']: { $exists: true } }).forEach(d => {
  const params = d.publicData.parameters;
  const legacy = params.wearableSlots;
  const current = params.wearingSlot;
  const mapped = RENAME[legacy] || legacy;
  const entry = { _id: d._id, name: d.name, legacy: legacy, mapped: mapped, current: current };

  if (!VALID_GROUPS.includes(mapped)) { plan.unmappable.push(entry); return; }
  if (current === undefined || current === null || current === '') { plan.setAndDrop.push(entry); return; }
  if (current === mapped) { plan.dropOnly.push(entry); return; }
  plan.conflict.push(entry);
});

print((DRY_RUN ? '[DRY-RUN] ' : '') + '002_migrate_wearing_slot');
print('  A) set wearingSlot + drop legacy: ' + plan.setAndDrop.length);
const byMap = {};
plan.setAndDrop.forEach(e => { const k = e.legacy + ' -> ' + e.mapped; byMap[k] = (byMap[k] || 0) + 1; });
Object.keys(byMap).sort().forEach(k => print('       ' + k + ': ' + byMap[k]));
print('  B) drop legacy only:             ' + plan.dropOnly.length);
print('  C) conflicts (untouched):        ' + plan.conflict.length);
plan.conflict.forEach(e => print('       !! ' + e.name + ' wearingSlot=' + e.current + ' vs legacy=' + e.legacy));
print('  D) unmappable (untouched):       ' + plan.unmappable.length);
plan.unmappable.forEach(e => print('       ?? ' + e.name + ' "' + e.legacy + '"'));

if (!DRY_RUN) {
  let set = 0, dropped = 0;
  plan.setAndDrop.forEach(e => {
    set += db.w_items.updateOne({ _id: e._id },
      { $set: { [P + 'wearingSlot']: e.mapped }, $unset: { [P + 'wearableSlots']: '' } }).modifiedCount;
  });
  plan.dropOnly.forEach(e => {
    dropped += db.w_items.updateOne({ _id: e._id }, { $unset: { [P + 'wearableSlots']: '' } }).modifiedCount;
  });
  print('  written: ' + set + ' set, ' + dropped + ' cleaned');
  print('  check - remaining wearableSlots: ' + db.w_items.countDocuments({ [P + 'wearableSlots']: { $exists: true } }));
  print('  check - wearingSlot values:      ' + JSON.stringify(db.w_items.distinct(P + 'wearingSlot')));
}
