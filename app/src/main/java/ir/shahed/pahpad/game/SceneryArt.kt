package ir.shahed.pahpad.game

import android.content.Context
import android.graphics.*
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.data.Env
import kotlin.math.*

/** Cached sky and two world-locked ground layers. No per-frame bitmap generation. */
class SceneryArt(private val context:Context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix=Matrix();private val fogMatrix=Matrix();private val skyMatrix=Matrix()
    private val sunMatrix=Matrix()
    private val src=FloatArray(8);private val dst=FloatArray(8);private val ground=FloatArray(3)
    private val groundCorners=Array(4){FloatArray(3)}
    private val shaderCache=HashMap<Env,BitmapShader>()
    private var sky:LinearGradient?=null;private var haze:LinearGradient?=null
    private var lastEnv:Env?=null;private var lastHeight=0f
    private val skyline=HashMap<Env,Bitmap>()
    private val rect=RectF()
    private val halo=RadialGradient(0f,0f,1f,intArrayOf(0x55FFE3AD,0x16F3D4B0,0x00E8C7A0),floatArrayOf(0f,.3f,1f),Shader.TileMode.CLAMP)
    private var detailBitmap:Bitmap?=null
    private var detailShader:BitmapShader?=null
    /** World-space sea animation phase, advanced by the render loop (seconds). */
    private var seaPhase=0f
    /** Accumulated seconds; read by draw() so the sea never sits perfectly still. */
    fun advance(dt:Float){ seaPhase+=dt }

    /**
     * Fraction of each env_* sheet that is sky, measured from the shipped PNGs.
     *
     * A fixed 40% cut was wrong for three of the four sheets: naval, urban and
     * special all have their horizon well above 40%, so the strip was showing
     * photographed water/town/mountains on top of the renderer's own ground and
     * water plane. Only the desert sheet's horizon happens to sit near 40%.
     *
     * These are measured constants, not a runtime edge search, because the
     * sheets are photos: a gradient scan locks onto cloud and building edges,
     * and a full w*h pixel read on the first draw of each env is pure waste.
     * `checks/horizon_sheet.py` locks them against the PNGs.
     */
    private fun skyFraction(env:Env):Float = when(env){
        Env.NAVAL -> 0.24f
        Env.URBAN -> 0.25f
        Env.SPECIAL -> 0.67f
        else -> 0.55f
    }
    private fun detail():BitmapShader {
        detailShader?.let{return it}
        val random=java.util.Random(71043L)
        val pixels=IntArray(64*64){
            val v=120+random.nextInt(33)
            Color.rgb(v+4,v+2,v)
        }
        val bitmap=Bitmap.createBitmap(pixels,64,64,Bitmap.Config.ARGB_8888)
        detailBitmap=bitmap
        return BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT).also{detailShader=it}
    }
    fun clear(){
        // Drop shader references first. Let GC reclaim bitmaps; Canvas may still reference them.
        shaderCache.clear();skyline.clear();detailShader=null;detailBitmap=null;sky=null;haze=null
    }
    fun name(env:Env)=env.name.lowercase(java.util.Locale.ROOT)
    fun draw(c:Canvas,scene:Scene3D,world:World,w:Float,h:Float) {
        if(w<=0f||h<=0f)return
        val env=world.level.env;val cam=scene.cam
        val skyTop=when(env){Env.NAVAL->0xFF4C7F9F.toInt();Env.SPECIAL->0xFF5E687F.toInt();Env.URBAN->0xFF526F8A.toInt();else->0xFF527E9D.toInt()}
        val middle=when(env){Env.NAVAL->0xFF97BECA.toInt();Env.SPECIAL->0xFFB6A3A3.toInt();Env.URBAN->0xFFB1BBC2.toInt();else->0xFFD1B696.toInt()}
        val fog=when(env){Env.NAVAL->0xFFB3CDD2.toInt();Env.SPECIAL->0xFFD3B8A8.toInt();Env.URBAN->0xFFD2C9B8.toInt();else->0xFFE1C7A1.toInt()}
        scene.fogColor=fog
        if(sky==null||lastEnv!=env||lastHeight!=h){
            sky=LinearGradient(0f,-h*1.5f,0f,h*.12f,intArrayOf(skyTop,middle,fog),floatArrayOf(0f,.68f,1f),Shader.TileMode.CLAMP)
            haze=LinearGradient(0f,0f,0f,h*.7f,intArrayOf(fog,Theme.withAlpha(fog,.65f),Theme.withAlpha(fog,0f)),floatArrayOf(0f,.18f,1f),Shader.TileMode.CLAMP)
            lastEnv=env;lastHeight=h
        }
        val horizon=cam.horizonY()
        skyMatrix.setTranslate(0f,horizon);sky?.setLocalMatrix(skyMatrix)
        paint.shader=sky;paint.alpha=255;c.drawRect(-w,-h,w*2,h*2,paint);paint.shader=null
        if(cam.project(cam.x+scene.sunX*1000f,cam.y+scene.sunY*1000f,cam.z+scene.sunZ*1000f,ground)){
            val radius=h*.14f
            sunMatrix.setScale(radius,radius);sunMatrix.postTranslate(ground[0],ground[1]);halo.setLocalMatrix(sunMatrix)
            paint.shader=halo;c.drawCircle(ground[0],ground[1],radius,paint);paint.shader=null
            paint.color=0xDDFFF0CF.toInt();c.drawCircle(ground[0],ground[1],h*.013f,paint)
        }
        val strip=skyline.getOrPut(env){
            val source=Gfx.get(context,"env_${name(env)}")
            val bitmap=Bitmap.createBitmap(768,180,Bitmap.Config.ARGB_8888)
            if(source!=null){
                // Crop the sky only. The renderer already draws a real water/ground
                // plane below `horizon`, so any water or skyline baked into the
                // strip is duplicated geometry floating over the playfield.
                val skyOnly=(source.height*skyFraction(env)).toInt().coerceAtLeast(1)
                val sc=Canvas(bitmap);val p=Paint(Paint.FILTER_BITMAP_FLAG)
                sc.drawBitmap(source,Rect(0,0,source.width,skyOnly),Rect(0,0,768,180),p)
                p.shader=LinearGradient(0f,0f,0f,180f,intArrayOf(0x00FFFFFF,0xFFFFFFFF.toInt(),0xFFFFFFFF.toInt(),0x00FFFFFF),floatArrayOf(0f,.25f,.75f,1f),Shader.TileMode.CLAMP)
                p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN);sc.drawRect(0f,0f,768f,180f,p)
            }
            bitmap
        }
        val bandH=h*.34f;val bandW=bandH*768f/180f
        val scroll=cam.yaw/(2f*PI.toFloat())*bandW*4f
        var tileIndex=floor((-w+scroll)/bandW).toInt()
        var bx=tileIndex*bandW-scroll
        while(bx<w*2f){
            rect.set(bx,horizon-bandH*.88f,bx+bandW,horizon+bandH*.12f)
            c.save()
            if((tileIndex and 1)!=0)c.scale(-1f,1f,rect.centerX(),0f)
            paint.alpha=100;c.drawBitmap(strip,null,rect,paint)
            c.restore();bx+=bandW;tileIndex++
        }
        paint.alpha=255
        val top=max(-h,horizon+1.5f);val bottom=h*2f
        if(top>=bottom)return
        paint.color=when(env){Env.NAVAL->0xFF265B6F.toInt();Env.SPECIAL->0xFF777B65.toInt();Env.URBAN->0xFF717169.toInt();else->0xFFA98F6A.toInt()}
        c.drawRect(-w,top,w*2f,bottom,paint)
        dst[0]=-w;dst[1]=top;dst[2]=w*2;dst[3]=top;dst[4]=w*2;dst[5]=bottom;dst[6]=-w;dst[7]=bottom
        var valid=true
        for(i in 0..3)if(!cam.groundAt(dst[i*2],dst[i*2+1],groundCorners[i])){valid=false;break}
        val bitmap=Gfx.get(context,"terrain_${name(env)}")
        if(valid&&bitmap!=null){
            val shader=shaderCache.getOrPut(env){BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT)}
            val tile=if(env==Env.NAVAL)110f else 180f
            // Naval water drifts against the wind; land keeps the static grid.
            val drift=if(env==Env.NAVAL)seaPhase*3.4f else 0f
            val ox=floor((cam.x+drift)/tile)*tile;val oz=floor(cam.z/tile)*tile
            for(i in 0..3){src[i*2]=(groundCorners[i][0]-ox)*bitmap.width/tile;src[i*2+1]=(groundCorners[i][2]-oz)*bitmap.height/tile}
            if(matrix.setPolyToPoly(src,0,dst,0,4)){
                shader.setLocalMatrix(matrix);paint.shader=shader;paint.alpha=255;c.drawRect(-w,top,w*2,bottom,paint);paint.shader=null
            }
            // Incommensurate period relative to base terrain, anchored in world
            // space. It must share the sea drift, else the fine grain reads as a
            // second, frozen wave field sliding over the moving water.
            val period=997f
            val detailX=floor((cam.x+drift)/period)*period;val detailZ=floor(cam.z/period)*period
            for(i in 0..3){src[i*2]=(groundCorners[i][0]-detailX)*64f/period;src[i*2+1]=(groundCorners[i][2]-detailZ)*64f/period}
            if(matrix.setPolyToPoly(src,0,dst,0,4)){
                val d=detail();d.setLocalMatrix(matrix);paint.shader=d;paint.alpha=22
                c.drawRect(-w,top,w*2,bottom,paint);paint.shader=null;paint.alpha=255
            }
        }
        fogMatrix.setTranslate(0f,horizon);haze?.setLocalMatrix(fogMatrix);paint.shader=haze;paint.alpha=255
        c.drawRect(-w,top,w*2,bottom,paint);paint.shader=null
    }
    fun roads(scene:Scene3D,world:World) {
        if(world.level.env!=Env.URBAN&&world.level.env!=Env.SPECIAL)return
        val cam=scene.cam
        val row0=floor((cam.z-120f-CityLayout.START_Z)/CityLayout.ROW_SPACING).toInt()-1
        val row1=row0+19
        for(row in row0..row1){
            // Cross streets connect the centers between adjacent building rows.
            val cross=CityLayout.crossZ(row)
            scene.quad(-400f,.04f,cross-CityLayout.HALF_ROAD,400f,.04f,cross-CityLayout.HALF_ROAD,400f,.04f,cross+CityLayout.HALF_ROAD,-400f,.04f,cross+CityLayout.HALF_ROAD,0xFF555C5B.toInt())
            for(col in -4..4){
                val x=col*CityLayout.COLUMN_SPACING+39f
                scene.quad(x-CityLayout.HALF_ROAD,.05f,cross,x+CityLayout.HALF_ROAD,.05f,cross,x+CityLayout.HALF_ROAD,.05f,cross+CityLayout.ROW_SPACING,x-CityLayout.HALF_ROAD,.05f,cross+CityLayout.ROW_SPACING,0xFF555C5B.toInt())
                for(i in 0..2){val zz=cross+i*32f;scene.quad(x-.35f,.08f,zz,x+.35f,.08f,zz,x+.35f,.08f,zz+12f,x-.35f,.08f,zz+12f,0xFFB8BAA2.toInt())}
            }
        }
    }
    fun shadow(c:Canvas,scene:Scene3D,x:Float,z:Float,w:Float,d:Float,alpha:Float=.22f) {
        val bmp=Gfx.get(context,"shadow_soft")?:return
        for(i in 0..3){
            val px=x+if(i==0||i==3)-w else w
            val pz=z+if(i<2)-d else d
            if(!scene.cam.project(px,.12f,pz,ground))return
            dst[i*2]=ground[0];dst[i*2+1]=ground[1]
        }
        src[0]=0f;src[1]=0f;src[2]=bmp.width.toFloat();src[3]=0f;src[4]=src[2];src[5]=bmp.height.toFloat();src[6]=0f;src[7]=src[5]
        if(matrix.setPolyToPoly(src,0,dst,0,4)){
            paint.shader=null;paint.alpha=(alpha*255).toInt().coerceIn(0,255)
            c.drawBitmap(bmp,matrix,paint);paint.alpha=255
        }
    }
}
