"""Independent asset/math checks, NOT an Android compile or device test."""
from pathlib import Path
import hashlib,json,math,re
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
GFX=ROOT/'app/src/main/assets/gfx'
checks=[]
def check(name,condition):
 if not condition:raise AssertionError(name)
 checks.append(name)

def project(point,origin,yaw,pitch,focal=620,cx=640,cy=360):
 dx,dy,dz=np.asarray(point)-origin;s,c=math.sin(yaw),math.cos(yaw);sp,cp=math.sin(pitch),math.cos(pitch)
 right=dx*c-dz*s;fw=dx*s+dz*c;up=dy*cp-fw*sp;dep=dy*sp+fw*cp
 return np.array([cx+focal*right/dep,cy-focal*up/dep,dep])
def ground_at(sx,sy,origin,yaw,pitch,focal=620,cx=640,cy=360):
 rx=(sx-cx)/focal;ry=(cy-sy)/focal;s,c=math.sin(yaw),math.cos(yaw);sp,cp=math.sin(pitch),math.cos(pitch)
 up=ry*cp+sp;fw=cp-ry*sp
 if up>=-1e-5:return None
 t=-origin[1]/up
 return np.array([origin[0]+t*(rx*c+fw*s),0,origin[2]+t*(-rx*s+fw*c)])
def clip(points,near=.602):
 out=[]
 for i,q in enumerate(points):
  p=points[i-1];pin=p[2]>=near;qin=q[2]>=near
  if pin!=qin:
   t=(near-p[2])/(q[2]-p[2]);v=p+(q-p)*t;v[2]=near;out.append(v)
  if qin:out.append(q)
 return np.asarray(out)

required=[f'drone_{m}{suffix}' for m in ['131','136','238','x'] for suffix in ['', '_side']]
required += [f'{prefix}_{env}' for prefix in ['env','terrain'] for env in ['desert','urban','naval','special']]
required += ['menu_hero','material_wall','material_roof','material_concrete','shadow_soft','prop_tree1','prop_tree2','prop_rock1','prop_rock2','hud_target']
for name in required:
 path=GFX/(name+'.png');check('exists '+name,path.is_file())
 with Image.open(path) as im:im.verify()
check('26 expected runtime images',len(required)==26)
for model in ['131','136','238','x']:
 a=np.asarray(Image.open(GFX/f'drone_{model}.png').convert('RGBA'))
 check('transparent corners '+model,all(a[y,x,3]==0 for x,y in [(0,0),(511,0),(0,511),(511,511)]))
 check('visible aircraft '+model,((a[:,:,3]>245).mean()>.12))
 chroma=(a[:,:,0].astype(int)-a[:,:,1]>90)&(a[:,:,2].astype(int)-a[:,:,1]>90)&(a[:,:,3]>220)
 check('magenta removed '+model,not chroma.any())
rng=np.random.default_rng(42);max_error=0
for i in range(1000):
 origin=np.array([rng.uniform(-3000,3000),rng.uniform(5,240),rng.uniform(-3000,3000)])
 yaw=rng.uniform(-math.pi,math.pi);pitch=rng.uniform(-.95,.6)
 horizon=360+620*math.tan(pitch);sx=rng.uniform(-1280,2560);sy=horizon+rng.uniform(2,1400)
 p=ground_at(sx,sy,origin,yaw,pitch)
 check_error=np.abs(project(p,origin,yaw,pitch)[:2]-[sx,sy]).max();max_error=max(max_error,check_error)
check('1000 ground projection round trips',max_error<1e-6)
for i in range(500):
 points=rng.uniform(-10,10,(4,3));out=clip(points)
 if len(out):
  check_ok=len(out)<=8 and (out[:,2]>=.602-1e-9).all() and np.isfinite(out).all()
  if not check_ok:raise AssertionError('clip failed')
check('500 near clipping cases',True)
check('half clipped quad retained',len(clip(np.array([[-1,0,-1],[1,0,2],[1,1,2],[-1,1,-1]],dtype=float)))==4)
check('fully behind quad hidden',len(clip(np.array([[-1,0,-1],[1,0,-1],[1,1,-2],[-1,1,-2]],dtype=float)))==0)
check('perspective size halves at twice the depth',abs(620*10/100/2-620*10/200)<1e-8)
# Lexical delimiter check only: catches truncation, not Kotlin type/compiler errors.
for path in ROOT.glob('app/src/main/java/**/*.kt'):
 text=path.read_text();text=re.sub(r'/\*.*?\*/|//[^\n]*','',text,flags=re.S);text=re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'','',text)
 stack=[];pairs={')':'(',']':'[','}':'{'}
 for ch in text:
  if ch in '([{':stack.append(ch)
  elif ch in pairs:
   if not stack or stack.pop()!=pairs[ch]:raise AssertionError('delimiter '+str(path))
 check('balanced delimiters '+path.name,not stack)
manifest={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(ROOT.rglob('*')) if p.is_file() and 'checks' not in p.parts and '__pycache__' not in p.parts}
(ROOT/'checks').mkdir(exist_ok=True)
(ROOT/'checks/SHA256.json').write_text(json.dumps(manifest,indent=2))
report={'result':'PASS','scope':'Independent asset, lexical and numerical checks only','android_compile':'NOT RUN: Android SDK, Gradle and Kotlin compiler unavailable','device_test':'NOT RUN','test_count':len(checks),'ground_max_pixel_error':max_error,'checks':checks}
(ROOT/'checks/verification.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k!='checks'},indent=2))
