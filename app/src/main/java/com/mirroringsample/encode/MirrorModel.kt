package com.mirroringsample.encode

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MirrorModel(private val metrics: DisplayMetrics, private val callback: MirrorCallback, private val context: Context) : ImageReader.OnImageAvailableListener {
    enum class StatesType { Stop, Running }

    private var reader:ImageReader? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private lateinit var heepPlane: Image.Plane
    private lateinit var heepBitmap: Bitmap

    private val scale = 0.5 // 端末ごとに調整して下さい

    fun setMediaProjection(mediaProjection: MediaProjection?) {
        this.mediaProjection = mediaProjection
    }

    fun disconnect() {
        runCatching {
            virtualDisplay?.release()
            mediaProjection?.stop()
        }.exceptionOrNull()?.printStackTrace()
        callback.changeState(StatesType.Stop)
    }

    @SuppressLint("WrongConstant")
    fun setupVirtualDisplay(inputSurface:Surface, width:Int, height:Int):Unit {

//        reader = ImageReader.newInstance(width , height, PixelFormat.RGBA_8888, 2).also { it.setOnImageAvailableListener(this, null) }
        Log.d("AASSAA", "setupVirtualDisplay")
        Log.d("AASSAA", inputSurface.isValid.toString())
        Log.d("AASSAA", "Width : " + width.toString())
        Log.d("AASSAA", "Height : " + height.toString())

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Capturing Display",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
//            null,
            inputSurface,
            null,
            null
        )

        callback.changeState(StatesType.Running)
    }

    //
    override fun onImageAvailable(reader: ImageReader) {
        reader.acquireLatestImage().use { img ->
            runCatching {
                Log.d("AASSAA_codec_MirrorModel", "onImageAvailable")
                heepPlane = img?.planes?.get(0) ?: return@use null
                val width = heepPlane.rowStride / heepPlane.pixelStride
                val height = (metrics.heightPixels * scale).toInt()
                heepBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(heepPlane.buffer) }
                callback.changeBitmap(heepBitmap)
            }
        }
    }

    interface MirrorCallback {
        fun changeState(states: StatesType)
        fun changeBitmap(image: Bitmap?)
    }
}
