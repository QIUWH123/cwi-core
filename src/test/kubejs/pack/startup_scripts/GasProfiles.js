// priority: 1000

// Pure Gas Buoyancy Uses Bounded Density Contrast Against Dry Air At Equal Temperature And Pressure
// Mixture Biases Are Estimates Unless Composition Is Known; They Do Not Define A Blend Ratio
// Half Life Models Gameplay Dispersal, Including In Enclosed Spaces, Not Chemical Decomposition
// Core Requires A Positive Finite Half Life; Sealed Room Conservation Needs Native Changes
// Concentration Thresholds Are Core Gameplay Units, Not Atmospheric Safety Limits
// Core Shares Oxygen Availability Across Gases; Ignition And Heat Remain Unimplemented
// Existing Fluid Tints Identify Products; Most Real Gases Are Colorless
// Visible Gas Opacity Leaves Room Above The Shader Cutoff For Particle Fading

// Molar Mass In Grams Per Mole Gives Relative Density At Equal Temperature And Pressure
// The Bounded Contrast Is A Transport Bias, Not A Physical Acceleration
function cwiGasBuoyancy(molarMass) {
    return Math.max(-1, Math.min(1, 1 - molarMass / 28.965))
}

const cwiGasProfiles = {
    lpg: { buoyancy: -0.65, halfLife: 1000, hazard: 'asphyxiant' },
    butane: { buoyancy: cwiGasBuoyancy(58.122), halfLife: 1000, hazard: 'asphyxiant' },
    propane: { buoyancy: cwiGasBuoyancy(44.097), halfLife: 1000, hazard: 'asphyxiant' },
    hydrogen: { buoyancy: cwiGasBuoyancy(2.016), spreadRate: 0.4, hazard: 'asphyxiant' },
    ethylene: { buoyancy: cwiGasBuoyancy(28.054), hazard: 'asphyxiant' },
    propylene: { buoyancy: cwiGasBuoyancy(42.081), hazard: 'asphyxiant' },
    neon: { buoyancy: cwiGasBuoyancy(20.180), spreadRate: 0.3, hazard: 'asphyxiant' },
    carbon_dioxide: { buoyancy: cwiGasBuoyancy(44.009), halfLife: 1200, hazard: 'carbon_dioxide' },
    oxygen: { buoyancy: cwiGasBuoyancy(31.998), oxygenFraction: 1, hazard: 'none' },
    nitrogen: { buoyancy: cwiGasBuoyancy(28.014), hazard: 'asphyxiant' },
    nitrogen_dioxide: { buoyancy: cwiGasBuoyancy(46.005), opacity: 0.25, particleDensity: 0.4, irritation: 0.05, hazard: 'irritant' },
    ammonia: { buoyancy: cwiGasBuoyancy(17.031), halfLife: 400, hazard: 'ammonia' },
    chlorine: { buoyancy: cwiGasBuoyancy(70.900), opacity: 0.28, particleDensity: 0.4, hazard: 'chlorine' },
    natural_gas: { buoyancy: 0.15, hazard: 'asphyxiant' },
    argon: { buoyancy: cwiGasBuoyancy(39.948), hazard: 'asphyxiant' },
    helium: { buoyancy: cwiGasBuoyancy(4.003), spreadRate: 0.4, hazard: 'asphyxiant' },
    hydrogen_sulfide: { buoyancy: cwiGasBuoyancy(34.076), halfLife: 700, hazard: 'hydrogen_sulfide' },
    sulfur_dioxide: { buoyancy: cwiGasBuoyancy(64.058), irritation: 0.1, hazard: 'irritant' },

// Steam Bias Describes Water Vapor; Particles Represent Condensation Without Heat Or Condensate Recovery
    steam: { buoyancy: cwiGasBuoyancy(18.015), halfLife: 120, spreadRate: 0.4, opacity: 0.2, particleDensity: 0.45, hazard: 'none' },
    ethane: { buoyancy: cwiGasBuoyancy(30.070), hazard: 'asphyxiant' },

// Unresolved Refinery Mixtures Use Neutral Movement As A Fallback, Not A Claim Of Equal Air Density
    cracked_ethane: { buoyancy: 0, hazard: 'asphyxiant' },
    cracked_propane: { buoyancy: 0, hazard: 'asphyxiant' },

// Cracked Naphtha Separates Into A Light Fraction Averaging 32.87 Grams Per Mole And Heavier Pyrolysis Gasoline
// This Conservative Sinking Bias Describes Cooled Vapor; The Exact Heavy Fraction And Release Temperature Remain Unspecified
    cracked_naphtha: { buoyancy: -0.14, hazard: 'asphyxiant' },

// Natural Gas Biases Retain The CO2 Scrubbing And Helium Removal Trends Without Assuming A Closed Composition Model
    purified_natural_gas: { buoyancy: 0.25, hazard: 'asphyxiant' },
    methane: { buoyancy: cwiGasBuoyancy(16.043), hazard: 'asphyxiant' },
    benzene: { buoyancy: cwiGasBuoyancy(78.114), halfLife: 1000, hazard: 'vapor' },
    toluene: { buoyancy: cwiGasBuoyancy(92.141), halfLife: 1000, hazard: 'vapor' },
    xylene: { buoyancy: cwiGasBuoyancy(106.168), halfLife: 1000, hazard: 'vapor' },
    butadiene: { buoyancy: cwiGasBuoyancy(54.092), hazard: 'vapor' },
    nitrous_oxide: { buoyancy: cwiGasBuoyancy(44.013), hazard: 'anesthetic' },
    dry_gas: { buoyancy: 0, hazard: 'asphyxiant' },
    cracked_gas: { buoyancy: 0, hazard: 'asphyxiant' },
    natural_gas_depleted: { buoyancy: 0.1, hazard: 'asphyxiant' },

// Syngas Represents Carbon Monoxide And Hydrogen Without An Exact Mixture Ratio
    syngas: { buoyancy: 0.6, hazard: 'syngas' },

// Existing Mod Fluids Retain Their Registrations
    air: { buoyancy: 0, oxygenFraction: 0.2095, halfLife: 200, spreadRate: 0.4, opacity: 0, particleDensity: 0, hazard: 'none' },

// Bio Gas Shares Digestion And Wood Carbonization Sources; One Profile Cannot Distinguish Their Composition
// The Asphyxiant Fallback Omits Possible Wood Gas CO Toxicity; Separate Streams Require Recipe And Fluid Changes
    bio_gas: { buoyancy: 0, hazard: 'asphyxiant' }
}

