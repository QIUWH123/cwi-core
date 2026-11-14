// Startup scripts only. Restart after changing gases; ordinary recipe /reload keeps these definitions.
StartupEvents.registry('fluid', event => {
    event.create('example_gas')
        .tint(0xD9E969)
        // Set stillTexture/flowingTexture here to your existing KubeJS fluid assets if desired.
        .gas(gas => gas
            .spreadRate(0.25)       // Fraction redistributed per 20 ticks, constrained by openings.
            .halfLife(800)          // Ticks; 800 = 40 seconds. Whole positive integers only.
            .buoyancy(-0.5)         // -1 sinks, 0 is neutral, +1 rises.
            .opacity(0.25)
            .particleDensity(0.5)
            .oxygenFraction(0)    // Oxygen-free gas; shared oxygen shortage bypasses filters.
            .route('inhalation', route => route
                .protectedBy(['cwi:gas_filtration', 'cwi:chemical_sealing'])
                .requiredResistance(4)
                .durabilityCost(1)
                // All four armor slots are checked by default; available pieces contribute.
            )
            .route('eyes', route => route.protectionSlots(['head']))
            .effect('minecraft:blindness', {
                route: 'eyes', threshold: 0.1, level: 1, duration: '2s'
            })
            .damage({
                route: 'inhalation', threshold: 1.5,
                damagePerSecond: 2, scaling: 'excess'
            })
        )

    event.create('example_dose_gas').tint(0x9988AA).gas(gas => gas
        .halfLife(600).spreadRate(0.2).buoyancy(0.2)
        .route('inhalation', route => route.noProtection())
        .effect('cwi:neurotoxin', {
            id: 'neurotoxin_dose', // Keep this stable to retain saved accumulated dose.
            route: 'inhalation', threshold: 0.02, duration: '2s',
            dose: {
                gainPerSecond: 2, recoveryPerSecond: 1,
                levelThresholds: [10, 30, 60, 100]
            }
        })
    )
})

// For an existing mod fluid, use this instead of registering a duplicate fluid.
// CwiEvents.gases(event => {
//     event.modify('modid:existing_gas', gas => gas
//         .color(0x99AABB).spreadRate(0.3).halfLife(500).buoyancy(0.7)
//         .route('skin', route => route.protectedBy('cwi:chemical_sealing'))
//     )
// })

// Supply composition must be explicit; this does not register duplicate oxygen/air fluids.
// CwiEvents.gases(event => {
//     event.modify('kubejs:oxygen', gas => gas.oxygenFraction(1))
//     event.modify('tfmg:air', gas => gas.oxygenFraction(0.2095))
// })
