"""Keep publication tokens encrypted with the current Windows user's DPAPI key.

Use `set modrinth`, `set curseforge`, or `status`. No command prints a token.
"""
from pathlib import Path
import argparse
import ctypes
from ctypes import wintypes
import getpass
import os

PROVIDERS = ('modrinth', 'curseforge')


def directory():
    if os.name != 'nt':
        raise RuntimeError('This credential store requires Windows DPAPI.')
    return Path(os.environ['LOCALAPPDATA']) / 'TeamEconomy' / 'credentials'


class Blob(ctypes.Structure):
    _fields_ = [('size', wintypes.DWORD), ('data', ctypes.POINTER(ctypes.c_ubyte))]


def crypt(data, decrypt=False):
    buffer = ctypes.create_string_buffer(data)
    source = Blob(len(data), ctypes.cast(buffer, ctypes.POINTER(ctypes.c_ubyte)))
    destination = Blob()
    api = ctypes.WinDLL('crypt32', use_last_error=True)
    free = ctypes.WinDLL('kernel32', use_last_error=True).LocalFree
    free.argtypes = [ctypes.c_void_p]
    free.restype = ctypes.c_void_p
    operation = api.CryptUnprotectData if decrypt else api.CryptProtectData
    operation.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p,
                          ctypes.c_void_p, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(Blob)]
    operation.restype = wintypes.BOOL
    if not operation(ctypes.byref(source), None, None, None, None, 1, ctypes.byref(destination)):
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        return ctypes.string_at(destination.data, destination.size)
    finally:
        free(destination.data)


def path(provider):
    if provider not in PROVIDERS:
        raise ValueError('Unknown credential provider')
    return directory() / (provider + '.dpapi')


def store(provider, token):
    token = token.strip()
    if len(token) < 20 or any(c.isspace() for c in token):
        raise ValueError('Token is empty or malformed; no value logged.')
    if provider == 'modrinth' and not token.startswith('mrp_'):
        raise ValueError('Expected a Modrinth personal access token; no value logged.')
    target = path(provider)
    target.parent.mkdir(parents=True, exist_ok=True)
    ciphertext = crypt(token.encode('utf-8'))
    temporary = target.with_suffix('.dpapi.tmp')
    temporary.write_bytes(ciphertext)
    os.replace(temporary, target)
    assert load(provider) == token


def load(provider):
    return crypt(path(provider).read_bytes(), decrypt=True).decode('utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('set', 'status'))
    parser.add_argument('provider', nargs='?', choices=PROVIDERS)
    args = parser.parse_args()
    if args.action == 'status':
        for provider in PROVIDERS:
            print(provider + ': ' + ('configured' if path(provider).is_file() else 'not configured'))
        print('Encrypted directory: ' + str(directory()))
    else:
        if not args.provider:
            parser.error('set requires a provider')
        store(args.provider, getpass.getpass(args.provider + ' token (hidden): '))
        print('Credential encrypted and saved for the current Windows user.')


if __name__ == '__main__':
    main()
