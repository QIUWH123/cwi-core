// A real startup event must prevent registration, not merely hide entries from JEI.
let cwiRegistrationContractEvent
const cwiRegistryContractId = Java.loadClass('net.minecraft.resources.ResourceLocation')
// NeoForge permits migration aliases before RegisterEvent; a later rule must stop their resolution too.
BuiltInRegistries.ITEM.addAlias(cwiRegistryContractId.parse('cwi:registration_early_alias'), cwiRegistryContractId.parse('minecraft:stone'))
CwiEvents.blockRegistration(event => {
  cwiRegistrationContractEvent = event
  event.prevent('cwi:registration_blocked')
  event.prevent('cwi:registration_blocked') // Duplicate rules are harmless.
  event.prevent('cwi:registration_preconstructed')
  event.prevent('cwi:registration_helper')
  event.prevent('cwi:registration_direct')
  event.preventItem('cwi:registration_item_only')
  event.preventBlock('cwi:registration_block_only')
  event.preventItem('cwi:registration_script_item')
  event.prevent('cwi:registration_script_block')
  event.preventItem('cwi:registration_blocked_alias')
  event.preventItem('cwi:registration_early_alias')
  event.preventItem('registry_fixture:blocked')
  // Reject malformed/existing IDs instead of silently changing their meaning.
  for (let id of [null, '', 'unqualified', ':empty_namespace', 'cwi:', 'Bad:Name', 'cwi:has space', '#cwi:tag', 'cwi:*', 'minecraft:stone', 'minecraft:stick']) {
    let rejected = false
    try { event.prevent(id) } catch (error) { rejected = true }
    if (!rejected) throw new Error('Registration blocker accepted invalid/already-registered ID: ' + id)
  }
})

StartupEvents.registry('block', event => {
  event.create('cwi:registration_script_block')
})
StartupEvents.registry('item', event => {
  let rejected = false
  try { cwiRegistrationContractEvent.preventItem('cwi:registration_late_kept') } catch (error) { rejected = true }
  if (!rejected) throw new Error('Registration event remained mutable after dispatch')
  event.create('cwi:registration_script_item')
  event.create('cwi:registration_script_kept')
  event.create('cwi:registration_late_kept')
})
