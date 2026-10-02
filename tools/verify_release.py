"""Verify loader/version, bytecode and exact resources of one distribution JAR."""
from pathlib import Path
import argparse
import struct
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = (
    ('neoforge', '1.21.1', 'build', []),
    ('neoforge', '1.21', 'build/neoforge-1.21', ['-PmcTarget=1.21']),
    ('forge', '1.20.1', 'build/forge-1.20.1', ['-p', 'versions/forge', '-Pmc=1.20.1']),
    ('forge', '1.21.1', 'build/forge-1.21.1', ['-p', 'versions/forge', '-Pmc=1.21.1']),
)

def verify(jar, loader, mc, version):
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        meta_path = 'META-INF/' + ('neoforge.mods.toml' if loader == 'neoforge' else 'mods.toml')
        other = 'META-INF/' + ('mods.toml' if loader == 'neoforge' else 'neoforge.mods.toml')
        assert other not in names, 'Mixed loader metadata'
        meta = tomllib.loads(z.read(meta_path).decode('utf-8'))
        license_id = next(line.split('=', 1)[1].strip() for line in
                          (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                          if line.startswith('mod_license='))
        assert meta['license'] == license_id, 'Stale license metadata'
        for name in ('LICENSE', 'NOTICE.md', 'CREDITS.md'):
            assert z.read(name) == (ROOT / name).read_bytes(), 'Stale legal notice: ' + name
        mod = meta['mods'][0]
        assert (mod['modId'], mod['version'], mod['authors']) == ('teamecon', version, '洁柔厨')
        deps = {d['modId']: d for d in meta['dependencies']['teamecon']}
        assert loader in deps and deps['minecraft']['versionRange'].startswith('[' + mc)
        if loader == 'neoforge':
            properties = dict(line.split('=', 1) for line in
                              (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                              if '=' in line and not line.lstrip().startswith('#'))
            key = 'neo_version_range_1_21' if mc == '1.21' else 'neo_version_range'
            assert deps[loader]['versionRange'] == properties[key], 'Stale NeoForge compatibility range'
        assert mod['logoFile'] in names
        assert {'LICENSE', 'NOTICE.md', 'CREDITS.md'} <= names
        assert not any('/gametest/' in n or '/qa/' in n or 'qa_empty' in n for n in names)
        assert not any(n.endswith(('PROGRESS.md', '待办事项.txt', '.java')) for n in names)
        classes = [n for n in names if n.endswith('.class')]
        assert classes and all(struct.unpack('>H', z.read(n)[6:8])[0] == (61 if mc == '1.20.1' else 65) for n in classes)
        resources = ROOT / ('src/main/resources' if loader == 'neoforge' else f'build/forge-{mc}/generated/resources')
        expected = {p.relative_to(resources).as_posix(): p for p in resources.rglob('*')
                    if p.is_file() and p.suffix != '.snbt' and p.name != 'qa_empty.nbt'}
        for name, path in expected.items():
            assert name in names and z.read(name) == path.read_bytes(), 'Stale/missing resource: ' + name
        actual = {n for n in names if not n.endswith('/') and n.startswith(('data/', 'assets/'))}
        assert actual == {n for n in expected if n.startswith(('data/', 'assets/'))}, 'Extra data/assets in JAR'
        if mc == '1.20.1':
            assert 'data/teamecon/recipes/guide_book.json' in names
            assert not any(n.startswith('data/teamecon/recipe/') for n in names)
    return {'file': Path(jar).name, 'loader': loader, 'minecraft': mc, 'classes': len(classes)}

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('jar', type=Path)
    p.add_argument('--loader', choices=['forge', 'neoforge'], required=True)
    p.add_argument('--mc', required=True)
    a = p.parse_args()
    version = next(s.split('=', 1)[1] for s in (ROOT/'gradle.properties').read_text().splitlines() if s.startswith('mod_version='))
    print(verify(a.jar, a.loader, a.mc, version))
