// Test-only tiers let the exact production model migration load in the isolated test pack.
ItemEvents.toolTierRegistry(event => {
  for (let name of ['steel', 'stainless_steel', 'bronze', 'industrial_iron', 'brass', 'zinc', 'copper', 'lead', 'rusted_iron']) {
    event.add(name, tier => {
      tier.setUses(500)
      tier.setSpeed(5)
      tier.setAttackDamageBonus(0)
      tier.setIncorrectBlocksForDropsTag('minecraft:incorrect_for_iron_tool')
      tier.setEnchantmentValue(0)
      tier.setRepairIngredient(Ingredient.of('minecraft:iron_ingot'))
    })
  }
})
