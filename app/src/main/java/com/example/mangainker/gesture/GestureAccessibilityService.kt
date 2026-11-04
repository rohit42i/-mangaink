package com.example.mangainker.gesture
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
class GestureAccessibilityService:AccessibilityService(){
 override fun onServiceConnected(){super.onServiceConnected();instance=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?)=Unit
 override fun onInterrupt(){Log.w(TAG,"accessibility interrupted")}
 override fun onDestroy(){if(instance===this)instance=null;super.onDestroy()}
 suspend fun drawPath(points:List<PointF>,durationMs:Long):Boolean{
  if(points.size<2)return false
  val path=Path().apply{moveTo(points.first().x,points.first().y);points.drop(1).forEach{lineTo(it.x,it.y)}}
  val duration=durationMs.coerceIn(1L,500L)
  val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0L,duration)).build()
  return suspendCancellableCoroutine{cont->
   val accepted=dispatchGesture(gesture,object:GestureResultCallback(){
    override fun onCompleted(g:GestureDescription?){Log.i(TAG,"completed duration=${duration}ms points=${points.size}");if(cont.isActive)cont.resume(true)}
    override fun onCancelled(g:GestureDescription?){Log.w(TAG,"cancelled duration=${duration}ms points=${points.size}");if(cont.isActive)cont.resume(false)}
   },null)
   Log.i(TAG,"dispatch accepted=$accepted duration=${duration}ms")
   if(!accepted&&cont.isActive)cont.resume(false)
   cont.invokeOnCancellation{Log.w(TAG,"player cancelled; active Android gesture may finish")}
  }
 }
 companion object{const val TAG="MI_GESTURE";@Volatile var instance:GestureAccessibilityService?=null;private set}
}
