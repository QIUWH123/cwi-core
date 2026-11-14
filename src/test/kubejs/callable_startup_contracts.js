// The same startup-created descriptors are reused by every server resource reload.
const callableSavedItemOf = Item.of
const callableSavedFluidOf = Fluid.of
const callableStartupItem = Item('2x minecraft:iron_ingot')
if (callableStartupItem.toStack().count !== 2 || callableSavedItemOf('minecraft:iron_ingot', 3).count !== 3)
  throw new Error('Callable startup Item compatibility')
if (Fluid('125x minecraft:water').toStack().amount !== 125 || callableSavedFluidOf('minecraft:water', 75).amount !== 75)
  throw new Error('Callable startup Fluid compatibility')
function callableReject(name, callback) {
  let rejected = false
  try { callback() } catch (e) { rejected = true }
  if (!rejected) throw new Error('Callable expected rejection: ' + name)
}
for (const n of [0, -1, 0.5, 2147483648, NaN, Infinity, '2', true]) {
  callableReject('item quantity ' + n, () => Item('minecraft:iron_ingot', n))
  callableReject('fluid quantity ' + n, () => Fluid('minecraft:water', n))
}
for (const value of ['', '2x #', '2x #A:B', '#2x #c:ingots/iron', '2x 3x minecraft:iron_ingot', {}, [], null])
  callableReject('malformed ' + value, () => Item(value))
callableReject('fluid amount required', () => Fluid('minecraft:water'))
callableReject('duplicate amount', () => Fluid('250x minecraft:water', 250))
callableReject('duplicate count', () => Item('2x minecraft:iron_ingot', 2))
callableReject('duplicate stack count', () => Item(Item.of('minecraft:iron_ingot'), 2))
callableReject('opposite item kind', () => Item(Fluid('1x minecraft:water')))
callableReject('opposite fluid kind', () => Fluid(callableStartupItem))
callableReject('tag to stack', () => Item('#c:ingots/iron').toStack())
callableReject('fluid tag to stack', () => Fluid('1x #cwi:water').toStack())
for (const n of [-1, 1.1, NaN, Infinity, '0.5']) callableReject('chance ' + n, () => callableStartupItem.withChance(n))
for (const n of [0, -1, NaN, Infinity, '1']) callableReject('weight ' + n, () => callableStartupItem.withWeight(n))
callableReject('chance then weight', () => callableStartupItem.withChance(1).withWeight(1))
callableReject('weight then chance', () => callableStartupItem.withWeight(1).withChance(1))
callableReject('timing integer', () => callableStartupItem.perTick(1.5))
for (const value of [callableStartupItem.withChance(1), callableStartupItem.withWeight(1), callableStartupItem.perTick(false)])
  callableReject('explicit metadata to stack', () => value.toStack())
let callableFresh = callableStartupItem.toStack()
callableFresh.count = 9
if (callableStartupItem.toStack().count !== 2) throw new Error('Descriptor stack copy was shared')
if (!Item.exists('minecraft:iron_ingot') || !Fluid.exists('minecraft:water')) throw new Error('Static exists delegation')
if (Fluid.ingredientOf('minecraft:water') == null || Fluid.sizedIngredientOf('minecraft:water', 25) == null) throw new Error('Fluid static ingredient delegation')
global.cwiCallableReusable = Item('2x minecraft:netherite_scrap')
global.cwiCallableTagItem = Item('#cwi:callable/shared')
global.cwiCallableTagFluid = Fluid('1x #cwi:callable/shared')
global.cwiCallableStartup = true
