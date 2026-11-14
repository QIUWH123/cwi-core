# CWI Core

Gameplay, machines and KubeJS tools for the Create Wasteland Industry modpack.
Core extends Create with pack-specific content and rules; its mod ID is `cwi`.

## Features

- Blast furnace, sealed mixing vessel, incubator, Stirling engine and lava
  extractor, with automation and applicable Engineer's Goggles information.
  The solar evaporator currently provides storage only.
- Charged hammers, equipment-upgrade recipes and protective enchantments;
  configurable gas clouds with exposure effects and oxygen displacement.
- Petrified wood and amber, reinforced rope, budding and suspicious blocks,
  weathered girders/brackets, and contraptions carried by Create conveyor chains.
- KubeJS machine recipes, catalysts, periodic inputs/outputs, weighted output
  pools and priority where supported; additional Create/addon recipe integrations
  and JEI displays. Emissive block/item textures use `.mcmeta` metadata.
- Fixed CWI rules prevent new Nether portals, disable all advancements and
  suppress experimental-world/resource-pack format warnings. Selected Vintage
  Improvements materials and addon conversion interactions are also removed.

## Usage

Install Core on the client and server alongside its dependencies and the pack's
KubeJS assets. Custom texture PNGs are supplied by the pack. Recipes, progression,
hammer items and gas profiles are configured through scripts; no gas profiles
are enabled by default.

Use hoppers, funnels and pipes for the sealed mixing vessel; it does not accept
manual bucket or empty-hand transfers. Empty-hand right-click a chain carriage
below a conveyor chain to assemble its load, then right-click the moving carriage
to disassemble. Conveyor speed and direction control travel; unloaded paths pause.

Add recipes in `kubejs/server_scripts/`, using outputs before inputs:

```js
ServerEvents.recipes(event => {
  event.recipes.cwi.mixing_vessel_mixing(
    'minecraft:clay_ball',
    ['2x minecraft:sand', Fluid.of('minecraft:water', 250)]
  ).processingTime(100).minimumRpm(16)
})
```

Processing time uses positive integer ticks. See [script examples](examples/kubejs/)
for hammers, upgrades, gases and advanced recipes. Reload server recipes with
`/reload`; item/fluid registration and startup gas changes require a restart.

## Requirements

- Java 21, Minecraft **1.21.1**, NeoForge **21.1.264–21.1.x**, and Create
  **6.0.10–6.0.x**, including Create's own dependencies.
- Optional: KubeJS **2101.7.2 builds 377 or 381** for scripting, JEI
  **19.64.0.455–19.x** for recipe displays, and ProbeJS **8.0.3** for declarations.
- Addon integrations are version-sensitive; accepted versions are declared in
  [mod metadata](src/main/resources/META-INF/neoforge.mods.toml). Installing an
  arbitrary newer addon version is not supported automatically.

Core is designed for CWI's scripts and external artwork. Create Builder JS,
Worldgen, Visual, Menu and Optimization are separate mods. Reserved protective
enchantments have no effect until a corresponding hazard is implemented.

## License

Core's original code is licensed under **GPL-3.0-only**: GNU General Public
License **version 3 only**, not any later version. See [LICENSE](LICENSE).

Third-party code, resource definitions and audio retain their separate upstream
terms and attribution. See the [bundled notices](src/main/resources/META-INF/),
including MIT-licensed material and separately licensed audio. These materials
are not relicensed by Core's GPL notice; custom pack textures remain external.
