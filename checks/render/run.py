#!/usr/bin/env python3
"""Compile real production Kotlin with recording graphics doubles, then run on JVM.
Requires Kotlin 1.9.22 compiler/stdlib/script/trove/annotations jars in
KOTLIN_TEST_TOOLS (default /tmp/pahpad-render-kotlin), and JAVA_HOME or java.
All build output goes to /tmp. This does NOT test Android rasterization.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
tools = Path(os.environ.get('KOTLIN_TEST_TOOLS', '/tmp/pahpad-render-kotlin'))
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else shutil.which('java')
if not java:
    candidates = sorted(Path('/tmp/pahpad-tools').glob('jdk*/bin/java'))
    java = str(candidates[0]) if candidates else None
if not java:
    raise SystemExit('Set JAVA_HOME to a real JDK')
for jar in ('compiler.jar', 'stdlib.jar', 'script.jar', 'trove.jar', 'annotations.jar'):
    if not (tools / jar).is_file():
        raise SystemExit(f'Missing real Kotlin dependency: {tools / jar}')
src = root / 'app/src/main/java/ir/shahed/pahpad'
probes = sorted((root / 'checks/render').glob('*Probe.kt'))
with tempfile.TemporaryDirectory(prefix='render-regression-', dir='/tmp') as out:
    subprocess.run([java, '-cp', str(tools/'*'), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-classpath', str(tools/'stdlib.jar') + ':' + str(tools/'annotations.jar'),
                    '-d', out, str(src/'game/Scene3D.kt'), str(src/'game/Fx.kt'), str(src/'core/Theme.kt'),
                    str(root/'checks/render/GraphicsDoubles.kt'), str(root/'checks/render/RenderRegression.kt'),
                    *map(str, probes)], check=True)
    subprocess.run([java, '-cp', out + ':' + str(tools/'stdlib.jar'), 'checks.render.RenderRegressionKt'], check=True)
    for probe in probes:
        entry = 'checks.render.' + probe.stem + 'Kt'
        print(f'--- {entry}')
        subprocess.run([java, '-cp', out + ':' + str(tools/'stdlib.jar'), entry], check=True)
