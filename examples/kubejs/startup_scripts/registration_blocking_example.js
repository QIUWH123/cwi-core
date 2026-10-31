// Put this file in kubejs/startup_scripts/ on both client and server.
// Requires CWI Core's registration-blocking integration. Fully restart after edits.
// /reload cannot add or remove these rules.
// The lists start empty: uncomment and replace the example IDs you want to prevent.

CwiEvents.blockRegistration(event => {
  // Prevent each ID in BOTH the block registry and the item registry.
  // Use this for a normal block and its same-ID block item.
  const blocksAndItems = [
    // 'some_mod:unwanted_machine',
    // 'some_mod:unwanted_ore'
  ]
  blocksAndItems.forEach(id => event.prevent(id))

  // Prevent ONLY the item. A same-ID block, if present, remains registered.
  const itemsOnly = [
    // 'some_mod:unwanted_tool',
    // 'some_mod:unwanted_material'
  ]
  itemsOnly.forEach(id => event.preventItem(id))

  // Prevent ONLY the block. A same-ID item, if present, remains registered.
  // Usually use prevent() instead when the block also has an item.
  const blocksOnly = [
    // 'some_mod:itemless_block'
  ]
  blocksOnly.forEach(id => event.preventBlock(id))

  // Individual calls are supported too; methods can be chained.
  // event.prevent('some_mod:machine')
  //   .preventItem('some_mod:tool')
  //   .preventBlock('some_mod:itemless_block')

  // If a block and its item use DIFFERENT IDs, target each separately:
  // event.preventBlock('some_mod:machine_block')
  // event.preventItem('some_mod:machine_item')
})

// Use exact 'namespace:path' IDs. Tags (#...), wildcards (*) and bare names don't work.
// Already-registered objects, including vanilla entries, cannot be prevented.
// Matching IDs will be absent from registries; adapt recipes, tags, loot and other
// content that refers to them. This does not migrate existing worlds.
