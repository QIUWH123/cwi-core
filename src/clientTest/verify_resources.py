"""Validate bundled JSON references against pinned mods and the declared external texture contract."""
from contextlib import ExitStack
from pathlib import Path
import json
import os
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CONFIG = json.loads((ROOT / 'build/client-contracts/environment.json').read_text())
entries = {}
errors = []
missing = set()
references = 0
with ExitStack() as stack:
    for path in map(Path, CONFIG['classpath'].split(os.pathsep)):
        if path.is_file() and path.suffix == '.jar':
            jar = stack.enter_context(zipfile.ZipFile(path))
            for name in jar.namelist():
                if name.startswith('assets/') and not name.endswith('/'):
                    entries[name] = (jar, name)
    assets = ROOT / 'src/main/resources/assets'
    for path in assets.rglob('*'):
        if path.is_file() and '/textures/' not in str(path):
            entries['assets/' + path.relative_to(assets).as_posix()] = path

    def read(path):
        entry = entries[path]
        return entry.read_bytes() if isinstance(entry, Path) else entry[0].read(entry[1])

    def resource(value, folder, suffix):
        namespace, _, name = value.partition(':') if ':' in value else ('minecraft', ':', value)
        return f'assets/{namespace}/{folder}/{name}{suffix}'

    def require(path, owner):
        global references
        references += 1
        if path not in entries:
            if '/textures/' in path:
                missing.add(path)
            else:
                errors.append(f'{owner}: missing {path}')
        return path in entries

    models = {}

    def model(path, visiting=()):
        if path in models:
            return models[path]
        if path in visiting:
            errors.append(f'Cyclic model parent: {visiting + (path,)}')
            return {}
        if not require(path, visiting[-1] if visiting else path):
            return {}
        data = json.loads(read(path))
        inherited = {}
        parent = data.get('parent')
        if parent and not parent.startswith('minecraft:builtin/') and not parent.startswith('builtin/'):
            inherited = model(resource(parent, 'models', '.json'), visiting + (path,))
        textures = {**inherited, **data.get('textures', {})}
        for key in textures:
            value = textures[key]
            seen = {key}
            while value.startswith('#'):
                target = value[1:]
                if target in seen or target not in textures:
                    # Parent templates may intentionally require bindings from a child.
                    value = ''
                    break
                seen.add(target)
                value = textures[target]
            if value:
                require(resource(value, 'textures', '.png'), path)
        for override in data.get('overrides', []):
            if 'model' in override:
                require(resource(override['model'], 'models', '.json'), path)
        models[path] = textures
        return textures

    def block_models(data, owner):
        if isinstance(data, dict):
            for key, value in data.items():
                if key == 'model' and isinstance(value, str):
                    require(resource(value, 'models', '.json'), owner)
                else:
                    block_models(value, owner)
        elif isinstance(data, list):
            for value in data:
                block_models(value, owner)

    for path in sorted(assets.rglob('*.json')):
        key = 'assets/' + path.relative_to(assets).as_posix()
        data = json.loads(path.read_text())
        if '/models/' in key:
            model(key)
        elif '/blockstates/' in key:
            block_models(data, key)
        elif '/particles/' in key:
            for texture in data.get('textures', []):
                require(resource(texture, 'textures/particle', '.png'), key)
        elif '/atlases/' in key:
            for source in data.get('sources', []):
                if source.get('type') in ('single', 'minecraft:single'):
                    require(resource(source['resource'], 'textures', '.png'), key)
        elif key.endswith('/sounds.json'):
            for event in data.values():
                for sound in event.get('sounds', []):
                    if isinstance(sound, str):
                        require(resource(sound, 'sounds', '.ogg'), key)
                    elif sound.get('type', 'file') == 'file':
                        require(resource(sound['name'], 'sounds', '.ogg'), key)

    if '--inventory' in sys.argv:
        print(json.dumps(sorted(missing), indent=2))
    else:
        contract = json.loads((ROOT / 'gradle/external-resources.json').read_text())
        expected = set(contract['references'])
        errors.extend(f'Undeclared missing texture: {path}' for path in sorted(missing - expected))
        errors.extend(f'Obsolete external texture declaration: {path}' for path in sorted(expected - missing))
        external = CONFIG.get('assets')
        if external:
            for name in expected | set(contract.get('runtime', [])):
                if not (Path(external) / name.removeprefix('assets/')).is_file():
                    errors.append(f'External texture missing from {external}: {name}')
        print(f'Resource definitions: {len(models)} models, {references} references, {len(expected)} declared external textures; external files ' + ('checked' if external else 'not supplied'))
    if errors:
        raise SystemExit('\n'.join(errors))
