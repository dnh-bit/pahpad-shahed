"""Compile all real Android Kotlin sources against installed Android 34 SDK.
This checks types, not APK packaging or device execution. No recording doubles.
"""
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[1]; tools=Path('/tmp/pahpad-render-kotlin'); out=Path('/tmp/pahpad-android-classes');out.mkdir(exist_ok=True)
sdk=Path('/tmp/pahpad-tools/sdk/platforms/android-34/android.jar'); assert sdk.is_file(),sdk
sources=list((r/'app/src/main/java').rglob('*.kt'))
subprocess.run(['/tmp/pahpad-tools/jdk-17.0.20.1+1/bin/java','-Xmx256m','-XX:ActiveProcessorCount=2','-cp',str(tools/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',':'.join(map(str,[tools/'stdlib.jar',tools/'annotations.jar',sdk])),'-d',str(out),*map(str,sources)],check=True)
print('PASS Android 34 compile:',len(sources),'production Kotlin files')
