#!/usr/bin/env python3
"""Run Compose from an ASCII source mirror; keep original .env and named volumes."""
import fcntl
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
EXCLUDE = {'.git', '.env', '.run', 'node_modules', 'target', 'dist', '__pycache__', '.playwright-mcp', '.DS_Store'}


def ignored(directory, names):
    return [name for name in names if name in EXCLUDE or name.startswith('.env.') or name.endswith(('.log', '.pyc')) or name == '.flattened-pom.xml']


def main():
    suffix = hashlib.sha256(str(ROOT).encode()).hexdigest()[:12]
    stage = Path('/tmp') / ('zhuqiyun-compose-' + suffix)
    lock = Path('/tmp') / ('zhuqiyun-compose-' + suffix + '.lock')
    if stage.is_symlink() or lock.is_symlink():
        raise SystemExit('Compose working path must not be a symbolic link.')
    with lock.open('a') as handle:
        os.chmod(lock, 0o600)
        fcntl.flock(handle, fcntl.LOCK_EX)
        if stage.exists() and stage.stat().st_uid != os.getuid():
            raise SystemExit('Compose working path belongs to a different user.')
        stage.mkdir(mode=0o700, exist_ok=True)
        stage.chmod(0o700)
        # A fixed allowlist avoids staging private records, runtime state, or .env.
        for name in ('backend', 'frontend', 'vendor', 'deploy', 'docs'):
            source = ROOT / name
            for path in source.rglob('*'):
                if path.is_symlink():
                    # node_modules/build outputs are excluded, including their symlinks.
                    if not any(part in EXCLUDE for part in path.relative_to(source).parts):
                        raise SystemExit(f'Unexpected source symlink: {path.relative_to(ROOT)}')
            destination = stage / name
            if destination.exists():
                shutil.rmtree(destination)
            shutil.copytree(source, destination, ignore=ignored)
        for name in ('compose.yml', '.dockerignore', 'THIRD_PARTY_NOTICES.md'):
            shutil.copy2(ROOT / name, stage / name)
        arguments = sys.argv[1:] or ['up', '-d', '--build']
        command = ['docker', 'compose', '--project-directory', str(stage)]
        if (ROOT / '.env').is_file():
            command.extend(['--env-file', str(ROOT / '.env')])
        command.extend(arguments)
        print(f'Compose source: {ROOT}\nBuild context: {stage}', flush=True)
        return subprocess.run(command, cwd=stage).returncode


if __name__ == '__main__':
    sys.exit(main())
