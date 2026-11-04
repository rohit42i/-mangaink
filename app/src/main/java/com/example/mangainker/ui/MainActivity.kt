package com.example.mangainker.ui
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mangainker.core.AppState
import com.example.mangainker.gesture.GestureAccessibilityService
import com.example.mangainker.overlay.FloatingPanelService
class MainActivity:Activity(){
 private lateinit var overlayRow:TextView
 private lateinit var accessRow:TextView
 private lateinit var status:TextView
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);render()}
 override fun onResume(){super.onResume();refresh()}
 private fun render(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(26,38,26,24);setBackgroundColor(0xFFF5F6F8.toInt())};root.addView(TextView(this).apply{text="MANGA INKER";textSize=26f;setTextColor(0xFF17212B.toInt())});root.addView(TextView(this).apply{text="Stage 1 • Touch gesture test";textSize=14f;setTextColor(0xFF596674.toInt());setPadding(0,8,0,24)});overlayRow=row(root,"Overlay");root.addView(TextView(this).apply{text="Allows the floating Start, Pause, Resume and Stop panel to appear over ibisPaint.";textSize=12f;setTextColor(0xFF596674.toInt())});addButton(root,"Open overlay settings"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))};accessRow=row(root,"Accessibility");root.addView(TextView(this).apply{text="Allows Manga Inker to send touch gestures. It does not read screen content.";textSize=12f;setTextColor(0xFF596674.toInt())});addButton(root,"Open accessibility settings"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))};status=TextView(this).apply{text="Open ibisPaint X and select a brush before testing.";textSize=14f;setTextColor(0xFF34414D.toInt());setPadding(0,22,0,16)};root.addView(status);addButton(root,"Test stroke"){if(!accessibilityOn())status.text="Enable Manga Inker accessibility first." else if(!Settings.canDrawOverlays(this))status.text="Enable Overlay permission first." else{startService(Intent(this,FloatingPanelService::class.java));status.text="Floating panel ready. Open ibisPaint X, select a brush, then tap Start on the panel."}};addButton(root,"Show floating controls"){if(!Settings.canDrawOverlays(this))status.text="Enable Overlay permission first." else{startService(Intent(this,FloatingPanelService::class.java));status.text="Floating controls opened."}};addButton(root,"Stop"){AppState.stop();status.text=AppState.status.value};root.addView(TextView(this).apply{text="Local only • No network, analytics, screenshots, storage or uploads";textSize=12f;setTextColor(0xFF687582.toInt());setPadding(0,26,0,0)});setContentView(root)}
 private fun row(parent:LinearLayout,name:String)=TextView(this).also{it.text="$name: checking…";it.textSize=16f;it.setTextColor(0xFF17212B.toInt());it.setPadding(0,18,0,6);parent.addView(it)}
 private fun addButton(parent:LinearLayout,label:String,action:()->Unit){parent.addView(Button(this).apply{text=label;isAllCaps=false;setOnClickListener{action()}})}
 private fun refresh(){overlayRow.text="Overlay: ${if(Settings.canDrawOverlays(this))"Enabled" else "Not enabled"}";accessRow.text="Accessibility: ${if(accessibilityOn())"Enabled" else "Not enabled"}"}
 private fun accessibilityOn():Boolean{val expected="$packageName/${GestureAccessibilityService::class.java.name}";val enabled=Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty();return enabled.split(':').any{it.equals(expected,true)}}
}
