"""Rebuild a file the Understory Drop Box sent in pieces.

Usage: python3 reassemble_dropbox.py <row.json> <dir-with-downloaded-pieces> <out-file>

<row.json> is the drop box `files` row (ArtifactData get/list). Each piece was
fetched with the Artifact tool (read, path=<piece id>) and saved as
<piece id>.<ext> in the directory. Every piece starts with the 8-byte header
"USCHUNK1"; the row's `hashes` hold the SHA-256 of each piece's payload.
"""
import glob
import hashlib
import json
import os
import sys


def main(row_path, piece_dir, out_path):
    row = json.load(open(row_path))
    row = row.get('data', row)
    header = row.get('header', 'USCHUNK1').encode()
    total = 0
    with open(out_path, 'wb') as out:
        for n, pid in enumerate(row['chunks']):
            match = glob.glob(os.path.join(piece_dir, pid + '.*')) or glob.glob(os.path.join(piece_dir, pid))
            if not match:
                sys.exit(f'missing piece {n + 1}/{len(row["chunks"])}: {pid}')
            data = open(match[0], 'rb').read()
            if not data.startswith(header):
                sys.exit(f'piece {n + 1} lacks the header')
            payload = data[len(header):]
            want = (row.get('hashes') or [None] * len(row['chunks']))[n]
            if want and hashlib.sha256(payload).hexdigest() != want:
                sys.exit(f'piece {n + 1} failed its checksum')
            out.write(payload)
            total += len(payload)
    if total != row['bytes']:
        sys.exit(f'size mismatch: rebuilt {total} bytes, expected {row["bytes"]}')
    print(f'rebuilt {row["name"]}: {total} bytes from {len(row["chunks"])} piece(s), all checksums OK')


if __name__ == '__main__':
    main(*sys.argv[1:4])
