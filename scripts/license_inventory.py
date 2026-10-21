#!/usr/bin/env python3
"""Inventory resolved Maven runtime and EVERY npm lock package without executing packages.
Writes only docs/dependencies*.json and new content-addressed docs/licenses/dependencies texts.
Network/cache/logs belong to --work-dir. Maven uses the project's anonymous settings.
Does not change source, POM, lock, global settings, images, containers, or package installations.
"""
from __future__ import annotations
import argparse
import base64
import collections
import concurrent.futures
import datetime as dt
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
ROOT = Path(__file__).resolve().parents[1]
LEGAL = re.compile(r'(?i)(^|/)(licen[sc]e|notice|copying|copyright)([^a-z]|$)|(^|/)(third[-_ ]?party[-_ ]?(notices?|licen[sc]es?)[^/]*)$')
MAX_BYTES = 128 * 1024 * 1024
TEXTS = {}
WORK = None


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def download(url):
    """Anonymous public fetch; no config, netrc, auth, npm or Maven credentials read."""
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'https' or parsed.username or parsed.password:
        raise ValueError('Only anonymous HTTPS is allowed')
    dest = WORK / 'downloads' / sha(url.encode())
    if dest.exists():
        return dest.read_bytes()
    with tempfile.NamedTemporaryFile(dir=WORK / 'downloads', prefix='fetch-', delete=False) as out:
        temp = Path(out.name)
    try:
        subprocess.run(['curl', '-q', '--fail', '--silent', '--show-error', '--location',
                        '--proto', '=https', '--proto-redir', '=https',
                        '--connect-timeout', '8', '--max-time', '25', '--retry', '1',
                        '--max-filesize', str(MAX_BYTES), '--output', str(temp), url],
                       check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        data = temp.read_bytes()
        if not data or len(data) > MAX_BYTES:
            raise ValueError('Empty or oversized download')
        temp.replace(dest)
        return data
    finally:
        temp.unlink(missing_ok=True)


def archive_text(data, origin):
    # Preserve bytes, including original copyright/NOTICE. Never rewrite an existing file.
    digest = sha(data)
    rel = f'docs/licenses/dependencies/{digest}.txt'
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with path.open('xb') as out:
            out.write(data)
    except FileExistsError:
        if path.read_bytes() != data:
            raise RuntimeError('Content address collision')
    if digest not in TEXTS:
        TEXTS[digest] = {'path': rel, 'sha256': digest, 'bytes': len(data), 'origins': []}
    if origin not in TEXTS[digest]['origins']:
        TEXTS[digest]['origins'].append(origin)
    return {'path': rel, 'sha256': digest, 'origin': origin}


def zip_legal(data, origin):
    result = []
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        for name in sorted(z.namelist()):
            if name.endswith('/') or not LEGAL.search(name):
                continue
            if Path(name).suffix.lower() not in ('', '.txt', '.md', '.html', '.xml', '.rst'):
                continue
            info = z.getinfo(name)
            if info.file_size > 2 * 1024 * 1024:
                continue
            content = z.read(name)
            if content and b'\x00' not in content:
                result.append(archive_text(content, f'{origin}!/{name}'))
    return result


def central(group, artifact, version, suffix):
    return f'https://repo.maven.apache.org/maven2/{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}{suffix}'


def resolve_props(value, props):
    for _ in range(6):
        updated = re.sub(r'\$\{([^}]+)\}', lambda m: props.get(m[1], m[0]), value)
        if updated == value:
            break
        value = updated
    return value


def pom_info(group, artifact, version, repo, seen=()):
    key = (group, artifact, version)
    if key in seen:
        return {'licenses': [], 'pomChain': [], 'issue': 'parent-cycle'}
    path = repo / group.replace('.', '/') / artifact / version / f'{artifact}-{version}.pom'
    url = central(group, artifact, version, '.pom')
    data = path.read_bytes() if path.exists() else download(url)
    root = ET.fromstring(data)
    def text(elem, name):
        return elem.findtext('m:' + name, default='', namespaces=NS).strip()
    props = {'revision': version, 'project.version': version, 'project.groupId': group}
    pe = root.find('m:properties', NS)
    if pe is not None:
        props.update({n.tag.split('}')[-1]: (n.text or '').strip() for n in pe})
    licenses = []
    for node in root.findall('m:licenses/m:license', NS):
        licenses.append({'name': text(node, 'name'), 'url': text(node, 'url'), 'comments': text(node, 'comments')})
    chain = [{'coordinate': ':'.join(key), 'url': url if group != 'cn.iocoder.boot' else None, 'evidence': 'local installed POM' if group == 'cn.iocoder.boot' else 'published POM cached locally or fetched anonymously', 'sha256': sha(data)}]
    if not licenses:
        parent = root.find('m:parent', NS)
        if parent is not None:
            pkey = tuple(resolve_props(text(parent, x), props) for x in ('groupId', 'artifactId', 'version'))
            inherited = pom_info(*pkey, repo, seen + (key,))
            licenses = inherited['licenses']
            chain += inherited['pomChain']
    return {'licenses': licenses, 'pomChain': chain, 'projectUrl': text(root, 'url'), 'scmUrl': root.findtext('m:scm/m:url', default='', namespaces=NS)}


def normalize_license(names):
    joined = ' '.join(names).lower()
    if 'mulan' in joined:
        return 'MulanPSL-2.0'
    if 'lgpl-2.1-or-later' in joined:
        return 'LGPL-2.1-or-later'
    if 'epl' in joined and 'lgpl' in joined:
        return 'EPL-2.0 OR LGPL-2.1-only'
    if ('epl' in joined or 'eclipse' in joined) and ('gpl' in joined or 'cpe' in joined):
        return 'EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0'
    if 'apache' in joined:
        return 'Apache-2.0'
    if 'mit' in joined:
        return 'MIT'
    return 'NOASSERTION'


# Full terms are used where published JARs lack a license file; this does not invent
# component-specific copyrights or assert that a project has no additional notices.
CANONICAL = {
    'Apache-2.0': 'https://www.apache.org/licenses/LICENSE-2.0.txt',
    'LGPL-2.1-or-later': 'https://raw.githubusercontent.com/mariadb-corporation/mariadb-connector-j/3.5.8/LICENSE',
    'Logback-dual': 'https://raw.githubusercontent.com/qos-ch/logback/v_1.5.34/LICENSE.txt',
    'EPL-2.0': 'https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.txt',
    'MulanPSL-2.0': 'https://raw.githubusercontent.com/dromara/hutool/5.8.47/LICENSE',
}


def backend_records(runtime_list, jar_path):
    records = []
    for line in runtime_list.read_text().splitlines():
        value = line.strip().split(' -- ')[0].replace(' (optional)', '')
        m = re.fullmatch(r'([^: ]+):([^:]+):([^:]+):(?:(.*?):)?([^:]+):(compile|runtime):(/.*)', value)
        if not m:
            continue
        group, artifact, kind, classifier, version, scope, file = m.groups()
        records.append({'component': f'{group}:{artifact}', 'groupId': group, 'artifactId': artifact, 'type': kind, 'classifier': classifier or None, 'version': version, 'scope': scope, 'optional': '(optional)' in line, '_file': Path(file)})
    if not records:
        raise RuntimeError('No Maven runtime records parsed')
    nested = {}
    nested_bytes = {}
    package = None
    if jar_path.exists():
        data = jar_path.read_bytes()
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            nested_bytes = {Path(n).name: z.read(n) for n in z.namelist() if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')}
            nested = {name: sha(value) for name, value in nested_bytes.items()}
        package = {'path': str(jar_path.relative_to(ROOT)), 'sha256': sha(data), 'nestedJarCount': len(nested), 'nestedJars': nested}
    known = {r['_file'].name for r in records}
    # Boot's repackage plugin contributes jarmode-tools outside dependency:list.
    for name in sorted(set(nested) - known):
        if name.startswith('spring-boot-jarmode-tools-'):
            version = name[len('spring-boot-jarmode-tools-'):-4]
            first = records[0]
            repo = Path(str(first['_file']).split('/' + first['groupId'].replace('.', '/') + '/')[0])
            records.append({'component': 'org.springframework.boot:spring-boot-jarmode-tools', 'groupId': 'org.springframework.boot', 'artifactId': 'spring-boot-jarmode-tools', 'type': 'jar', 'classifier': None, 'version': version, 'scope': 'packaged-only', 'optional': False, '_file': repo/'org/springframework/boot/spring-boot-jarmode-tools'/version/name})
        else:
            raise RuntimeError(f'Unattributed packaged JAR: {name}')
    for r in records:
        path = r.pop('_file')
        print(f'License inspection: {r["component"]}:{r["version"]}', flush=True)
        data = path.read_bytes() if path.exists() else nested_bytes[path.name]
        r['artifactBytesEvidence'] = 'resolved local Maven artifact' if path.exists() else 'actual BOOT-INF/lib nested JAR'
        r['sha256'] = sha(data)
        r['artifactUrl'] = central(r['groupId'], r['artifactId'], r['version'], '.jar') if r['groupId'] != 'cn.iocoder.boot' else 'vendor/ruoyi/'
        repo = Path(str(path).split('/' + r['groupId'].replace('.', '/') + '/')[0])
        info = pom_info(r['groupId'], r['artifactId'], r['version'], repo)
        r.update(info)
        if r['component'] == 'cn.iocoder.boot:yudao-common':
            r['licenseExpression'] = 'MIT'
            r['licenseEvidence'] = 'vendor/ruoyi/LICENSE; fixed upstream 7c397bbfae3e768b26e3ee962c5aa7064e8d7c96'
        else:
            r['licenseExpression'] = normalize_license([x['name'] for x in r['licenses']])
            r['licenseEvidence'] = 'published POM (parent inheritance retained); normalization is analyst interpretation'
        r['packaged'] = path.name in nested if package else None
        r['packagedSha256MatchesResolved'] = nested[path.name] == r['sha256'] if path.name in nested else None
        r['legalFiles'] = zip_legal(data, r['artifactUrl'])
        if r['component'] == 'cn.iocoder.boot:yudao-common':
            r['legalFiles'].append(archive_text((ROOT/'vendor/ruoyi/LICENSE').read_bytes(), 'vendor/ruoyi/LICENSE'))
        if (not r['legalFiles'] and r['groupId'] != 'cn.iocoder.boot') or r['component'] in ('org.mariadb.jdbc:mariadb-java-client', 'ch.qos.logback:logback-classic', 'ch.qos.logback:logback-core'):
            source_url = central(r['groupId'], r['artifactId'], r['version'], '-sources.jar')
            try:
                src = download(source_url)
                r['sourceArchive'] = {'url': source_url, 'sha256': sha(src), 'bytes': len(src), 'deliveryStatus': 'downloaded to temporary audit cache; not included in deliverable source bundle'}
                r['legalFiles'] += zip_legal(src, source_url)
                if r['component'] == 'org.mariadb.jdbc:mariadb-java-client':
                    with zipfile.ZipFile(io.BytesIO(src)) as source_zip:
                        header_source = source_zip.read('org/mariadb/jdbc/Driver.java')
                    match = re.match(rb'((?://[^\r\n]*(?:\r?\n|$))+|/\*.*?\*/)', header_source, re.S)
                    if not match or b'Copyright' not in match.group(1):
                        raise RuntimeError('MariaDB copyright header unavailable')
                    r['legalFiles'].append(archive_text(match.group(1), source_url + '!/org/mariadb/jdbc/Driver.java#leading-license-comment'))
            except Exception as exc:
                if 'sourceArchive' in r:
                    r['sourceArchive']['inspectionErrorType'] = type(exc).__name__
                    r.setdefault('legalWarnings', []).append('Source archive downloaded but license extraction incomplete: ' + type(exc).__name__)
                else:
                    r['sourceArchive'] = {'url': source_url, 'status': 'not downloaded', 'errorType': type(exc).__name__}
        # Explicit licensing edge cases keep both licensing alternatives and exact text.
        keys = []
        if r['licenseExpression'] == 'Apache-2.0' and not r['legalFiles']:
            keys = ['Apache-2.0']
        elif r['licenseExpression'] == 'MulanPSL-2.0':
            keys = ['MulanPSL-2.0']
        elif r['licenseExpression'].startswith('EPL-2.0 OR LGPL'):
            keys = ['Logback-dual', 'EPL-2.0', 'LGPL-2.1-or-later']
        elif r['licenseExpression'] == 'LGPL-2.1-or-later':
            keys = ['LGPL-2.1-or-later']
        for key in keys:
            url = CANONICAL[key]
            try:
                r['legalFiles'].append(archive_text(download(url), url))
            except Exception as exc:
                r.setdefault('legalWarnings', []).append(f'{key} canonical fetch: {type(exc).__name__}: {url}')
        r['licenseTextStatus'] = 'archived' if r['legalFiles'] else 'missing'
    return sorted(records, key=lambda r:(r['component'],r['version'])), package


def frontend_record(item):
    path, entry = item
    installed_as = path.rsplit('node_modules/', 1)[1]
    component = entry.get('name', installed_as)
    url = entry['resolved']
    if urllib.parse.urlsplit(url).hostname != 'registry.npmjs.org':
        raise ValueError('Review non-public-registry lock entry explicitly')
    data = download(url)
    integrity = entry.get('integrity', '')
    matched = False
    for token in integrity.split():
        algorithm, expected = token.split('-', 1)
        if algorithm in hashlib.algorithms_available and base64.b64encode(hashlib.new(algorithm, data).digest()).decode() == expected:
            matched = True
    if not matched:
        raise RuntimeError(f'npm integrity mismatch: {component}')
    materials = []
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as tf:
        meta = {}
        for member in tf.getmembers():
            if not member.isfile() or member.size > 2*1024*1024:
                continue
            if member.name.count('/') == 1 and member.name.endswith('/package.json'):
                meta = json.loads(tf.extractfile(member).read())
            if LEGAL.search(member.name) and Path(member.name).suffix.lower() in ('', '.txt', '.md', '.html', '.rst'):
                content = tf.extractfile(member).read()
                if content and b'\x00' not in content:
                    materials.append((content, f'{url}!/{member.name}'))
    if meta.get('version') != entry['version'] or meta.get('name') != component:
        raise RuntimeError(f'npm identity mismatch: {component}')
    return {'component': component, 'installedAs': installed_as, 'version': entry['version'], 'lockPath': path, 'licenseExpression': entry['license'], 'packageJsonLicense': meta.get('license'), 'development': bool(entry.get('dev', False)), 'optional': bool(entry.get('optional', False)), 'devOptional': bool(entry.get('devOptional', False)), 'os': entry.get('os', []), 'cpu': entry.get('cpu', []), 'resolved': url, 'integrity': integrity, 'integrityVerified': True, 'tarballSha256': sha(data), 'repository': meta.get('repository'), 'author': meta.get('author'), 'contributors': meta.get('contributors'), 'copyright': meta.get('copyright'), '_materials': materials}


NPM_LEGAL_SOURCES = {
    ('@vue/devtools-api', '6.6.4'): 'https://raw.githubusercontent.com/vuejs/vue-devtools/df6ab6bb7791a7a525a97990de73b3ea5e9a1941/LICENSE',
}
NPM_README_LICENSES = {
    ('agent-base', '6.0.2'): ('c4b8ea2e1a11bae023bb09b708050a50418204e9', 'https://api.github.com/repos/TooTallNate/node-agent-base/git/blobs/256f1f32196c60a970bd2b0bd75f669b135112bf'),
    ('https-proxy-agent', '5.0.1'): ('d0d80cc0482f20495aa8595f802e1a9f3b1b3409', 'https://api.github.com/repos/TooTallNate/proxy-agents/git/blobs/328656a9e048a3f45b1e3e3a91c557865cb5a9a5'),
}



def main():
    global WORK
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--maven', default='mvn')
    ap.add_argument('--work-dir', required=True)
    ap.add_argument('--runtime-list', type=Path, help='Optional fresh dependency:list output with absolute filenames')
    ap.add_argument('--online', action='store_true', help='Allow Maven anonymous Central downloads (default offline)')
    ap.add_argument('--jar', type=Path, default=ROOT/'backend/target/construction-erp-0.1.0.jar')
    args = ap.parse_args()
    WORK = Path(args.work_dir).resolve()
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK/'downloads').mkdir(exist_ok=True)
    inputs = ['backend/pom.xml','frontend/package.json','frontend/package-lock.json','vendor/ruoyi/pom.xml','vendor/ruoyi/yudao-dependencies/pom.xml','vendor/ruoyi/yudao-framework/yudao-common/pom.xml','backend/Dockerfile','frontend/Dockerfile','compose.yml']
    hashes = {p:sha((ROOT/p).read_bytes()) for p in inputs}
    stamp = dt.datetime.now().astimezone().isoformat(timespec='seconds')
    runtime_list = args.runtime_list or WORK/'maven-runtime.txt'
    command = None
    if args.runtime_list is None:
        command = [args.maven,'-B','-ntp'] + ([] if args.online else ['-o']) + ['-s',str(ROOT/'deploy/maven-settings.xml'),'-gs',str(ROOT/'deploy/maven-settings.xml'),'-f',str(ROOT/'backend/pom.xml'),'dependency:list','-DincludeScope=runtime','-DoutputAbsoluteArtifactFilename=true',f'-DoutputFile={runtime_list}']
        proc = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        (WORK/'maven-list.log').write_text(proc.stdout)
        if proc.returncode:
            raise SystemExit('Maven resolution failed: inspect task-local maven-list.log')
    print('Reading Maven runtime artifacts and licensing metadata', flush=True)
    backend, package = backend_records(runtime_list,args.jar.resolve())
    lock = json.loads((ROOT/'frontend/package-lock.json').read_bytes())
    entries = [(p,e) for p,e in lock['packages'].items() if p]
    print(f'Fetching {len(entries)} exact npm lock tarballs (no package execution)', flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        frontend = list(pool.map(frontend_record,entries))
    for record in frontend:
        record['legalFiles'] = [archive_text(data,origin) for data,origin in record.pop('_materials')]
    # Platform binary packages sometimes carry only package.json's MIT declaration.
    # Use the same version's publisher package legal text, with explicit provenance.
    by_name = {(r['component'],r['version']):r for r in frontend}
    for r in frontend:
        if not r['legalFiles']:
            sibling = None
            if r['component'].startswith('@esbuild/'):
                sibling = by_name.get(('esbuild',r['version']))
            elif r['component'].startswith('@rollup/'):
                sibling = by_name.get(('rollup',r['version']))
            if sibling and sibling['legalFiles']:
                r['legalFiles'] = sibling['legalFiles'][:]
                r['licenseTextRelation'] = 'same publisher/project/version package license; target platform tarball has no separate license text'
                r['licenseTextPublisherPackage'] = sibling['component']
        source = NPM_LEGAL_SOURCES.get((r['component'], r['version']))
        if not r['legalFiles'] and source:
            try:
                r['legalFiles'].append(archive_text(download(source), source))
                r['licenseTextRelation'] = 'upstream repository root at exact npm registry version gitHead; not extracted from target tarball'
            except Exception as exc:
                r['licenseTextFetchIssue'] = type(exc).__name__
        readme = NPM_README_LICENSES.get((r['component'], r['version']))
        if not r['legalFiles'] and readme:
            commit, blob_url = readme
            registry_url = 'https://registry.npmjs.org/' + r['component'] + '/' + r['version']
            registry_bytes = download(registry_url)
            metadata = json.loads(registry_bytes)
            if metadata.get('gitHead') != commit:
                raise RuntimeError('npm gitHead evidence changed')
            blob = json.loads(download(blob_url))
            original = base64.b64decode(blob['content'])
            if hashlib.sha1(b'blob ' + str(len(original)).encode() + b'\0' + original).hexdigest() != blob['sha']:
                raise RuntimeError('Git blob content mismatch')
            # Preserve the exact original license section, without README code examples.
            header = b'License\n-------\n'
            section = original[original.index(header):]
            end = section.index(b'SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.') + len(b'SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.')
            r['legalFiles'].append(archive_text(section[:end], blob_url + '#README.md-License-section'))
            r['licenseTextRelation'] = 'original README license section from exact npm gitHead, verified Git blob; target tarball lacks separate license'
            r['licenseTextSourceEvidence'] = {'registryUrl': registry_url, 'registryMetadataSha256': sha(registry_bytes), 'gitHead': commit, 'readmeBlob': blob['sha']}
        if not r['legalFiles']:
            r['licenseTextLimitation'] = 'Published tarball has package.json license declaration only; complete upstream license/copyright text remains to be obtained before distribution'
        r['licenseTextStatus'] = 'archived' if r['legalFiles'] else 'missing'
    frontend.sort(key=lambda r:(r['component'],r['version'],r['lockPath']))
    mysql_license_url = 'https://raw.githubusercontent.com/mysql/mysql-server/mysql-8.0.43/LICENSE'
    image_legal = archive_text(download(mysql_license_url), mysql_license_url)
    image_records = []
    for source, stage, tag in [('backend/Dockerfile','build','maven:3.9.9-eclipse-temurin-21'),('backend/Dockerfile','runtime','eclipse-temurin:21-jre-jammy'),('frontend/Dockerfile','build','node:22-alpine'),('frontend/Dockerfile','runtime','nginx:1.28-alpine'),('compose.yml','runtime-external-server','mysql:8.0.43')]:
        inspection = subprocess.run(['docker','image','inspect',tag,'--format','{{json .Id}} {{json .RepoDigests}} {{json .Os}} {{json .Architecture}}'],capture_output=True,text=True)
        image_records.append({'tag':tag,'declaredIn':source,'stage':stage,'localMetadata':inspection.stdout.strip() if inspection.returncode==0 else None,'status':'local image metadata read; OS packages not inventoried' if inspection.returncode==0 else 'declared tag only; image not pulled or scanned','legalFiles':[image_legal] if tag.startswith('mysql:') else [],'coverage':'Top-level license context only; no claim to complete image dependency/license SBOM'})
    end_hashes = {p:sha((ROOT/p).read_bytes()) for p in inputs}
    if hashes != end_hashes:
        raise SystemExit('Input changed during scan; repeat inventory before publishing JSON')
    common = {'schemaVersion':1,'generatedAt':stamp,'generator':'scripts/license_inventory.py','inputSha256':hashes}
    write_json(ROOT/'docs/dependencies-backend.json',common|{'scope':'Maven includeScope=runtime plus identified Boot repackager extra; NOT test dependencies or all build plugins','counts':{'resolvedRuntime':sum(r['scope']!='packaged-only' for r in backend),'packagedOnly':sum(r['scope']=='packaged-only' for r in backend),'packagedMatched':sum(r['packaged'] is True for r in backend)},'packageInspection':package,'components':backend})
    write_json(ROOT/'docs/dependencies-frontend.json',common|{'scope':'ALL non-root package-lock v3 entries including dev/optional/foreign platform; not a bundle inclusion assertion','lockfileVersion':lock['lockfileVersion'],'counts':dict(collections.Counter(r['licenseExpression'] for r in frontend)),'components':frontend})
    write_json(ROOT/'docs/dependencies-licenses.json',common|{'scope':'Exact license/NOTICE bytes extracted from published archives and identified primary license sources; identical bytes deduplicated','texts':sorted(TEXTS.values(),key=lambda r:r['path'])})
    evidence=common|{'mavenCommand':command,'mavenListSha256':sha(runtime_list.read_bytes()),'backendCount':len(backend),'frontendCount':len(frontend),'npmIntegrityVerifiedCount':sum(r['integrityVerified'] for r in frontend),'licenseTextCount':len(TEXTS),'missingLicenseText':[r['component']+'@'+r['version'] for r in backend+frontend if r['licenseTextStatus']=='missing'],'unknownLicenseExpressions':[r['component'] for r in backend+frontend if r['licenseExpression']=='NOASSERTION'],'packagedMismatches':[r['component'] for r in backend if r['packagedSha256MatchesResolved'] is False],'warnings':[{ 'component':r['component'],'warnings':r['legalWarnings']} for r in backend if r.get('legalWarnings')],'executionBoundary':'Dependency parsing, anonymous fetch, integrity and archive inspection only; no app tests, server start, image builds, container changes or legal compliance sign-off'}
    write_json(ROOT/'docs/dependencies-evidence.json',evidence)
    write_json(ROOT/'docs/dependencies-images.json',common|{'scope':'Declared multi-stage bases and external database, local metadata where available; OS packages and native bundled libraries NOT exhaustively scanned','images':image_records})
    print(json.dumps({k:evidence[k] for k in ('backendCount','frontendCount','npmIntegrityVerifiedCount','licenseTextCount','missingLicenseText','unknownLicenseExpressions','packagedMismatches','warnings')},ensure_ascii=False,indent=2),flush=True)

if __name__ == '__main__':
    main()
