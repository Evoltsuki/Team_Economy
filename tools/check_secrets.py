"""Reject credential files and recognizable tokens without printing their values."""
from pathlib import PurePosixPath
import argparse
import re
import subprocess
import sys

SECRET_DIRS = {'.secrets', '.credentials', 'credentials', 'secrets'}
SECRET_SUFFIXES = {'.dpapi', '.key', '.pem', '.p12', '.pfx', '.jks', '.keystore'}
PATTERNS = (
    ('Modrinth token', re.compile(rb'mrp_[A-Za-z0-9]{30,}')),
    ('GitHub token', re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})')),
    ('private key', re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----')),
    ('literal API credential', re.compile(
        rb'''(?i)["']?(?:api[_-]?(?:key|token)|access[_-]?token|modrinth[_-]?token|curseforge[_-]?token)["']?\s*[:=]\s*["']([A-Za-z0-9_./+$=-]{20,})["']''')),
)


def sensitive_path(name):
    path = PurePosixPath(name.replace('\\', '/').lower())
    return (bool(set(path.parts) & SECRET_DIRS) or path.suffix in SECRET_SUFFIXES
            or (path.name.startswith('.env') and path.name != '.env.example')
            or bool(re.fullmatch(r'(?:credentials|api[_-]?keys|api[_-]?tokens|tokens|secrets)(?:\.[\w-]+)+', path.name)))


def findings(name, data):
    if sensitive_path(name):
        yield name, 0, 'credential file'
    if b'\0' not in data:
        for label, pattern in PATTERNS:
            for match in pattern.finditer(data):
                yield name, data[:match.start()].count(b'\n') + 1, label


def git(*args):
    return subprocess.check_output(['git', *args])


def index_blobs(names):
    entries = {}
    for entry in git('ls-files', '--stage', '-z').split(b'\0'):
        if not entry:
            continue
        info, name = entry.split(b'\t', 1)
        _, digest, stage = info.split()
        if stage == b'0':
            entries[name.decode('utf-8')] = digest
    selected = [(name, entries[name]) for name in names]
    result = subprocess.run(['git', 'cat-file', '--batch'],
                            input=b''.join(digest + b'\n' for _, digest in selected),
                            capture_output=True, check=True).stdout
    offset = 0
    for name, _ in selected:
        end = result.index(b'\n', offset)
        size = int(result[offset:end].split()[-1])
        offset = end + 1
        yield name, result[offset:offset + size]
        offset += size + 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--staged', action='store_true', help='Check only staged additions and modifications')
    args = parser.parse_args()
    names = (git('diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z') if args.staged
             else git('ls-files', '-z')).decode('utf-8').strip('\0').split('\0')
    errors = []
    for name, data in index_blobs(list(filter(None, names))):
        # Read the index: an unstaged edit cannot conceal a staged credential.
        errors.extend(findings(name, data))
    for name, line, reason in errors:
        print(f'BLOCKED {name}:{line}: {reason}; credential value withheld.', file=sys.stderr)
    if errors:
        return 1
    print(f'Credential check passed ({len(list(filter(None, names)))} files).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
