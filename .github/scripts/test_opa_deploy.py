#!/usr/bin/env python3
"""Exercise the actual reusable workflow's Helm deployment step."""

import base64
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHART = Path("deployment/helm/laa-data-user-api")


class OpaDeploymentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.helm = shutil.which("helm")
        cls.yq = shutil.which("yq")
        cls.docker = shutil.which("docker")
        if not cls.helm or not cls.yq:
            raise RuntimeError("helm and yq are required")
        if os.environ.get("TEST_REAL_DOCKER") == "1" and not cls.docker:
            raise RuntimeError("docker is required for the real bundle test")
        cls.deploy_step = subprocess.check_output(
            [cls.yq, "-r", '.jobs.deploy.steps[] | select(.name == "Deploy to Cloud Platform") | .run',
             str(ROOT / ".github/workflows/reusable-helm-deploy.yml")],
            text=True,
        )

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.workspace = Path(self.temp.name)
        self.chart = self.workspace / CHART
        shutil.copytree(ROOT / CHART, self.chart)
        shutil.copytree(ROOT / "opa/policies", self.workspace / "opa/policies")
        self.generated = self.chart / "values-secrets.generated.yaml"
        self.generated.write_text("{}\n")
        self.bin = self.workspace / "bin"
        self.bin.mkdir()
        self.output = self.workspace / "rendered.yaml"
        self.docker_log = self.workspace / "docker.log"
        self._write_command("helm", """\
import os, subprocess, sys
args = sys.argv[1:]
assert args[:2] == ["upgrade", "--install"], args
args = args[2:]
args.remove("--atomic")
timeout = args.index("--timeout")
del args[timeout:timeout + 2]
with open(os.environ["TEST_HELM_OUTPUT"], "w") as output:
    subprocess.run([os.environ["TEST_REAL_HELM"], "template", *args], stdout=output, check=True)
""")
        self._write_command("docker", """\
import os, pathlib, subprocess, sys
pathlib.Path(os.environ["TEST_DOCKER_LOG"]).touch()
if os.environ.get("TEST_FAIL_DOCKER") == "1":
    sys.exit(1)
if os.environ.get("TEST_REAL_DOCKER") == "1":
    sys.exit(subprocess.call([os.environ["TEST_DOCKER"], *sys.argv[1:]]))
bundle_dir = next(arg.split(":", 1)[0] for arg in sys.argv if arg.endswith(":/bundle"))
if os.environ.get("TEST_OPA_BINARY"):
    policies = next(arg[:-len(":/policies:ro")] for arg in sys.argv if arg.endswith(":/policies:ro"))
    assert sys.argv[-6:] == ["openpolicyagent/opa:0.68.0", "build", "-b", "-o",
                             "/bundle/bundle.tar.gz", "/policies"], sys.argv
    sys.exit(subprocess.call([os.environ["TEST_OPA_BINARY"], "build", "-b", "-o",
                              str(pathlib.Path(bundle_dir, "bundle.tar.gz")), policies]))
pathlib.Path(bundle_dir, "bundle.tar.gz").write_bytes(pathlib.Path(os.environ["TEST_BUNDLE"]).read_bytes())
""")
        # A local fixture is used when Docker is unavailable; CI builds the real bundle.
        self.bundle = self.workspace / "fixture.tar.gz"
        with tarfile.open(self.bundle, "w:gz") as archive:
            data = (ROOT / "opa/policies/authz/lifecycle.rego").read_bytes()
            info = tarfile.TarInfo("authz/lifecycle.rego")
            info.size = len(data)
            archive.addfile(info, io.BytesIO(data))

    def _write_command(self, name, body):
        path = self.bin / name
        path.write_text("#!/usr/bin/env python3\n" + body)
        path.chmod(0o755)

    def run_deploy(self, **overrides):
        env = os.environ.copy()
        env.update({
            "PATH": f"{self.bin}{os.pathsep}{env['PATH']}",
            "GITHUB_WORKSPACE": str(self.workspace),
            "RUNNER_TEMP": str(self.workspace),
            "KUBE_NAMESPACE": "test",
            "HELM_VALUES_ENV": "dev",
            "TEST_REAL_HELM": self.helm,
            "TEST_HELM_OUTPUT": str(self.output),
            "TEST_DOCKER": self.docker or "",
            "TEST_DOCKER_LOG": str(self.docker_log),
            "TEST_BUNDLE": str(self.bundle),
        })
        env.pop("OPA_ENABLED", None)
        env.update(overrides)
        return subprocess.run(
            ["bash", "-e", "-c", self.deploy_step],
            cwd=self.workspace, env=env, text=True, capture_output=True,
        )

    def test_enabled_builds_and_renders_binary_bundle(self):
        self.generated.write_text("authorization:\n  opaEnabled: false\n")
        result = self.run_deploy(OPA_ENABLED="true")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.docker_log.exists())
        deployment = json.loads(subprocess.check_output(
            [self.yq, "-o=json", "-I=0", 'select(.kind == "Deployment")', str(self.output)],
            text=True,
        ))
        containers = deployment["spec"]["template"]["spec"]["containers"]
        self.assertEqual([container["name"] for container in containers], ["laa-data-user-api", "opa"])
        env = {item["name"]: item.get("value") for item in containers[0]["env"]}
        self.assertEqual(env["APP_AUTHORIZATION_OPA_ENABLED"], "true")
        config_map = json.loads(subprocess.check_output(
            [self.yq, "-o=json", "-I=0", 'select(.kind == "ConfigMap" and .immutable == true)',
             str(self.output)],
            text=True,
        ))
        bundle = base64.b64decode(config_map["binaryData"]["bundle.tar.gz"], validate=True)
        bundle_ref = hashlib.sha256(bundle).hexdigest()
        self.assertEqual(config_map["metadata"]["annotations"]["laa-data-user-api/opa-bundle-ref"], bundle_ref)
        self.assertEqual(
            config_map["metadata"]["name"],
            "laa-data-user-api-opa-bundle-" + hashlib.sha256(bundle_ref.encode()).hexdigest()[:12],
        )
        with tarfile.open(fileobj=io.BytesIO(bundle), mode="r:gz") as archive:
            self.assertTrue(any(name.endswith("authz/lifecycle.rego") for name in archive.getnames()))
        if os.environ.get("TEST_REAL_DOCKER") == "1":
            bundle_file = self.workspace / "rendered-bundle.tar.gz"
            bundle_file.write_bytes(bundle)
            bundle_file.chmod(0o644)
            decision = subprocess.check_output(
                [self.docker, "run", "--rm", "--user", "10000:10000", "--read-only",
                 "--volume", f"{bundle_file}:/bundle.tar.gz:ro",
                 "openpolicyagent/opa:0.68.0", "eval", "-b", "/bundle.tar.gz",
                 "--format", "raw", "data.authz.allow"],
                text=True,
            )
            self.assertEqual(decision.strip(), "false")
        self.assertFalse(list(self.workspace.glob("opa-bundle.*")))

    def test_unset_flag_disables_opa_despite_chart_default(self):
        result = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(self.docker_log.exists())
        self.assertNotIn("laa-data-user-api-opa-bundle", self.output.read_text())
        deployment = json.loads(subprocess.check_output(
            [self.yq, "-o=json", "-I=0", 'select(.kind == "Deployment")', str(self.output)],
            text=True,
        ))
        containers = deployment["spec"]["template"]["spec"]["containers"]
        self.assertEqual([container["name"] for container in containers], ["laa-data-user-api"])
        env = {item["name"]: item.get("value") for item in containers[0]["env"]}
        self.assertEqual(env["APP_AUTHORIZATION_OPA_ENABLED"], "false")
        self.assertEqual(env["APP_AUTHORIZATION_LEGACY_POLICIES_ENABLED"], "true")

    def test_explicit_false_disables_opa(self):
        result = self.run_deploy(OPA_ENABLED="false")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(self.docker_log.exists())
        self.assertNotIn("laa-data-user-api-opa-bundle", self.output.read_text())

    def test_workflow_uses_environment_scoped_variable(self):
        configured = subprocess.check_output(
            [self.yq, "-r", '.jobs.deploy.steps[] | select(.name == "Deploy to Cloud Platform") '
             '| .env.OPA_ENABLED', str(ROOT / ".github/workflows/reusable-helm-deploy.yml")],
            text=True,
        ).strip()
        self.assertEqual(configured, "${{ vars.OPA_ENABLED || 'false' }}")

    def test_bundle_build_failure_stops_deployment_and_cleans_up(self):
        result = self.run_deploy(OPA_ENABLED="true", TEST_FAIL_DOCKER="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(self.docker_log.exists())
        self.assertFalse(self.output.exists())
        self.assertFalse(list(self.workspace.glob("opa-bundle.*")))

    def test_older_chart_without_flag_skips_bundle(self):
        values = self.chart / "values.yaml"
        subprocess.run([self.yq, "-i", "del(.authorization)", str(values)], check=True)
        (self.chart / "templates/deployment.yaml").unlink()
        (self.chart / "templates/opa-bundle.yaml").unlink()
        result = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(self.docker_log.exists())

    def test_older_chart_cannot_enable_opa(self):
        values = self.chart / "values.yaml"
        subprocess.run([self.yq, "-i", "del(.authorization)", str(values)], check=True)
        result = self.run_deploy(OPA_ENABLED="true")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("selected chart does not support OPA", result.stderr)
        self.assertFalse(self.docker_log.exists())
        self.assertFalse(self.output.exists())

    def test_invalid_environment_flag_fails_before_deployment(self):
        for value in ("TRUE", "yes", "1", " true "):
            with self.subTest(value=value):
                result = self.run_deploy(OPA_ENABLED=value)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("OPA_ENABLED must be true or false", result.stderr)
                self.assertFalse(self.docker_log.exists())
                self.assertFalse(self.output.exists())

    def test_non_boolean_flag_fails_before_deployment(self):
        for value in ('"true"', "null", "1"):
            with self.subTest(value=value):
                self.generated.write_text(f"authorization:\n  opaEnabled: {value}\n")
                result = self.run_deploy()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("authorization.opaEnabled must be a boolean", result.stderr)
                self.assertFalse(self.docker_log.exists())
                self.assertFalse(self.output.exists())

    def test_chart_rejects_missing_bundle(self):
        result = subprocess.run(
            [self.helm, "template", "test", str(self.chart)],
            text=True, capture_output=True,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("opa.bundleRef is required", result.stderr)
        result = subprocess.run(
            [self.helm, "template", "test", str(self.chart), "--set-string", "opa.bundleRef=test"],
            text=True, capture_output=True,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("opa.bundleData is required", result.stderr)


if __name__ == "__main__":
    unittest.main()
