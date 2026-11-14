"""Portable native submission/bytecode checks. No window, GPU, instance paths or historical build inputs."""
from pathlib import Path
from contextlib import ExitStack
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/client-contracts'
CONFIG = json.loads((OUT / 'environment.json').read_text())
CP = CONFIG['classpath']
DEPS = json.loads((ROOT / 'gradle/local-dependencies.json').read_text())['artifacts']


def dependency(fragment):
    matches = [ROOT / entry['path'] for entry in DEPS if fragment in Path(entry['path']).name]
    if len(matches) != 1:
        raise RuntimeError(f'Expected one pinned {fragment}: {matches}')
    return matches[0]


def run(name, main, *args, prefix=(), exclude=()):
    cp = os.pathsep.join([*map(str, prefix), *(entry for entry in CP.split(os.pathsep)
                       if not any(part in Path(entry).name for part in exclude))])
    result = subprocess.run([CONFIG['java'], '-cp', cp, main, *map(str, args)], cwd=ROOT,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (OUT / f'{name}.log').write_text(result.stdout)
    if result.returncode:
        print(result.stdout[-9000:])
        raise SystemExit(f'{name} failed: {result.returncode}')
    print(name, 'PASS')


def transform(name, config, access=False, sodium=False):
    destination = OUT / name
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir()
    staged = destination / 'input'
    shutil.copytree(Path(CONFIG['classes']), staged)
    if access:
        with ExitStack() as archives:
            neoforge = next(p for p in map(Path, CP.split(os.pathsep)) if p.name.startswith('neoforge-')
                            and p.is_file() and 'META-INF/accesstransformer.cfg' in archives.enter_context(zipfile.ZipFile(p)).namelist())
        run('access-transformers', 'ApplyInstalledAccessTransformers', staged,
            neoforge, *((dependency('sodium-neoforge-'),) if sodium else ()))
    path = destination / 'mixins.json'
    path.write_text(json.dumps(config))
    configs = [path]
    if sodium:
        with zipfile.ZipFile(dependency('sodium-neoforge-')) as archive:
            for entry in archive.namelist():
                if entry.startswith('net/caffeinemc/mods/sodium/mixin/') and entry.endswith('.class'):
                    target = staged / entry
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(archive.read(entry))
        selected = ['core.model.quad.BakedQuadMixin', 'features.render.frapi.BakedModelMixin',
                    'features.render.frapi.ItemRendererAccessor', 'features.render.frapi.ItemRendererMixin',
                    'features.render.model.item.ItemRendererMixin', 'features.textures.animations.tracking.SpriteContentsMixin']
        extra = destination / 'sodium.mixins.json'
        extra.write_text(json.dumps({'required': True, 'minVersion': '0.8', 'package': 'net.caffeinemc.mods.sodium.mixin',
                                    'compatibilityLevel': 'JAVA_21', 'client': selected, 'injectors': {'defaultRequire': 1}}))
        configs.append(extra)
    run(name + '-transform', 'CheckTargetMixins', staged, dependency('create-1.21.1-'), destination, *configs,
        prefix=(staged,))
    return destination / 'transformed', staged


emissive = json.loads((ROOT / 'src/main/resources/cwi-emissive.mixins.json').read_text())
emissive['client'] = [name for name in emissive['client'] if name != 'SodiumBlockRenderContextMixin']
emissive_classes, access = transform('emissive', emissive, access=True)
run('emissive-behavior', 'EmissiveFixture', prefix=(emissive_classes, access))
run('emissive-without-sodium', 'EmissiveWithoutSodiumFixture', exclude=('sodium', 'fabric-', 'shadowdrop', 'kubejs-', 'rhino-'))
full_emissive = json.loads((ROOT / 'src/main/resources/cwi-emissive.mixins.json').read_text())
sodium_classes, sodium_access = transform('sodium-emissive', full_emissive, access=True, sodium=True)
run('sodium-emissive-behavior', 'SodiumEmissiveFixture', prefix=(sodium_classes, sodium_access))
run('spout-jei', 'WorldSpoutingJeiFixture')
run('machine-jei', 'MachineRecipeJeiFixture')
frame_capture = OUT / 'frame-capture'
run('frame-capture', 'AnimatedJeiFrameFixture', 'capture', frame_capture)
run('animated-frames', 'AnimatedJeiFrameFixture', prefix=(frame_capture,))
capture = OUT / 'spout-capture'
run('spout-capture', 'WorldSpoutingAnimationFixture', 'capture', capture)
run('spout-animation', 'WorldSpoutingAnimationFixture', prefix=(capture,))

integrations = json.loads((ROOT / 'src/main/resources/cwi-recipe-outputs.mixins.json').read_text())
integration_classes, _ = transform('integrations', integrations)
run('recipe-output-layouts', 'RecipeOutputJeiFixture', prefix=(integration_classes,))
run('vintage-renderers', 'VintageRedstoneClientFixture', integration_classes, prefix=(integration_classes,))
capture = OUT / 'rolling-capture'
run('rolling-capture', 'RollingJeiAlignmentFixture', 'capture', capture, prefix=(integration_classes,))
run('rolling-animation', 'RollingJeiAlignmentFixture', prefix=(capture, integration_classes))
processing = json.loads((ROOT / 'src/main/resources/cwi-processing-jei.mixins.json').read_text())
processing_classes, _ = transform('processing', processing)
capture = OUT / 'processing-capture'
run('processing-capture', 'ProcessingJeiFixture', 'capture', capture)
run('processing-behavior', 'ProcessingJeiFixture', prefix=(capture, processing_classes))
run('processing-without-addons', 'ProcessingJeiFixture', 'without-addons', prefix=(capture, processing_classes),
    exclude=('createaddition', 'createdieselgenerators', 'vintageimprovements'))
application = json.loads((ROOT / 'src/main/resources/cwi-item-application.mixins.json').read_text())
application_classes, _ = transform('manual-application', application)
run('manual-application-behavior', 'ManualApplicationJeiFixture', prefix=(application_classes,))
advanced = json.loads((ROOT / 'src/main/resources/cwi-advanced-processing.mixins.json').read_text())
advanced_classes, _ = transform('native-advanced', advanced)
run('native-advanced-jei', 'NativeAdvancedJeiFixture', prefix=(advanced_classes,))

# These native LivingEntity hooks run on both sides; validate client-side injection too.
protection = json.loads((ROOT / "src/main/resources/cwi-protection.mixins.json").read_text())
transform("protective-enchantments", protection)

# Shared Create open endpoint must also transform on the client; providers stay client-only.
pollution = json.loads((ROOT / "src/main/resources/cwi-gas-pollution.mixins.json").read_text())
pollution_classes, _ = transform("gas-pollution", pollution)
run("gas-particles", "GasParticleFixture")
run("gas-container-visuals", "GasContainerVisualFixture", prefix=(pollution_classes,))

pack_policy = json.loads((ROOT / "src/main/resources/cwi-pack-policy.mixins.json").read_text())
pack_policy_classes, _ = transform("pack-policy", pack_policy)
run("experimental-warning", "PackWarningFixture", "world", prefix=(pack_policy_classes,))
run("resource-pack-warnings", "PackWarningFixture", "packs", prefix=(pack_policy_classes,))
weathered = json.loads((ROOT / "src/main/resources/cwi-weathered.mixins.json").read_text())
weathered_classes, weathered_input = transform("weathered-decoration", weathered)
run("weathered-decoration", "WeatheredGirderClientFixture", prefix=(weathered_classes, weathered_input),
    exclude=('bits_n_bobs', 'azimuth', 'struts', 'sable'))
print('Native client contracts passed; graphical-client acceptance remains separate.')
