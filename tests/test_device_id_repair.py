"""Host-only runner tests; never execute the uploaded provisioning binary."""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class RepairRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.base = self.root / 'repair'
        self.base.mkdir()
        self.module = self.root / 'module'
        self.vendor = self.root / 'vendor'
        self.vendor.mkdir()
        for name in ['libminkdescriptor.so', 'libmodem_device_ids.so', 'libcrypto.so']:
            (self.vendor / name).write_text('mock library')
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        for name, body in [('getprop', 'echo NX809J'), ('id', 'echo 0'), ('su', 'shift; shift; exec sh -c "$1"')]:
            f = self.bin / name
            f.write_text('#!/bin/sh\n' + body + '\n')
            f.chmod(0o700)
        self.env = dict(os.environ, PATH=str(self.bin) + ':' + os.environ['PATH'])
        self.mock_binary('exit 0')

    def mock_binary(self, ending):
        f = self.base / 'KmInstallKeybox'
        f.write_text('#!/bin/sh\necho invoked >> "' + str(self.base / 'calls') + '"\n'
                     'echo "serial: SECRET_IMEI"\necho "Device info provisioning complete."\n' + ending + '\n')
        f.chmod(0o700)
        source = (ROOT / 'app/src/main/assets/device_id_repair/run.sh').read_text()
        source = source.replace('/data/adb/redmagic_toolbox/device_id_repair', str(self.base))
        source = source.replace('/data/adb/modules/autoDeviceID', str(self.module))
        source = source.replace('/vendor/lib64', str(self.vendor))
        source = source.replace('a5eedbc361c631d50398c6458b0b8cf1b6130fa97454e5ea203ada1c71287768', hashlib.sha256(f.read_bytes()).hexdigest())
        self.runner = self.root / 'run.sh'
        self.runner.write_text(source)

    def run_repair(self, mode='manual'):
        return subprocess.run(['sh', str(self.runner), mode], env=self.env, capture_output=True, text=True, timeout=5)

    def test_success_filters_identifiers_and_requires_zero_exit(self):
        result = self.run_repair()
        self.assertEqual(0, result.returncode)
        self.assertIn('Device info provisioning completed', result.stdout)
        self.assertNotIn('SECRET_IMEI', result.stdout)
        self.assertNotIn('SECRET_IMEI', (self.base / 'status').read_text())
        self.mock_binary('exit 7')
        self.assertIn('Repair failed or completion unverified', self.run_repair().stdout)

    def test_enabled_module_suppresses_but_disabled_module_allows(self):
        self.module.mkdir()
        self.assertIn('Handled by installed module', self.run_repair().stdout)
        self.assertFalse((self.base / 'calls').exists())
        (self.module / 'disable').touch()
        self.assertIn('Device info provisioning completed', self.run_repair().stdout)

    def test_boot_opt_in_and_single_attempt(self):
        self.run_repair('boot')
        self.assertFalse((self.base / 'calls').exists())
        (self.base / 'boot_enabled').touch()
        self.run_repair('boot')
        self.run_repair('boot')
        self.assertEqual(['invoked'], (self.base / 'calls').read_text().splitlines())

    def test_unknown_lock_blocks_retry_and_missing_library_blocks_execution(self):
        boot = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        lock = self.base / ('operation-' + boot)
        lock.mkdir()
        self.assertIn('unfinished', self.run_repair().stdout)
        self.assertFalse((self.base / 'calls').exists())
        lock.rmdir()
        (self.vendor / 'libcrypto.so').unlink()
        self.assertIn('Required vendor library unavailable', self.run_repair().stdout)
        self.assertFalse((self.base / 'calls').exists())

    def test_binary_hash_mismatch_blocks_execution(self):
        with (self.base / 'KmInstallKeybox').open('a') as f:
            f.write('#changed\n')
        self.assertIn('Binary verification failed', self.run_repair().stdout)
        self.assertFalse((self.base / 'calls').exists())

if __name__ == '__main__':
    unittest.main()
