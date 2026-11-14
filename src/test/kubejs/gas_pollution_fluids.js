// Fluid Fixtures Use The Exact Pack Gas Helper Staged Beside This Script
StartupEvents.registry('fluid', event => {
    for (const name of ['chlorine', 'ammonia', 'hydrogen_sulfide', 'carbon_dioxide']) {
        event.create(`kubejs:${name}`).gas(gas => {
            configureCwiGas(gas, name)
            gas.particleDensity(1)
        })
    }
})

StartupEvents.registry('fluid', event => {
    event.create('oxygen').gas(gas => gas.particleDensity(0).oxygenFraction(1))
    event.create('air').tint(0xFFFFFF).gas(gas => gas.particleDensity(0).oxygenFraction(0.2095))
    event.create('contract_gas').tint(0x3366CC).gas(gas => gas
        .spreadRate(0.1).halfLife(80).buoyancy(-0.5).opacity(0.2).particleDensity(0.25)
        .route('skin', route => route.protectedBy(['cwi:chemical_sealing', 'cwi:heat_shielding']))
        .effect('minecraft:weakness', {route: 'skin', threshold: 0.1, level: 1, duration: '2s'})
    )
})

StartupEvents.registry('fluid', event => {
    event.create('contract_existing_gas').noBlock()
    event.create('contract_dose_gas').tint(0x8844AA).gas(gas => gas
        .halfLife(200).spreadRate(0).particleDensity(0).oxygenFraction(0.2095)
        .route('inhalation', route => route.noProtection())
        .effect('cwi:neurotoxin', {
            id: 'dose_test', route: 'inhalation', threshold: 0.02, duration: '2s',
            dose: {gainPerSecond: 2, recoveryPerSecond: 1, levelThresholds: [2, 4, 6, 8]}
        })
        .effect('cwi:neurotoxin', {
            id: 'lower_dose_test', route: 'inhalation', threshold: 0.02, duration: '2s',
            dose: {gainPerSecond: 1, recoveryPerSecond: 1, levelThresholds: [1, 100, 200, 300]}
        })
        .damage({route: 'inhalation', threshold: 1.5, damagePerSecond: 2, scaling: 'excess'})
    )
})
CwiEvents.gases(event => {
    event.modify('kubejs:contract_existing_gas', gas => gas.color(0x998877).halfLife(160)
        .route('inhalation', route => route.protectedBy('cwi:gas_filtration').protectionSlots(['head']))
    )
})
