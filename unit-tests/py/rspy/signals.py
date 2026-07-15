# License: Apache 2.0. See LICENSE file in root directory.
# Copyright(c) 2025 RealSense, Inc. All Rights Reserved.

from rspy import log
import os, sys, signal, subprocess

signal_handler = lambda: log.d("Signal handler not set")
_cleanup_in_progress = False
_watchdog = None

# Grace period the watchdog gives this process to clean up and exit after a
# SIGTERM/SIGINT before force-killing it (must exceed a normal hub/device cleanup)
WATCHDOG_GRACE_S = 60

# The Python-level handlers below only run when the main thread returns to the
# interpreter loop. If the main thread is stuck in a native call that never returns
# (e.g. a C++ mutex/GIL deadlock), a Jenkins abort's SIGTERM is never processed, the
# process survives as an orphan, and its open BrainStem USB fd keeps the Acroname hub
# unreachable for every subsequent run on that agent.
# The watchdog is a separate process, so it is immune to this: it receives the same
# SIGTERM/SIGINT (same process group), waits out the grace period, then SIGKILLs us.
# The kernel then closes our fds, releasing the hub. It exits on its own when we
# exit normally (its stdin pipe hits EOF).
_WATCHDOG_CODE = '''
import os, signal, sys, time
ppid, grace = int(sys.argv[1]), float(sys.argv[2])
def _abort(signum, frame):
    deadline = time.monotonic() + grace
    while time.monotonic() < deadline:
        try:
            os.kill(ppid, 0)
        except OSError:
            os._exit(0)  # parent exited on its own during the grace period
        time.sleep(0.5)
    try:
        os.kill(ppid, signal.SIGKILL)
    except OSError:
        pass
    os._exit(0)
signal.signal(signal.SIGTERM, _abort)
signal.signal(signal.SIGINT, _abort)
os.write(1, b'R')  # handshake: handlers armed, parent may proceed
while True:
    if not os.read(0, 1):  # EOF: parent exited (or crashed) without a signal
        os._exit(0)
'''


def start_abort_watchdog(grace=None):
    """
    Start the GIL-independent abort watchdog (POSIX only; no-op on Windows or if
    already running). See _WATCHDOG_CODE above for why it must be a separate process.
    """
    global _watchdog
    if os.name != 'posix' or _watchdog is not None:
        return
    if grace is None:
        grace = WATCHDOG_GRACE_S
    try:
        _watchdog = subprocess.Popen([sys.executable, '-c', _WATCHDOG_CODE, str(os.getpid()), str(grace)],
                                     stdin=subprocess.PIPE,
                                     stdout=subprocess.PIPE,
                                     stderr=subprocess.DEVNULL)
        _watchdog.stdout.read(1)  # wait until its signal handlers are armed
        log.d('abort watchdog started, pid', _watchdog.pid)
    except Exception as e:
        log.w('failed to start abort watchdog:', e)


def register_signal_handlers(on_signal=None):
    def handle_abort(signum, _):
        global signal_handler, _cleanup_in_progress
        if _cleanup_in_progress:
            # Second signal during cleanup — force-exit immediately so we don't hang
            log.w("got signal", signum, "during cleanup — force-exiting")
            os._exit(1)
        _cleanup_in_progress = True
        log.w("got signal", signum, "aborting... ")
        signal_handler()
        os._exit(1)

    global signal_handler
    signal_handler = on_signal or signal_handler

    signal.signal(signal.SIGTERM, handle_abort)  # for when aborting via Jenkins
    signal.signal(signal.SIGINT, handle_abort)  # for Ctrl+C
    start_abort_watchdog()
