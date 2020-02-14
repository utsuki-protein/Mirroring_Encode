package com.mirroringsample.encode

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService

class MirroringService : Service() {

    lateinit var mirroringUsecase: MirroringUsecase
    lateinit var mHandler:Handler

    var mirrorWidth = 0
    var mirrorHeight = 0

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?).also { manager ->
            kotlin.runCatching {
                if (manager?.getNotificationChannel(CHANNEL_ID) == null) manager?.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                        description = CHANNEL_DESC
                    }
                )
            }
        }

        if(intent != null) {
            mirrorWidth = intent.getIntExtra("width", 1)
            mirrorHeight = intent.getIntExtra("height", 2)
        }
        mirroringUsecase = MirroringUsecase(this, mirrorWidth, mirrorHeight)
        mirroringUsecase.start()
        startForeground(ID, NotificationCompat.Builder(this, CHANNEL_ID).apply {
            color = ContextCompat.getColor(applicationContext, R.color.common_google_signin_btn_tint)
            setColorized(true)
            setSmallIcon(R.mipmap.ic_launcher)
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setContentTitle(NOTIFY_TITLE)
            setContentText(NOTIFY_TEXT)
        }.build())

        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(configChangeBroadcastReciver, IntentFilter("android.intent.action.CONFIGURATION_CHANGED"))
    }

    override fun onDestroy() {
        unregisterReceiver(configChangeBroadcastReciver)
        mirroringUsecase.stop()
        super.onDestroy()
    }

    private val configChangeBroadcastReciver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            mirroringUsecase.restart()
        }
    }

    companion object {
        const val ID = 1
        const val CHANNEL_ID = "mirrorForeground"
        const val CHANNEL_NAME = "ミラーリング"
        const val CHANNEL_DESC = "録画するよ"
        const val NOTIFY_TITLE = "ScreenMirror"
        const val NOTIFY_TEXT = "ミラーリング中だよ"


        val start: (context:Context, width:Int, height:Int) -> Unit = { context, width, height ->
            val intent = Intent(context, MirroringService::class.java)
            // Oreoから制約が入った
            if (Build.VERSION.SDK_INT >= 26){
                intent.putExtra("width", width)
                intent.putExtra("height", height)
                startForegroundService(context, intent)
            }
            else{
                intent.putExtra("width", width)
                intent.putExtra("height", height)
                context.startService(intent)
            }
        }

        val stop: (Context) -> Unit = {
            val intent = Intent(it, MirroringService::class.java)
            it.stopService(intent)
        }
    }
}