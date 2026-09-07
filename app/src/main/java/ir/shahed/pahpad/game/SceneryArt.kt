package ir.shahed.pahpad.game

import android.content.Context
import android.graphics.*
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.data.Env
import kotlin.math.*

/** Visual-only materials. Mission physics, collision volumes and level data are unchanged. */
class SceneryArt(private val context:Context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix=Matrix();private val fogMatrix=Matrix()
    private val src=FloatArray(8);private val dst=FloatArray(8);private val ground=FloatArray(3)
    private val path=Path()
    private val shaderCache=HashMap<Env,BitmapShader>()
    private var sky:LinearGradient?=null;private var haze:LinearGradient?=null
    private var lastEnv:Env?=null;private var lastHeight=0f
    private val skyline=HashMap<Env,Bitmap>()
    private val rect=RectF()
    fun name(env:Env)=env.name.lowercase(java.util.Locale.ROOT)
    fun draw(c:Canvas,scene:Scene3D,world:World,w:Float,h:Float) {
        val env=world.level.env;val cam=scene.cam
        val skyTop=when(env){Env.NAVAL->0xFF5E92AF.toInt();Env.SPECIAL->0xFF7C9197.toInt();else->0xFF6C98B2.toInt()}
        val fog=when(env){Env.NAVAL->0xFFB3CDD2.toInt();Env.SPECIAL->0xFFC8C9B9.toInt();else->0xFFD6CFB7.toInt()}
        scene.fogColor=fog
        if(sky==null||lastEnv!=env||lastHeight!=h){
            sky=LinearGradient(0f,-h,0f,h,skyTop,fog,Shader.TileMode.CLAMP)
            haze=LinearGradient(0f,0f,0f,h*.7f,intArrayOf(fog,Theme.withAlpha(fog,.65f),Theme.withAlpha(fog,0f)),floatArrayOf(0f,.18f,1f),Shader.TileMode.CLAMP)
            lastEnv=env;lastHeight=h
        }
        val horizon=cam.horizonY()
        paint.shader=sky;paint.alpha=255;c.drawRect(-w,-h,w*2,h*2,paint);paint.shader=null
        // A cached horizon strip; its alpha feather avoids a rectangular photo edge.
        val strip=skyline.getOrPut(env){
            val source=Gfx.get(context,"env_${name(env)}")
            val bm=Bitmap.createBitmap(768,180,Bitmap.Config.ARGB_8888)
            if(source!=null){
                val sc=Canvas(bm);val p=Paint(Paint.FILTER_BITMAP_FLAG)
                sc.drawBitmap(source,Rect(0,0,source.width,(source.height*.40f).toInt()),Rect(0,0,768,180),p)
                p.shader=LinearGradient(0f,0f,0f,180f,intArrayOf(0x00FFFFFF,0xFFFFFFFF.toInt(),0xFFFFFFFF.toInt(),0x00FFFFFF),floatArrayOf(0f,.25f,.75f,1f),Shader.TileMode.CLAMP)
                p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN);sc.drawRect(0f,0f,768f,180f,p)
            };bm
        }
        val bandH=h*.34f;val bandW=bandH*768f/180f
        val offset=((cam.yaw/(2f*PI.toFloat())*bandW*4f)%bandW+bandW)%bandW
        var bx=-w-offset
        while(bx<w*2f){rect.set(bx,horizon-bandH*.88f,bx+bandW+1f,horizon+bandH*.12f);paint.alpha=155;c.drawBitmap(strip,null,rect,paint);bx+=bandW}
        paint.alpha=255
        val top=max(-h,horizon+1.5f);val bottom=h*2f
        if(top>=bottom)return
        paint.color=when(env){Env.NAVAL->0xFF265B6F.toInt();Env.SPECIAL->0xFF777B65.toInt();Env.URBAN->0xFF717169.toInt();else->0xFFA98F6A.toInt()}
        c.drawRect(-w,top,w*2f,bottom,paint)
        val bitmap=Gfx.get(context,"terrain_${name(env)}")
        if(bitmap!=null){
            val shader=shaderCache.getOrPut(env){BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)}
            dst[0]=-w;dst[1]=top;dst[2]=w*2;dst[3]=top;dst[4]=w*2;dst[5]=bottom;dst[6]=-w;dst[7]=bottom
            val tile=if(env==Env.NAVAL)110f else 180f
            val ox=floor(cam.x/tile)*tile;val oz=floor(cam.z/tile)*tile
            var valid=true
            for(i in 0..3){
                if(!cam.groundAt(dst[i*2],dst[i*2+1],ground)){valid=false;break}
                src[i*2]=(ground[0]-ox)*bitmap.width/tile;src[i*2+1]=(ground[2]-oz)*bitmap.height/tile
            }
            if(valid&&matrix.setPolyToPoly(src,0,dst,0,4)){
                shader.setLocalMatrix(matrix);paint.shader=shader;c.drawRect(-w,top,w*2,bottom,paint);paint.shader=null
            }
        }
        fogMatrix.setTranslate(0f,horizon);haze?.setLocalMatrix(fogMatrix);paint.shader=haze
        c.drawRect(-w,top,w*2,bottom,paint);paint.shader=null
    }
    fun roads(scene:Scene3D,world:World) {
        if(world.level.env!=Env.URBAN&&world.level.env!=Env.SPECIAL)return
        val cam=scene.cam;val start=floor((cam.z-120f)/95f).toInt();val end=start+17
        for(row in start..end){
            val z=row*95f
            scene.quad(-400f,.04f,z+27f,400f,.04f,z+27f,400f,.04f,z+43f,-400f,.04f,z+43f,0xFF555C5B.toInt())
            for(col in -4..4){
                val x=col*78f+39f
                scene.quad(x-8f,.05f,z,x+8f,.05f,z,x+8f,.05f,z+95f,x-8f,.05f,z+95f,0xFF555C5B.toInt())
                for(i in 0..2){val zz=z+i*32f;scene.quad(x-.35f,.08f,zz,x+.35f,.08f,zz,x+.35f,.08f,zz+12f,x-.35f,.08f,zz+12f,0xFFB8BAA2.toInt())}
            }
        }
    }
    fun shadow(c:Canvas,scene:Scene3D,x:Float,z:Float,w:Float,d:Float,alpha:Float=.22f) {
        // Ground-plane polygon instead of a screen-space fixed-size oval.
        val bmp=Gfx.get(context,"shadow_soft")?:return
        val points=arrayOf(floatArrayOf(x-w,.12f,z-d),floatArrayOf(x+w,.12f,z-d),floatArrayOf(x+w,.12f,z+d),floatArrayOf(x-w,.12f,z+d))
        for(i in 0..3){if(!scene.cam.project(points[i][0],points[i][1],points[i][2],ground))return;dst[i*2]=ground[0];dst[i*2+1]=ground[1]}
        src[0]=0f;src[1]=0f;src[2]=bmp.width.toFloat();src[3]=0f;src[4]=src[2];src[5]=bmp.height.toFloat();src[6]=0f;src[7]=src[5]
        if(matrix.setPolyToPoly(src,0,dst,0,4)){
            paint.alpha=(alpha*255).toInt().coerceIn(0,255);c.drawBitmap(bmp,matrix,paint);paint.alpha=255
        }
    }
}
