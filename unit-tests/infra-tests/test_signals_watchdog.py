# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2026 RealSense, Inc. All Rights Reserved.

"""
Tests for the rspy/signals.py abort watchdog: a separate process that force-kills a run
whose signal handler can never execute (main thread stuck in a native call).
"""

import os
import signal
import subprocess
import sys
import time

import pytest

pytestmark = pytest.mark.skipif(sys.platform == 'win32', reason='watchdog is POSIX-only')

UNIT_TESTS_DIR = os.path.normpath(os.path.join(os.path.dirname(__file__), '..'))


def spawn(child_code):
    # start_new_session: own process group, so signal_group() below can emulate a
    # Jenkins abort (SIGTERM to the whole tree) without hitting this pytest itself
    return subprocess.Popen([sys.executable, '-u', '-c', child_code],
                            cwd=UNIT_TESTS_DIR,
                            env={**os.environ, 'PYTHONPATH': os.path.join(UNIT_TESTS_DIR, 'py')},
                            stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT,
                            text=True,
                            start_new_session=True)


def signal_group(proc, sig):
    """Jenkins aborts SIGTERM every process in the build's tree — watchdog included."""
    os.killpg(os.getpgid(proc.pid), sig)


def wait_for_ready(proc, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        line = proc.stdout.readline()
        if 'READY' in line:
            return
        if line == '' and proc.poll() is not None:
            break
    pytest.fail('child never printed READY')


def wait_for_exit(proc, timeout):
    try:
        proc.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        proc.kill()
        pytest.fail(f'child still alive after {timeout}s')


def test_watchdog_kills_stuck_cleanup():
    """SIGTERM handler that never finishes cleanup -> watchdog SIGKILLs after grace."""
    child = spawn('''
import time
from rspy import signals

def stuck_cleanup():
    while True:  # simulates cleanup that can never complete
        time.sleep(1)

signals.WATCHDOG_GRACE_S = 2
signals.register_signal_handlers(stuck_cleanup)
print('READY', flush=True)
time.sleep(120)
''')
    wait_for_ready(child)
    signal_group(child, signal.SIGTERM)
    # grace is 2s; well before the 120s sleep ends the watchdog must have killed it
    wait_for_exit(child, timeout=15)
    assert child.returncode == -signal.SIGKILL


def test_watchdog_exits_when_parent_ends_normally():
    """No signal: parent exits normally, watchdog must follow (no leaked process)."""
    child = spawn('''
import time
from rspy import signals

signals.register_signal_handlers()
print('READY', flush=True)
print('WATCHDOG_PID', signals._watchdog.pid, flush=True)
''')
    wait_for_ready(child)
    line = child.stdout.readline()
    watchdog_pid = int(line.split()[1])
    wait_for_exit(child, timeout=15)
    # give the watchdog a moment to notice the pipe EOF
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        try:
            os.kill(watchdog_pid, 0)
        except OSError:
            return  # watchdog gone
        time.sleep(0.2)
    os.kill(watchdog_pid, signal.SIGKILL)
    pytest.fail('watchdog process leaked after parent exited')


def test_clean_abort_not_killed_by_watchdog():
    """Cleanup that finishes within grace: process exits via os._exit(1), not SIGKILL."""
    child = spawn('''
import time
from rspy import signals

signals.WATCHDOG_GRACE_S = 30
signals.register_signal_handlers(lambda: time.sleep(0.5))
print('READY', flush=True)
time.sleep(120)
''')
    wait_for_ready(child)
    signal_group(child, signal.SIGTERM)
    wait_for_exit(child, timeout=15)
    assert child.returncode == 1  # normal abort path, watchdog never fired
