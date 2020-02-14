package com.mirroringsample.encode

import android.content.Context
import android.graphics.Bitmap
import android.media.ImageReader
import android.util.Log
import androidx.lifecycle.MutableLiveData
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class MirroringUsecase(private val context: Context,  width:Int, height:Int): MirrorModel.MirrorCallback {

    private var mirrorModel: MirrorModel = MirrorModel(context.resources.displayMetrics, this, context)

    lateinit var surface: Surface
    lateinit var codec: MediaCodec//エンコーダ

    val mirrorWidth = width
    val MirrorHeight = height

    init {
        changeState(MirrorModel.StatesType.Stop)
    }

    fun start() {
        val bitRate = 5600000
        val fps = 30
        val iFrameInterval = 1

        MediaProjectionModel.run(context) {
            runCatching {
                mirrorModel.setMediaProjection(it)
                surface = PrepareEncoder(
                    mirrorWidth,
                    MirrorHeight,
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    bitRate,// bitrate
                    fps, // fps
                    iFrameInterval//Iフレームは固定で
                )
                mirrorModel.setupVirtualDisplay(surface, mirrorWidth, MirrorHeight)

            }.exceptionOrNull()?.printStackTrace()
        }
    }

    fun restart() {
        runCatching {
            mirrorModel.setupVirtualDisplay(surface, mirrorWidth, MirrorHeight)
        }.exceptionOrNull()?.printStackTrace()
    }

    fun stop() {
        mirrorModel.disconnect()
    }

    override fun changeState(states: MirrorModel.StatesType) {
        stateLivaData.value = states
    }

    override fun changeBitmap(image: Bitmap?) {
        // DoNothing
    }


    //エンコーダの準備
    @Throws(Exception::class)
    private fun PrepareEncoder(
        width:Int,
        height:Int,
        MIME_TYPE: String,
        BIT_RATE: Int,
        FPS: Int,
        IFRAME_INTERVAL: Int
    ):Surface {
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)

        //フォーマットのプロパティを設定
        //最低限のプロパティを設定しないとconfigureでエラーになる
        // 今回の試験機はPixel3とZenfone5なので共通のカラーフォーマットを指定
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, FPS)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)

        //エンコーダの取得
        codec = MediaCodec.createEncoderByType(MIME_TYPE)

        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // 入力はSurfaceから自動なので今回は使用しない
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                val buffer = codec.getOutputBuffer(index)
                val array = ByteArray(info.size + 4)

                buffer?.run {
                    this.get(array, 0, info.size)

                    // emulation_prevention_three_byte
                    // 0x00000002はH264のNALパケットには出てこない
                    array[0 + info.size] = 0.toByte()
                    array[1 + info.size] = 0.toByte()
                    array[2 + info.size] = 0.toByte()
                    array[3 + info.size] = 2.toByte()
                }

                val intent = Intent()
                // フィルタのアクション名と合わせる必要があります
                intent.action = "DISPLAY_UPDATE"
                intent.putExtra("IMAGE", array)
                // アプリ内のみに対してブロードキャスト送信
                val mBroadcastReceiver = LocalBroadcastManager.getInstance(context)
                mBroadcastReceiver.sendBroadcast(intent)

                //バッファを解放
                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                // Error時の処理を書く、理解が深まってから
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                // codecのフォーマットが変更になったとき呼ばれる
                // 理解が深まってから
            }
        })

        //エンコーダを設定
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        //エンコーダにフレームを渡すのに使うSurfaceを取得
        //configureとstartの間で呼ぶ必要あり
        val surface =  codec.createInputSurface()

        codec.start()
        return surface

    }

    companion object {
        val stateLivaData: MutableLiveData<MirrorModel.StatesType> = MutableLiveData()
//        val imageLivaData: MutableLiveData<Bitmap> = MutableLiveData()
    }
}