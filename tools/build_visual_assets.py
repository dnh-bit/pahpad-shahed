"""Rebuild art: python tools/build_visual_assets.py drone-sheet.png environment-sheet.png
Requires Pillow and numpy. Source sheets are AI-generated; textures are procedural.
"""
from pathlib import Path
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageOps
OUT=Path(__file__).resolve().parents[1]/'app/src/main/assets/gfx'
rng=np.random.default_rng(136)
def noise(n):
 y,x=np.mgrid[:n,:n]*(2*np.pi/n); a=np.zeros((n,n))
 for f,g in [(1,1),(2,.55),(4,.28),(8,.17),(16,.09),(32,.04)]:
  for _ in range(5):
   k,l=rng.integers(-f,f+1,2);a+=g*np.cos(k*x+l*y+rng.random()*2*np.pi)
 return (a-a.mean())/(a.std()+1e-6)
def save(name,a): Image.fromarray(np.uint8(np.clip(a,0,255))).save(OUT/(name+'.png'),optimize=True)
def sprites(sheet):
 im=Image.open(sheet).convert('RGB');w,h=im.width//2,im.height//2
 for i,m in enumerate(['131','136','238','x']):
  a=np.asarray(im.crop((i%2*w,i//2*h,(i%2+1)*w,(i//2+1)*h))).astype(float)
  strength=np.minimum(a[:,:,0],a[:,:,2])-a[:,:,1];alpha=np.clip((105-strength)/65,0,1)
  spill=(strength>22)&(alpha>0)&(alpha<1)
  for ch in [0,2]:a[:,:,ch][spill]=np.minimum(a[:,:,ch][spill],a[:,:,1][spill]+20)
  s=Image.fromarray(np.uint8(np.clip(np.dstack([a,alpha*255]),0,255)));s=s.crop(s.getbbox());s.thumbnail((460,460),Image.Resampling.LANCZOS)
  out=Image.new('RGBA',(512,512));out.alpha_composite(s,((512-s.width)//2,(512-s.height)//2));out.save(OUT/f'drone_{m}.png',optimize=True)
  display=out.rotate(-32,resample=Image.Resampling.BICUBIC,expand=True);display.thumbnail((480,300),Image.Resampling.LANCZOS);display.save(OUT/f'drone_{m}_side.png',optimize=True)
def environments(sheet):
 im=Image.open(sheet).convert('RGB');w,h=im.width//2,im.height//2
 for i,n in enumerate(['desert','urban','naval','special']):
  c=im.crop((i%2*w+2,i//2*h+2,(i%2+1)*w-2,(i//2+1)*h-2));ImageOps.fit(c,(768,432),method=Image.Resampling.LANCZOS).save(OUT/f'env_{n}.png',optimize=True)
 bg=Image.open(OUT/'env_desert.png').convert('RGBA');s=Image.open(OUT/'drone_136.png').rotate(-24,expand=True,resample=Image.Resampling.BICUBIC);s.thumbnail((390,350),Image.Resampling.LANCZOS);bg.alpha_composite(s,(340,40));bg.convert('RGB').save(OUT/'menu_hero.png',optimize=True)
def textures():
 n=512;a=noise(n);b=noise(n);y,x=np.mgrid[:n,:n]
 for name,base,amount in [('desert',[169,143,106],13),('special',[108,111,91],14),('urban',[113,113,105],8)]:
  shade=a*amount+b*2
  if name=='desert':shade+=np.sin(2*np.pi*(y*12/n+.16*np.sin(x*2*np.pi/n)))*2
  save('terrain_'+name,np.array(base)[None,None,:]+shade[:,:,None])
 wave=np.sin(2*np.pi*(y*32/n+.28*np.sin(x*8*np.pi/n)));crest=np.maximum(0,wave-.65)**3*160
 save('terrain_naval',np.array([38,91,111])[None,None,:]+(a*6+wave*6+crest)[:,:,None])
 a=noise(256);wall=Image.fromarray(np.uint8(np.clip(np.array([165,162,147])[None,None,:]+a[:,:,None]*5,0,255)));d=ImageDraw.Draw(wall)
 for y in range(0,256,32):
  d.line((0,y,256,y),fill=(139,138,128))
  for x in range(0,256,32):
   d.rectangle((x+8,y+8,x+22,y+25),fill=(57,70,73));d.rectangle((x+9,y+9,x+20,y+12),fill=(103,119,122));d.line((x+7,y+26,x+24,y+26),fill=(205,201,181),width=2)
 wall.save(OUT/'material_wall.png');save('material_concrete',np.array([151,149,138])[None,None,:]+a[:,:,None]*9)
 roof=Image.fromarray(np.uint8(np.clip(np.array([120,123,116])[None,None,:]+a[:,:,None]*5,0,255)));d=ImageDraw.Draw(roof)
 for y in range(0,256,64):d.line((0,y,256,y),fill=(91,95,89),width=2)
 for x in range(0,256,64):d.line((x,0,x,256),fill=(135,137,128))
 roof.save(OUT/'material_roof.png')
 shadow=Image.new('RGBA',(128,128));d=ImageDraw.Draw(shadow);d.ellipse((18,24,110,104),fill=(18,24,21,150));shadow.filter(ImageFilter.GaussianBlur(12)).save(OUT/'shadow_soft.png')
def props():
 for variant in [1,2]:
  tree=Image.new('RGBA',(256,256));d=ImageDraw.Draw(tree);d.polygon([(118,242),(124,100),(134,102),(140,242)],fill=(88,70,46))
  for _ in range(26):
   x=int(rng.normal(128,40));y=int(rng.normal(100,25));d.line((130,195,x,y),fill=(88,78,52),width=3)
  for _ in range(950):
   x,y=rng.normal(128,37),rng.normal(99,27)
   if ((x-128)/85)**2+((y-105)/66)**2>1:continue
   r=int(rng.integers(2,8));light=(1-x/256)*35+(1-y/256)*22+rng.normal(0,10);c=tuple(int(np.clip(v+light,0,255)) for v in (39,53,26));d.ellipse((x-r,y-r,x+r,y+r),fill=c+(255,))
  tree.save(OUT/f'prop_tree{variant}.png',optimize=True)
  mask=Image.new('L',(256,192));d=ImageDraw.Draw(mask);poly=[(18,158),(30,107),(63,57),(113,31),(176,43),(218,88),(243,161),(167,180),(60,176)]
  if variant==2:poly=[(x,int(y*.82+25)) for x,y in poly]
  d.polygon(poly,fill=255);a=noise(256)[:192,:];yy,xx=np.mgrid[:192,:256];light=20*(1-xx/256)-25*yy/192+a*11;rgb=np.clip(np.array([129,118,94])[None,None,:]+light[:,:,None],0,255).astype('uint8');rock=Image.fromarray(np.dstack([rgb,np.asarray(mask)]));d=ImageDraw.Draw(rock);d.line([(63,57),(106,94),(74,151)],fill=(96,86,68,255),width=2);d.line([(176,43),(155,105),(218,154)],fill=(102,92,73,255),width=2);rock.save(OUT/f'prop_rock{variant}.png',optimize=True)
if __name__=='__main__':
 sprites(sys.argv[1]);environments(sys.argv[2]);textures();props();print('Art rebuilt')
