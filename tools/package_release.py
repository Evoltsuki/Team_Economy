"""Build a public GitHub release: four mod JARs, source ZIP and SHA256SUMS.

Private notes, development backups and QA output never enter the source manifest.
The version is read from gradle.properties and is never changed by this tool.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from verify_release import TARGETS, verify

ROOT = Path(__file__).resolve().parents[1]
VERSION = next(line.split('=', 1)[1].strip() for line in
               (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
               if line.startswith('mod_version='))
ROOT_FILES = ('README.md', 'README_EN.md', 'LICENSE', 'NOTICE.md', 'CREDITS.md',
              'TEMPLATE_LICENSE.txt', 'build.gradle', 'settings.gradle',
              'gradle.properties', 'gradlew', 'gradlew.bat', 'requirements.txt',
              '.gitignore', '.gitattributes', 'build-mod.bat')
PUBLIC_DIRS = ('src', 'gradle', '.github', 'docs', 'tools', 'versions')
PRIVATE_NAMES = {'README_MCBBS.md', 'PROGRESS.md', '待办事项.txt', '建模交付规范.md', 'modlist.txt'}


def source_files():
    paths = [ROOT / name for name in ROOT_FILES]
    for folder in PUBLIC_DIRS:
        paths.extend(p for p in (ROOT / folder).rglob('*') if p.is_file()
                     and not any(part in {'__pycache__', '.cache', '.gradle', '.git', '.idea', '.vscode', 'build', 'run', 'runs', 'logs'} for part in p.relative_to(ROOT).parts)
                     and p.suffix not in {'.pyc', '.log', '.tmp', '.bak', '.key', '.pem', '.jks', '.keystore'}
                     and not p.name.startswith('.env')
                     and p.name != 'local.properties')
    for path in paths:
        if not path.is_file() or path.is_symlink():
            raise ValueError(f'Missing or linked public source: {path}')
        if path.name in PRIVATE_NAMES:
            raise ValueError(f'Private document in public source: {path}')
    return sorted(set(paths))


def stage(target):
    target = target.resolve()
    if target == ROOT or ROOT.is_relative_to(target):
        raise ValueError('Staging must not overwrite the project or its ancestors')
    if target.is_relative_to(ROOT) and not target.is_relative_to(ROOT / 'build'):
        raise ValueError('Stage inside build/ or outside the working project')
    if target.exists() and any(target.iterdir()):
        raise ValueError('Use an empty staging directory to prevent stale files')
    paths = source_files()
    target.mkdir(parents=True, exist_ok=True)
    for path in paths:
        out = target / path.relative_to(ROOT)
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, out)
    return target


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package(offline=False, java17=None):
    # A failed build or resource check must never publish a stale JAR.
    wrapper = [str(ROOT / 'gradlew.bat')] if sys.platform == 'win32' else ['bash', str(ROOT / 'gradlew')]
    jars, checks, tests = [], [], {}
    for loader, mc, output, options in TARGETS:
        args = wrapper + options + ['build', '--no-daemon', '--no-configuration-cache']
        if offline: args.append('--offline')
        if loader == 'forge' and mc == '1.20.1' and java17:
            args.append('-Porg.gradle.java.installations.paths=' + str(java17.resolve()))
        subprocess.run(args, cwd=ROOT, check=True)
        jar = ROOT / output / 'libs' / f'teamecon-{loader}-{mc}-{VERSION}.jar'
        checks.append(verify(jar, loader, mc, VERSION))
        jars.append(jar)
        if loader == 'neoforge':
            suites = [ET.parse(p).getroot() for p in (ROOT/output/'test-results/test').glob('TEST-*.xml')]
            count = sum(int(s.attrib['tests']) for s in suites)
            if not count or any(int(s.attrib[k]) for s in suites for k in ('failures', 'errors', 'skipped')):
                raise ValueError('Release requires passing unit test reports: ' + mc)
            tests[f'{loader}-{mc}'] = count
    subprocess.run([sys.executable, str(ROOT / 'tools/verify_assets.py'), '--jar', str(jars[0])], cwd=ROOT, check=True)

    dest = ROOT / 'release' / VERSION
    source_name = f'teamecon-{VERSION}-github-source.zip'
    expected = {jar.name for jar in jars} | {source_name, 'SHA256SUMS.txt'}
    # Only the public Chinese and English READMEs enter the source ZIP.
    if dest.exists() and any(p.name not in expected or not p.is_file() for p in dest.iterdir()):
        raise ValueError(f'Archive old/unexpected contents of {dest} before packaging')
    with tempfile.TemporaryDirectory(prefix='public-release-', dir=ROOT / 'build') as temp:
        temp = Path(temp)
        source = stage(temp / 'source')
        artifact_dir = temp / 'artifacts'
        artifact_dir.mkdir()
        for jar in jars: shutil.copy2(jar, artifact_dir / jar.name)
        with zipfile.ZipFile(artifact_dir / source_name, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(source.rglob('*')):
                if path.is_file():
                    archive.write(path, f'Team_Economy-{VERSION}/' + path.relative_to(source).as_posix())
        (artifact_dir / 'SHA256SUMS.txt').write_text(''.join(
            f'{sha(artifact_dir / name)}  {name}\n' for name in [jar.name for jar in jars] + [source_name]), encoding='utf-8')
        dest.mkdir(parents=True, exist_ok=True)
        for path in artifact_dir.iterdir():
            shutil.copy2(path, dest / path.name)
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    for jar in jars:
        shutil.copy2(jar, dist/jar.name)
        (dist/(jar.name+'.sha256')).write_text(sha(jar)+'  '+jar.name+'\n', encoding='utf-8')
    record = {'version': VERSION, 'checks': checks,
              'tests': tests, 'source_files': len(source_files()),
              'artifacts': sorted(expected)}
    (ROOT / 'build/release-delivery.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(record, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path, help='Copy only public source into an empty directory')
    parser.add_argument('--offline', action='store_true', help='Build using cached Gradle dependencies')
    parser.add_argument('--java17', type=Path, help='Optional local JDK 17 path for Forge 1.20.1')
    args = parser.parse_args()
    if args.stage:
        print(stage(args.stage))
    else:
        package(args.offline, args.java17)
