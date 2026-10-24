"""Writes a Debian-style ar archive (member names without GNU ar's trailing slash), as webOS expects."""
import os, sys, time
out, members = sys.argv[1], sys.argv[2:]
with open(out, 'wb') as f:
    f.write(b'!<arch>\n')
    for path in members:
        data = open(path, 'rb').read()
        name = os.path.basename(path)
        f.write(('%-16s%-12d%-6d%-6d%-8s%-10d`\n' % (name, int(time.time()), 0, 0, '100644', len(data))).encode())
        f.write(data)
        if len(data) % 2:
            f.write(b'\n')
