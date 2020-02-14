package com.mirroringsample.encode

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class MediaProjectionModel : Activity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            projection(mediaProjectionManager.getMediaProjection(resultCode, data))
        }
        finish()
    }


    companion object {
        private const val REQUEST_CAPTURE = 1
        // projectionはラムダ式
        private var projection: (MediaProjection?) -> Unit = {}

        // 関数の引数はContextと戻り値なしラムダ式、戻り値なし
        //
        val run: (context: Context, (MediaProjection?) -> Unit) -> Unit = { context, callback ->
            projection = callback
            context.startActivity(Intent(context, MediaProjectionModel::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

}
