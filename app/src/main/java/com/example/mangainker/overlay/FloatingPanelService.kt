package com.example.mangainker.overlay
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mangainker.core.AppState
class FloatingPanelService:Service(){
 private lateinit var wm:WindowManager
 private var panel:LinearLayout?=null
 private lateinit var status:TextView
 private lateinit var actions:LinearLayout
 override fun onCreate(){super.onCreate();if(!Settings.canDrawOverlays(this)){AppState.status("Allow Display over other apps first.");stopSelf();return};wm=getSystemService(Context.WINDOW_SERVICE) as WindowManager;showPanel()}
 private fun showPanel(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,10,14,10);setBackgroundColor(0xEE171B22.toInt())};root.addView(TextView(this).apply{text="MANGA INKER";textSize=14f;setTextColor(0xFFFFFFFF.toInt())});status=TextView(this).apply{textSize=12f;setTextColor(0xFFB8DCEB.toInt());setPadding(0,8,0,8)};root.addView(status);actions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};actions.addView(button("Start"){AppState.start()});actions.addView(button("Pause"){AppState.pause()});actions.addView(button("Resume"){AppState.resume()});actions.addView(button("Stop"){AppState.stop()});root.addView(actions);root.addView(button("Minimize / Restore"){actions.visibility=if(actions.visibility==View.VISIBLE)View.GONE else View.VISIBLE});val lp=WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.END;x=16;y=100};panel=root;wm.addView(root,lp);root.post(object:Runnable{override fun run(){if(panel!==root)return;status.text=when{AppState.running.value&&!AppState.paused.value->"● RUNNING";AppState.paused.value->"Ⅱ PAUSED";else->AppState.status.value};root.postDelayed(this,250L)}})}
 private fun button(label:String,click:()->Unit)=Button(this).apply{text=label;isAllCaps=false;setOnClickListener{click()}}
 override fun onDestroy(){panel?.let{runCatching{wm.removeView(it)}};panel=null;super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
