import errno
import unittest
from unittest.mock import patch

import zap_verify_isolation as isolation


class ZapIsolationTest(unittest.TestCase):
    def test_nonloopback_interface_fails_before_network_probes(self):
        with patch.object(isolation.Path, "read_text", return_value="0x1"), \
                patch.object(isolation.socket, "if_nameindex", return_value=[(1, "lo"), (2, "eth0")]), \
                patch.object(isolation.socket, "create_connection") as connect:
            with self.assertRaisesRegex(RuntimeError, "non-loopback"):
                isolation.verify_isolation()
            connect.assert_not_called()

    def test_unreachable_destinations_pass_but_timeouts_do_not(self):
        with patch.object(isolation.Path, "read_text", return_value="0x1"), \
                patch.object(isolation.socket, "if_nameindex", return_value=[(1, "lo")]), \
                patch.object(isolation.socket, "create_connection",
                             side_effect=OSError(errno.ENETUNREACH, "unreachable")):
            isolation.verify_isolation()
        with patch.object(isolation.Path, "read_text", return_value="0x1"), \
                patch.object(isolation.socket, "if_nameindex", return_value=[(1, "lo")]), \
                patch.object(isolation.socket, "create_connection", side_effect=TimeoutError()):
            with self.assertRaisesRegex(RuntimeError, "did not fail as unreachable"):
                isolation.verify_isolation()

    def test_any_successful_external_connection_fails(self):
        with patch.object(isolation.Path, "read_text", return_value="0x1"), \
                patch.object(isolation.socket, "if_nameindex", return_value=[(1, "lo")]), \
                patch.object(isolation.socket, "create_connection") as connect:
            with self.assertRaisesRegex(RuntimeError, "Unexpected network access"):
                isolation.verify_isolation()
            connect.return_value.close.assert_called_once()

    def test_down_tunnel_devices_do_not_count_as_external_connectivity(self):
        with patch.object(isolation.Path, "read_text", side_effect=["0x1", "0x80"]), \
                patch.object(isolation.socket, "if_nameindex", return_value=[(1, "lo"), (2, "tunl0")]), \
                patch.object(isolation.socket, "create_connection",
                             side_effect=OSError(errno.ENETUNREACH, "unreachable")):
            isolation.verify_isolation()


if __name__ == "__main__":
    unittest.main()
