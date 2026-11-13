"""Fail closed unless the scan namespace has only loopback networking."""

import errno
from pathlib import Path
import socket


def verify_isolation():
    # Some kernels expose dormant tunnel devices even in a "none" namespace.
    interfaces = {
        name for _, name in socket.if_nameindex()
        if int(Path(f"/sys/class/net/{name}/flags").read_text().strip(), 16) & 1
    }
    if interfaces != {"lo"}:
        raise RuntimeError(f"Scan namespace has active non-loopback interfaces: {sorted(interfaces)}")
    for host in ("1.1.1.1", "169.254.169.254", "172.17.0.1"):
        try:
            connection = socket.create_connection((host, 80), timeout=2)
        except OSError as error:
            if error.errno not in (errno.ENETUNREACH, errno.EHOSTUNREACH):
                raise RuntimeError(f"Isolation probe to {host} did not fail as unreachable") from error
        else:
            connection.close()
            raise RuntimeError(f"Unexpected network access to {host}")
    print("PASS: loopback-only namespace; public, metadata and host-bridge addresses unreachable.")


if __name__ == "__main__":
    verify_isolation()
