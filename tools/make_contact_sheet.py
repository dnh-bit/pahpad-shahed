"""Creates an asset comparison, explicitly not an Android screenshot."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageOps
import zipfile,io,sys
root=Path(__file__).resolve().parents[1];gfx=root/'app/src/main/assets/gfx'
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
def f(n):return ImageFont.truetype(font,n)
canvas=Image.new('RGB',(1600,1240),(235,235,223));d=ImageDraw.Draw(canvas)
d.text((48,32),'PAHPAD / VISUAL UPDATE',font=f(34),fill=(38,52,49))
d.text((48,83),'Bundled asset comparison. Not an Android gameplay screenshot.',font=f(19),fill=(96,106,97))
d.text((48,141),'ORIGINAL 0.0.2',font=f(20),fill=(96,106,97));d.text((820,141),'UPDATED 0.0.3',font=f(20),fill=(38,52,49))
z=zipfile.ZipFile(sys.argv[1]);prefix=z.namelist()[0]+'app/src/main/assets/gfx/'
def old(n):return Image.open(io.BytesIO(z.read(prefix+n+'.png'))).convert('RGBA')
def new(n):return Image.open(gfx/(n+'.png')).convert('RGBA')
def box(im,x,y,w,h,bg=(213,216,202)):
 base=Image.new('RGBA',(w,h),bg+(255,));im=im.copy();im.thumbnail((w-24,h-24),Image.Resampling.LANCZOS);base.alpha_composite(im,((w-im.width)//2,(h-im.height)//2));canvas.paste(base.convert('RGB'),(x,y))
for col,load in [(48,old),(820,new)]:
 for i,m in enumerate(['131','136','238','x']):box(load('drone_'+m),col+i*180,178,172,190)
 for i,env in enumerate(['desert','urban','naval','special']):
  x=col+i%2*366;y=403+i//2*245
  im=ImageOps.fit(load('env_'+env),(354,204),method=Image.Resampling.LANCZOS);canvas.paste(im.convert('RGB'),(x,y));d.text((x,y+210),env.upper(),font=f(15),fill=(65,82,73))
 for i,n in enumerate(['prop_tree1','prop_rock1']):box(load(n),col+i*182,923,174,180)
 if load==new:
  for i,n in enumerate(['terrain_desert','terrain_naval']):box(load(n),col+364+i*182,923,174,180)
d.text((48,1151),'New in flight: world-locked terrain textures, shaded drone body, depth-sorted scenery.',font=f(20),fill=(38,52,49))
d.text((48,1189),'Kotlin + Android Canvas source included. Android build and device validation still required.',font=f(17),fill=(96,106,97))
canvas.save(root/'preview/asset-comparison.jpg',quality=92)
print('Asset preview saved')