// Older Installed Core Keeps Its Presets Until The Updated Jar Is Installed
let cwiScriptedGases = false

function configureCwiGas(gas, name) {
    const profile = cwiGasProfiles[name]

    if (!profile) {
        throw new Error(`Missing CWI Gas Profile: ${name}`)
    }

    gas.buoyancy(profile.buoyancy)
        .halfLife(profile.halfLife === undefined ? 800 : profile.halfLife)
        .spreadRate(profile.spreadRate === undefined ? 0.25 : profile.spreadRate)
        .opacity(profile.opacity === undefined ? 0.2 : profile.opacity)
        .particleDensity(profile.particleDensity === undefined ? 0.15 : profile.particleDensity)
        .targets('players')

// Retain Old Core Compatibility Until Its Shared Oxygen Build Is Installed
    const sharedOxygen = typeof gas.oxygenFraction === 'function'

    if (sharedOxygen) {
        gas.oxygenFraction(profile.oxygenFraction === undefined ? 0 : profile.oxygenFraction)
    }

    if (!cwiScriptedGases && ['chlorine', 'ammonia', 'hydrogen_sulfide', 'carbon_dioxide'].includes(name)) return
    if (profile.hazard === 'none') return

// Preserve The Existing CO2 Rules Alongside Core's Shared Oxygen Availability
    if (profile.hazard === 'carbon_dioxide') {
        gas.route('inhalation', route => route.noProtection())
            .effect('minecraft:weakness', {
                route: 'inhalation', threshold: 0.75, level: 1, duration: '2s'
            })
            .effect('minecraft:slowness', {
                route: 'inhalation', threshold: 0.75, duration: '2s'
            })
            .damage({
                route: 'inhalation', threshold: 1.5, damagePerSecond: 2, scaling: 'excess'
            })

        for (let stage = 2; stage <= 4; stage++) {
            gas.effect('minecraft:weakness', {
                route: 'inhalation', threshold: (stage - 1) * 2, level: stage, duration: '2s'
            })
        }

        return
    }

// Fixed Plus Excess Terms Preserve The Previous Concentration Scaled Damage At Its Threshold
    if (profile.hazard === 'chlorine' || profile.hazard === 'ammonia') {
        let airwayThreshold = profile.hazard === 'chlorine' ? 0.05 : 0.1
        let airwayRate = profile.hazard === 'chlorine' ? 2 : 1.25

        gas.effect('minecraft:blindness', {
            route: 'eyes', threshold: 0.1, duration: '2s'
        }).damage({
            route: 'inhalation', threshold: airwayThreshold, damagePerSecond: airwayRate * airwayThreshold
        }).damage({
            route: 'inhalation', threshold: airwayThreshold, damagePerSecond: airwayRate, scaling: 'excess'
        })

        return
    }

// Saved Dose And Effect Ownership Import Once Through Generic Core Migration Fields
    if (profile.hazard === 'hydrogen_sulfide') {
        gas.effect('minecraft:blindness', {
            route: 'eyes', threshold: 0.4, duration: '2s'
        }).effect('cwi:neurotoxin', {
            id: 'neurotoxin_dose', route: 'inhalation', threshold: 0.02, duration: '2s',
            dose: {
                gainPerSecond: 2, recoveryPerSecond: 1, levelThresholds: [10, 30, 60, 100],
                legacyKey: 'cwi:airborne_neurotoxin', legacyOwnerKey: 'cwi:airborne_neurotoxin_level'
            }
        }).damage({
            route: 'inhalation', threshold: 0.75, damagePerSecond: 4, scaling: 'excess'
        })

        return
    }

// Ordinary Filters Do Not Supply Oxygen Or Remove Nitrous Oxide
    if (profile.hazard === 'asphyxiant' || profile.hazard === 'anesthetic') {
        gas.route('inhalation', route => route.noProtection())

// Updated Core Owns Suffocation; Keep Legacy Rules Only For The Previously Installed Build
        if (!sharedOxygen) {
            gas
                .effect('minecraft:weakness', {
                    route: 'inhalation', threshold: 0.75, duration: '2s'
                })
                .effect('minecraft:slowness', {
                    route: 'inhalation', threshold: 1, duration: '2s'
                })
                .damage({
                    route: 'inhalation', threshold: 1.5, damagePerSecond: 2, scaling: 'excess'
                })
        }

        if (profile.hazard === 'anesthetic') {
            gas.effect('minecraft:nausea', {
                route: 'inhalation', threshold: 0.5, duration: '4s'
            })
        }

        return
    }

// Toxin Protection Is Independent From Core's Unfiltered Shared Oxygen Check
    if (profile.hazard === 'irritant') {
        gas.effect('minecraft:blindness', {
            route: 'eyes', threshold: 0.1, duration: '2s'
        }).damage({
            route: 'inhalation', threshold: profile.irritation, damagePerSecond: 2, scaling: 'excess'
        })

        return
    }

// Organic Vapor Rules Model Acute Exposure, Not Cancer Or Other Chronic Disease
    if (profile.hazard === 'vapor') {
        gas.effect('minecraft:weakness', {
            route: 'inhalation', threshold: 0.4, duration: '2s'
        }).effect('minecraft:nausea', {
            route: 'inhalation', threshold: 0.75, duration: '4s'
        }).effect('minecraft:blindness', {
            route: 'eyes', threshold: 0.5, duration: '2s'
        }).damage({
            route: 'inhalation', threshold: 1.5, damagePerSecond: 2, scaling: 'excess'
        })

        return
    }

// Carbon Monoxide Exposure Accumulates; Ordinary Gas Filters Do Not Remove It
// Separate Symptom Rules Use Matching Dose Rates And Caps; Existing Weakness ID Preserves Saves
// Recovery Is A Gameplay Rate; Carbon Monoxide Dose Damage And Oxygen Treatment Remain Unimplemented
    if (profile.hazard === 'syngas') {
        gas.route('inhalation', route => route.noProtection())
            .effect('minecraft:weakness', {
                id: 'carbon_monoxide_dose', route: 'inhalation', threshold: 0.05, duration: '2s',
                dose: { gainPerSecond: 2, recoveryPerSecond: 1, levelThresholds: [10, 30, 60, 100] }
            })
            .effect('minecraft:nausea', {
                route: 'inhalation', threshold: 0.25, duration: '4s'
            })
            .effect('minecraft:nausea', {
                id: 'carbon_monoxide_nausea', route: 'inhalation', threshold: 0.05, duration: '4s',
                dose: { gainPerSecond: 2, recoveryPerSecond: 1, levelThresholds: [30, 60, 100] }
            })
            .damage({
                route: 'inhalation', threshold: 0.75, damagePerSecond: 2, scaling: 'excess'
            })

        return
    }

    throw new Error(`Unknown CWI Gas Hazard: ${profile.hazard}`)
}

CwiEvents.gases(event => {
    cwiScriptedGases = typeof event.alias === 'function'

// Explicit Saved Cloud Aliases Preserve Both Previous Save Formats
    if (cwiScriptedGases) {
        for (const name of ['chlorine', 'ammonia', 'hydrogen_sulfide', 'carbon_dioxide']) {
            event.alias(`cwi:${name}`, `kubejs:${name}`)
        }

        if (Platform.isLoaded('tfmg')) {
            event.alias('tfmg:carbon_dioxide', 'kubejs:carbon_dioxide')
        }
    }

    if (Platform.isLoaded('tfmg')) {
        event.modify('tfmg:air', gas => {
            configureCwiGas(gas, 'air')
            gas.color(0xFFFFFF)
        })
    }

    if (Platform.isLoaded('ratatouille')) {
        event.modify('ratatouille:bio_gas', gas => {
            configureCwiGas(gas, 'bio_gas')
            gas.color(0xFFFFFF)
        })
    }
})
