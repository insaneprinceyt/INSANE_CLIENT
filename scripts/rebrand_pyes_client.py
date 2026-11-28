#!/usr/bin/env python3
"""Deterministically rebrand the pinned, MIT-labelled PYES binary without executing it.

Only String constants, display metadata, six PNGs, translations and the update
channel change. Java names, instructions, mixins, module/config keys and nested
mods remain untouched. This is a branding patch, not reconstructed Java source.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SUPPORT = ROOT / 'third_party/pyes-client'
UPSTREAM_SHA256 = '02095d719da26fa756fe4239025415c60c881d7f18831759a3af764505f7f3ee'
UPSTREAM_BYTES = 38840795
TEXTURES = {
    'assets/pyesclient/icon.png': 'icon.png',
    'assets/pyesclient/textures/gui/pyes_logo.png': 'logo.png',
    'assets/pyesclient/textures/gui/king/pyes_logo_tight.png': 'logo-tight.png',
    'assets/pyesclient/textures/gui/pyes_wordmark.png': 'wordmark.png',
    'assets/pyesclient/textures/gui/pyes_start_panel.png': 'panel.png',
    'assets/pyesclient/textures/gui/pyes_start_panel_glow.png': 'panel-glow.png',
}
# Exact strings only. In particular, "PYES Client Watermark", "PYES Player",
# "PYES Inventory", "PYES Freelook", "PYES Shulker", "PYES Keys" and theme
# option values are persisted identifiers, not safe global replacement targets.
DISPLAY = {
    b'PYES Client': b'INSANE CLIENT',
    b'PYES Client v1.1.2': b'INSANE CLIENT v1.1.2',
    b'PYES HUD Editor': b'INSANE CLIENT HUD Editor',
    b'PYES Menu': b'INSANE CLIENT',
    b'PYES Waypoints': b'INSANE CLIENT Waypoints',
    b'PYES Showcase Workshop': b'INSANE CLIENT Showcase Workshop',
    b'PYES red/orange identity': b'INSANE CLIENT appearance',
    b'Open the PYES hand-draw editor.': b'Open the INSANE CLIENT hand-draw editor.',
    b'Toggle and resize the PYES wordmark on pause and inventory screens.':
        b'Toggle and resize the INSANE CLIENT wordmark on pause and inventory screens.',
    b'PYES world GUI failed; returning to normal menu':
        b'INSANE CLIENT world GUI failed; returning to normal menu',
    b'PYES Updates': b'INSANE CLIENT Updates',
    b'PYES  /  CLIENT UPDATES': b'INSANE CLIENT  /  UPDATES',
    b'Opening PYES Store & Community Discord...': b'Opening the upstream PYES community Discord...',
}


def require(value, message):
    if not value:
        raise ValueError(message)


def json_bytes(value):
    return (json.dumps(value, indent=2, ensure_ascii=True) + '\n').encode('utf-8')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def parse_pool(data):
    """Return constant-pool records and body without decoding modified UTF-8."""
    require(data[:4] == b'\xca\xfe\xba\xbe', 'Invalid class header')
    count = struct.unpack_from('>H', data, 8)[0]
    records, strings, structural_utf8 = {}, set(), set()
    pos, index = 10, 1
    while index < count:
        start = pos
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, pos)[0]
            pos += 2 + length
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8
        elif tag in (7, 8, 16, 19, 20):
            ref = struct.unpack_from('>H', data, pos)[0]
            (strings if tag == 8 else structural_utf8).add(ref)
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            if tag == 12:
                structural_utf8.update(struct.unpack_from('>HH', data, pos))
            pos += 4
        elif tag == 15:
            pos += 3
        else:
            raise ValueError('Unknown constant-pool tag: ' + str(tag))
        require(pos <= len(data), 'Truncated class constant pool')
        records[index] = data[start:pos]
        index += 2 if tag in (5, 6) else 1
    return records, strings, structural_utf8, data[pos:]


def display_text(old):
    if old in DISPLAY:
        return DISPLAY[old]
    if old.startswith(b'[PYES Client] '):
        return old.replace(b'[PYES Client]', b'[INSANE CLIENT]', 1)
    if old.startswith(b'=== PYES Client Custom Wallpaper Guide ==='):
        return old.replace(b'PYES Client', b'INSANE CLIENT', 1)
    return old


def patch_class(data):
    records, strings, structural, body = parse_pool(data)
    changes = []
    for index in sorted(strings):
        record = records[index]
        require(record[0] == 1, 'String constant does not reference UTF8')
        old = record[3:]
        new = display_text(old)
        if old == new:
            continue
        require(index not in structural, 'Display string shared with a structural identifier')
        require(len(new) <= 65535, 'String exceeds constant-pool length limit')
        records[index] = b'\x01' + struct.pack('>H', len(new)) + new
        changes.append({'before': old.decode('ascii'), 'after': new.decode('ascii')})
    patched = data[:10] + b''.join(records.values()) + body
    after, after_strings, after_structural, after_body = parse_pool(patched)
    require(after_body == body and after_strings == strings and after_structural == structural,
            'Class structure changed')
    require(after.keys() == records.keys(), 'Constant-pool indices changed')
    return patched, changes


def png_dimensions(data):
    require(data.startswith(b'\x89PNG\r\n\x1a\n') and len(data) >= 24, 'Invalid PNG')
    return struct.unpack_from('>II', data, 16)


def rebrand(source, destination):
    original = source.read_bytes()
    require(len(original) == UPSTREAM_BYTES and digest(original) == UPSTREAM_SHA256,
            'Input is not the verified PYES Client artifact; refusing to patch')
    require(source.resolve() != destination.resolve(), 'Never overwrite the upstream JAR')
    report = {'upstream_sha256': UPSTREAM_SHA256, 'classes': {}, 'textures': {}, 'unchanged_nested_jars': {}}
    with zipfile.ZipFile(source) as archive:
        require(archive.testzip() is None, 'Corrupt input archive')
        names = archive.namelist()
        require(len(names) == len(set(names)), 'Duplicate archive entries')
        require(not any(n.startswith('/') or '..' in Path(n).parts for n in names), 'Unsafe archive name')
        require(not any(n.upper().endswith(('.SF', '.RSA', '.DSA', '.EC')) and n.startswith('META-INF/')
                        for n in names), 'Signed upstream artifact requires a different release process')
        files = {n: archive.read(n) for n in names if not n.endswith('/')}
        metadata = json.loads(files['fabric.mod.json'])
        require(metadata['id'] == 'pyesclient' and metadata['version'] == '1.1.2'
                and metadata['name'] == 'PYES Client' and metadata['license'] == 'MIT',
                'Unexpected client metadata')
        require(metadata['depends'] == {'fabricloader': '>=0.19.2', 'minecraft': '1.21.11',
                                       'java': '>=21', 'fabric-api': '*'}, 'Unexpected dependencies')
        metadata['name'] = 'INSANE CLIENT'
        metadata['description'] = ('INSANE CLIENT: HUD modules, inventory tools and a client dashboard for Minecraft 1.21.11. '
                                   'Based on PYES Client by PYES_KING; branding and launcher integration by INSANE CLIENT.')
        files['fabric.mod.json'] = json_bytes(metadata)
        # Keep UpdateClient entrypoint: it registers core controls/dashboard too.
        # Its connected() guards startup/manual checks, promotions and update timer.
        files['pyes-update-channel.json'] = json_bytes({'manifestUrl': '', 'publicKey': ''})
        translation = json.loads(files['assets/pyesclient/lang/en_us.json'])
        translation['key.pyesclient.open'] = 'Open INSANE CLIENT'
        translation['key.category.pyesclient.category'] = 'INSANE CLIENT'
        files['assets/pyesclient/lang/en_us.json'] = json_bytes(translation)
        for name, data in list(files.items()):
            if name.endswith('.class') and name.startswith('dev/pyesclient/'):
                patched, changes = patch_class(data)
                if changes:
                    files[name] = patched
                    report['classes'][name] = changes
        for name, replacement in TEXTURES.items():
            data = (SUPPORT / 'branding' / replacement).read_bytes()
            require(png_dimensions(data) == png_dimensions(files[name]), 'Texture canvas changed: ' + name)
            files[name] = data
            report['textures'][name] = digest(data)
        files['LICENSE_PYES_CLIENT'] = (SUPPORT / 'LICENSE').read_bytes()
        files['INSANE-CLIENT-NOTICE.txt'] = (SUPPORT / 'NOTICE.txt').read_bytes()
        require(len(report['classes']) >= 8, 'Expected client display strings were not patched')
        for name in names:
            if name.endswith('.jar') or name in ('THIRD-PARTY-NOTICES.txt', 'LICENSE_MouseTweaks'):
                require(files[name] == archive.read(name), 'Bundled component/notice changed: ' + name)
                if name.endswith('.jar'):
                    report['unchanged_nested_jars'][name] = digest(files[name])
        # A fixed timestamp, ordering and permissions make repeat builds identical.
        destination.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=destination.parent, suffix='.jar', delete=False) as temporary:
            temporary_path = Path(temporary.name)
        try:
            with zipfile.ZipFile(temporary_path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
                for name in sorted(files):
                    info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.create_system = 3
                    info.external_attr = 0o100644 << 16
                    output.writestr(info, files[name], compresslevel=9)
            with zipfile.ZipFile(temporary_path) as output:
                require(output.testzip() is None, 'Output CRC validation failed')
                require(set(output.namelist()) == set(files), 'Output file inventory changed')
                for name in files:
                    require(output.read(name) == files[name], 'Output readback changed: ' + name)
            temporary_path.replace(destination)
        finally:
            temporary_path.unlink(missing_ok=True)
    report['output_sha256'] = digest(destination.read_bytes())
    report['output_bytes'] = destination.stat().st_size
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    report = rebrand(args.upstream, args.output)
    if args.report:
        args.report.write_bytes(json_bytes(report))
    print(json.dumps({'sha256': report['output_sha256'], 'bytes': report['output_bytes'],
                      'changed_classes': len(report['classes']), 'replaced_textures': len(report['textures']),
                      'unchanged_nested_jars': len(report['unchanged_nested_jars'])}))


if __name__ == '__main__':
    main()
