"""Compile and execute the exact production onDraw body in an isolated Kotlin host.
Android view lifecycle is not emulated. Test targets elapsed-time subdivision only.
"""
from pathlib import Path
import subprocess,tempfile,os
root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/ir/shahed/pahpad/core/BaseScreen.kt').read_text()
body=src.split('override fun onDraw(canvas: Canvas) {',1)[1].split('// -------------------------------------------------- لمس',1)[0].strip()
# the captured trailing } closes onDraw
harness='''class Canvas
class Clock {
 var lastFrame=System.nanoTime()-100_000_000L
 var looping=true; var laidOut=true; var time=0f
 var elapsed=0f; var biggest=0f
 fun update(dt:Float) {elapsed+=dt; biggest=maxOf(biggest,dt)}
 fun render(c:Canvas) {}
 fun postInvalidateOnAnimation() {}
 fun onDraw(canvas:Canvas) {
'''+body+'''
}
fun main() {
 val c=Clock(); c.onDraw(Canvas())
 check(c.elapsed>=.09f && c.elapsed<.2f) {"Discarded elapsed time: ${c.elapsed}"}
 check(c.biggest<=.050001f) {"Unsafe physics step: ${c.biggest}"}
 println("PASS production onDraw elapsed=${c.elapsed} maxStep=${c.biggest}")
}
'''
java='/tmp/pahpad-tools/jdk-17.0.20.1+1/bin/java'; tools=Path('/tmp/pahpad-render-kotlin')
with tempfile.TemporaryDirectory(dir='/tmp') as d:
 p=Path(d); (p/'Clock.kt').write_text(harness)
 subprocess.run([java,'-Xmx192m','-XX:ActiveProcessorCount=2','-cp',str(tools/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(tools/'stdlib.jar'),'-d',d,str(p/'Clock.kt')],check=True)
 subprocess.run([java,'-cp',d+':'+str(tools/'stdlib.jar'),'ClockKt'],check=True)
