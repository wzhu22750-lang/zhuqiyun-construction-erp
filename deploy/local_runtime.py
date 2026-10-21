#!/usr/bin/env python3
"""Owned local-demo lifecycle and anonymous Maven bootstrap (Python 3.12+)."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ENV_KEYS = set('COMPOSE_PROJECT_NAME HTTP_BIND_ADDRESS API_PORT UI_PORT DB_PORT MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD APP_SEED DEMO_PASSWORD JAVA_TOOL_OPTIONS LOCAL_UI_PORT LOCAL_USE_EXISTING_DB DB_URL DB_USER DB_PASSWORD ZQY_MAVEN_CACHE'.split())


def fail(message):
    raise SystemExit(message)


def read_env(path):
    if not path.exists():
        return
    for number, line in enumerate(path.read_text().splitlines(), 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition('=')
        key, value = key.strip(), value.strip()
        if key not in ENV_KEYS:
            continue
        if not separator:
            fail(f'.env:{number}: expected KEY=value')
        if value.startswith(("'", '"')):
            quote = value[0]
            if len(value) < 2 or not value.endswith(quote):
                fail(f'.env:{number}: use a complete single-line quoted value')
            value = value[1:-1].replace('\\' + quote, quote)
            if quote == '"':
                value = value.replace('\\\\', '\\')
        else:
            value = re.split(r'\s+#', value, maxsplit=1)[0].rstrip()
        if '\x00' in value or '\n' in value or '\r' in value:
            fail(f'.env:{number}: multiline values are unsupported')
        if key not in os.environ:
            # Literal values only: no eval, shell expansion, or command substitution.
            print(f'{key}={value}')


def atomic_json(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, indent=2) + '\n')
    temporary.chmod(0o600)
    temporary.replace(path)


def owner(state):
    result = json.loads((state / 'owner.json').read_text())
    if Path(result['root']).resolve() != state.parent.parent.resolve():
        fail('Local state belongs to a different checkout; no resources changed.')
    return result


def init(root, state):
    if state.is_symlink() or state.parent.is_symlink():
        fail('Refusing a symlink for local runtime state.')
    state.mkdir(mode=0o700, parents=True, exist_ok=True)
    state.chmod(0o700)
    if (state / 'owner.json').exists():
        owner(state)
        return
    token = uuid.uuid4().hex
    project = 'zqy-local-' + hashlib.sha256(str(root).encode()).hexdigest()[:8] + '-' + token[:8]
    # Exclusive creation also protects against two first-start invocations.
    with (state / 'owner.json').open('x') as output:
        json.dump({'root': str(root), 'owner': token, 'project': project}, output)
    (state / 'owner.json').chmod(0o600)


def identity(pid):
    result = subprocess.run(['ps', '-p', str(pid), '-o', 'lstart=', '-o', 'command='], text=True,
                            capture_output=True, env={**os.environ, 'LC_ALL': 'C'})
    return result.stdout.strip() if result.returncode == 0 else None


def same_process(record):
    try:
        return identity(record['pid']) == record['identity'] and os.getpgid(record['pid']) == record['pid']
    except ProcessLookupError:
        return False


def record_for(state, name):
    if name not in ('backend', 'frontend'):
        fail('Unknown local process name')
    path = state / (name + '.json')
    if not path.exists():
        return None
    record = json.loads(path.read_text())
    if record['owner'] != owner(state)['owner']:
        fail('Process record owner mismatch; no process changed.')
    return record


def launch(state, name, log, cwd, command):
    old = record_for(state, name)
    if old and same_process(old):
        fail(f'{name} is already running (PID {old["pid"]}).')
    with log.open('ab', buffering=0) as output:
        child = subprocess.Popen(command, cwd=cwd, stdin=subprocess.DEVNULL,
                                 stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    # Some launchers (including macOS Python) exec the real binary just after Popen.
    # Observe the stable executable identity before saving the stop record.
    observed = identity(child.pid)
    for _ in range(20):
        time.sleep(0.1)
        current = identity(child.pid)
        if current == observed and child.poll() is None:
            break
        observed = current
    if child.poll() is not None or not observed:
        fail(f'{name} exited during startup; inspect {log}')
    atomic_json(state / (name + '.json'), {'owner': owner(state)['owner'], 'pid': child.pid,
                                         'identity': observed, 'log': str(log)})
    print(f'{name}: PID {child.pid}; log {log}')


def stop(state, name):
    record = record_for(state, name)
    if not record:
        print(f'{name}: no owned process record')
        return
    if not same_process(record):
        if identity(record['pid']) is not None:
            fail(f'{name}: process identity changed; PID {record["pid"]} was NOT stopped.')
        print(f'{name}: recorded process already exited')
        (state / (name + '.json')).unlink()
        return
    os.killpg(record['pid'], signal.SIGTERM)
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline and same_process(record):
        time.sleep(0.2)
    if same_process(record):
        os.killpg(record['pid'], signal.SIGKILL)
    (state / (name + '.json')).unlink()
    print(f'{name}: stopped owned process group {record["pid"]}')


def ports(values):
    for value in values:
        port = int(value)
        if not 1 <= port <= 65535:
            fail('Port must be between 1 and 65535')
        with socket.socket() as sock:
            try:
                sock.bind(('127.0.0.1', port))
            except OSError:
                fail(f'Port {port} is occupied; choose another port. Existing services were not stopped.')


def wait_http(state, name, url, codes, seconds):
    deadline = time.monotonic() + int(seconds)
    allowed = set(map(int, codes.split(',')))
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    while time.monotonic() < deadline:
        record = record_for(state, name)
        if not record or not same_process(record):
            fail(f'{name} exited; inspect {state / (name + ".log")}')
        try:
            with opener.open(url, timeout=2) as response:
                status = response.status
        except urllib.error.HTTPError as error:
            status = error.code
        except (OSError, urllib.error.URLError):
            status = None
        if status in allowed:
            return
        time.sleep(1)
    fail(f'{name} HTTP startup timed out; inspect {state / (name + ".log")}')


def maven(cache):
    version = '3.9.9'
    destination = cache / ('apache-maven-' + version)
    executable = destination / 'bin/mvn'
    if executable.is_file():
        print(executable)
        return
    cache.mkdir(parents=True, exist_ok=True)
    # Serialize downloads; install only after a verified complete extraction.
    with (cache / 'maven-download.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if not executable.is_file():
            with tempfile.TemporaryDirectory(prefix='maven-download-', dir=cache) as directory:
                temporary = Path(directory)
                archive = temporary / 'maven.tar.gz'
                checksum = temporary / 'maven.sha512'
                base = f'https://archive.apache.org/dist/maven/maven-3/{version}/binaries/apache-maven-{version}-bin.tar.gz'
                for url, target in [(base, archive), (base + '.sha512', checksum)]:
                    subprocess.run(['curl', '-q', '--fail', '--location', '--silent', '--show-error', '--retry', '2',
                                    '--connect-timeout', '15', '--max-time', '300', '--output', str(target), url], check=True)
                match = re.search(r'\b[0-9a-fA-F]{128}\b', checksum.read_text())
                if not match or hashlib.sha512(archive.read_bytes()).hexdigest() != match.group(0).lower():
                    fail('Maven SHA-512 verification failed; nothing installed.')
                with tarfile.open(archive, 'r:gz') as bundle:
                    bundle.extractall(temporary, filter='data')
                (temporary / destination.name).rename(destination)
    print(executable)


def main():
    action, *args = sys.argv[1:]
    if action == 'env':
        read_env(Path(args[0]))
    elif action == 'init':
        init(Path(args[0]).resolve(), Path(args[1]))
    elif action == 'field':
        print(owner(Path(args[0]))[args[1]])
    elif action == 'lock':
        state = Path(args[0]); owner(state)
        with (state / 'lifecycle.lock').open('a') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                fail('Another start/stop is in progress; no resources changed.')
            raise SystemExit(subprocess.call(args[1:]))
    elif action == 'active':
        record = record_for(Path(args[0]), args[1])
        raise SystemExit(0 if record and same_process(record) else 1)
    elif action == 'start':
        launch(Path(args[0]), args[1], Path(args[2]), Path(args[3]), args[4:])
    elif action == 'stop':
        stop(Path(args[0]), args[1])
    elif action == 'ports':
        ports(args)
    elif action == 'wait-http':
        wait_http(Path(args[0]), *args[1:])
    elif action == 'maven':
        maven(Path(args[0]))
    else:
        fail('Unknown lifecycle action')


if __name__ == '__main__':
    main()
