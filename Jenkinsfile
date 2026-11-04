@Library('wf-ci') _

wfBuild(
    repo: 'GemRender',
    jdk: '25',
    branches: ['main'],
    build: 'buildAll --no-daemon --no-parallel --stacktrace',
    publish: 'publishAll --no-daemon --no-parallel --stacktrace',
    archive: 'versions/*/build/libs/*.jar',
    modRelease: [
        versionCmd: "grep '^mod_version=' gradle.properties | cut -d= -f2- | tr -d '[:space:]'",
        build: 'buildAll --no-daemon --no-parallel --stacktrace',
        publish: 'publishAllMods --no-daemon --no-parallel --stacktrace',
        name: 'GemRender @VERSION@ (26.1 and earlier)',
        collect: '''
python3 - <<'PY'
import os
import shutil
from pathlib import Path

version = os.environ['RELEASE_VERSION']
destination = Path('dist')
destination.mkdir(exist_ok=True)
collected = 0
for properties in sorted(Path('versions').glob('*/gradle.properties')):
    values = dict(line.split('=', 1) for line in properties.read_text().splitlines()
                  if line.strip() and not line.lstrip().startswith(('#', '!')) and '=' in line)
    if values.get('version_buildable', 'true').lower() == 'false':
        continue
    minecraft = values['minecraft_version'].strip()
    if tuple(map(int, minecraft.split('.'))) >= (26, 2):
        raise SystemExit(f'{properties}: Minecraft {minecraft} belongs to the vulkan branch')
    loader = values['loader'].strip()
    if loader not in ('forge', 'neoforge', 'fabric'):
        raise SystemExit(f'{properties}: unsupported loader {loader}')
    archive = (f'gemrender-fabric-{minecraft}-{version}.jar' if loader == 'fabric'
               else f'gemrender-{version}.jar')
    source = properties.parent / 'build' / 'libs' / archive
    if not source.is_file():
        raise SystemExit(f'Missing release JAR: {source}')
    target = destination / f'gemrender-{version}+{minecraft}-{loader}.jar'
    shutil.copy2(source, target)
    print(target)
    collected += 1
if collected == 0:
    raise SystemExit('No configured main release artifacts')
PY
''',
    ],
)
