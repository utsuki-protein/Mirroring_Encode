package com.mirroringsample.encode

import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import androidx.core.content.ContextCompat
import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.analytics.FirebaseAnalytics
import java.lang.reflect.InvocationTargetException

class MainActivity : AppCompatActivity() {
    private val REQUEST_CONNECT_DEVICE_SECURE = 1
    private val REQUEST_CONNECT_DEVICE_INSECURE = 2
    private val REQUEST_ENABLE_BT = 3
    private val REQUEST_MEDIA_PROJECTION = 4

    val BLUETOOTH_CONNECTED = "BLUETOOTH_CONNECTED"
    val DISPLAY_UPDATE = "DISPLAY_UPDATE"

    // Message types sent from the BluetoothChatService Handler
    val MESSAGE_STATE_CHANGE = 1
    val MESSAGE_READ = 2
    val MESSAGE_WRITE = 3
    val MESSAGE_DEVICE_NAME = 4
    val MESSAGE_TOAST = 5
    // Key names received from the BluetoothChatService Handler
    val DEVICE_NAME = "device_name"
    val TOAST = "toast"
    /**
     * Name of the connected device
     */
    private var mConnectedDeviceName: String? = null
    /**
     * Array adapter for the conversation thread
     */
    private var mConversationArrayAdapter: ArrayAdapter<String>? = null
    /**
     * String buffer for outgoing messages
     */
    private var mOutStringBuffer: StringBuffer? = null
    /**
     * Local Bluetooth adapter
     */
    private var mBluetoothAdapter: BluetoothAdapter? = null
    /**
     * Member object for the chat services
     */
    private var mChatService: BluetoothChatService? = null


//    private

    // for ScreenCapture
    private var mMediaProjectionManager:MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private var mSurface: Surface? = null
    private var mSurfaceView: SurfaceView? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mImageReader: ImageReader? = null // スクリーンショット用
    private var bufferSize:Int = 0

    private var mWidth = 0
    private var mHeight = 0


    // Permission RequestCode
    private val MEDIA_STORAGE_ACCESS = 1
    private val MEDIA_CAMERA_ACCESS = 2

    val util:Utility = Utility()

    var isBtConnect:Boolean = false



    // for Analytics
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    // エンコーダ→サーフェスイメージへの変換係数
    // Surface作成時に代入
    var scaleToSurfaceX:Double = 0.0
    var scaleToSurfaceY:Double = 0.0
    // エンコードサイズ
    val encodeWidth = 480
    val encodeHeight = 360


    // Android LifeCycle
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            val activity = this
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show()
            activity.finish()
        }


        val rmsButton:Button = findViewById(R.id.rmsButton)
        rmsButton.setOnClickListener {
            // ミラーリングを開始
            if(isBtConnect)
                println("encodeWidth, encodeHeight : $encodeWidth, $encodeHeight")
                MirroringService.start(this@MainActivity, encodeWidth, encodeHeight)
        }

        // 権限のチェック
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
        {
            // 許可しない→今後表示しないを選択した場合の処理も本当は必要
            // ActivityCompat.shouldShowRequestPermissionRationale

            util.Print("Unregisterd")
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                MEDIA_STORAGE_ACCESS)

        }

        // カメラへのアクセス権限のチェック
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            // 権限がない場合はリクエスト
            requestPermissions( arrayOf(Manifest.permission.CAMERA), MEDIA_CAMERA_ACCESS)
        }

        // ローカルブロードキャスト登録
        val mBroadcastReceiver = LocalBroadcastManager.getInstance(this)
        val filter = IntentFilter()
        filter.addAction(BLUETOOTH_CONNECTED)
        filter.addAction(DISPLAY_UPDATE)
        mBroadcastReceiver.registerReceiver( mReceiver, filter)
    }

    override fun onStart() {
        super.onStart()
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter!!.isEnabled()) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat()
        }
    }
    override fun onResume() {
        super.onResume()

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            val state = mChatService?.stateSession
            if (state == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService?.start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        MirroringService.stop(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CONNECT_DEVICE_SECURE -> {
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data!!, true)
                }
            }
        }
    }


    // TopMenuView
    // Bluetoothのアイコンメニュー作成
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bluetooth_icon, menu)
        return true
    }

    // 上部メニューのクリックイベント
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // whenの最後の式がReturnされる
        return when(item.itemId) {
            R.id.bluetoothIcon ->{
                val context = this
                util.Print(context.toString())
                // BluetoothFragmentの処理追加する
                val serverIntent = Intent(this, DeviceListActivity::class.java)
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE)
                true
            }
            else ->{
                super.onOptionsItemSelected(item)
            }
        }
    }

    // OSに関連するところ
    // 権限許可ダイアログのコールバック関数
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode)
        {
            MEDIA_CAMERA_ACCESS->{
                //DoNothing
            }

        }
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when (action) {
                BLUETOOTH_CONNECTED->
                {
                    util.Print("BLUETOOTH_CONNECTED")
                    isBtConnect = true
                    // 画面キャプチャ
                    // 権限のチェックとか、起動時(キャプチャ開始時の御作法)
//                    mMediaProjectionManager =  getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//                    // ここでミラーリングしてもいい？のダイアログが出る
//                    startActivityForResult(mMediaProjectionManager?.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
                }
                DISPLAY_UPDATE ->{
//                    println("DISPLAY_UPDATE")
                    val datas = intent.getByteArrayExtra("IMAGE")
                    mChatService?.write(datas)

                }

                else->
                {
                    util.Print("Else" + action!!)
                }
            }
        }
    }



    // general Function
    /**
     * Establish connection with other device
     *
     * @param data   An [Intent] with [DeviceListActivity.EXTRA_DEVICE_ADDRESS] extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private fun connectDevice(data: Intent, secure: Boolean) {
        // Get the device MAC address
        val address = data.extras!!
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS)
        // Get the BluetoothDevice object
        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        // Attempt to connect to the device
        // なんやかんやでActivityResult使って接続先のMacAddressが帰ってくるので接続する
        mChatService?.connect(device, secure)
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private fun setupChat() {
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = BluetoothChatService(this, mHandler)

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = StringBuffer("")
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private val mHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            val activity = this
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    BluetoothChatService.STATE_CONNECTED -> {
                        setStatus(getString(R.string.title_connected_to, mConnectedDeviceName))
                        mConversationArrayAdapter?.clear()
                    }
                    BluetoothChatService.STATE_CONNECTING -> setStatus(R.string.title_connecting)
                    BluetoothChatService.STATE_LISTEN, BluetoothChatService.STATE_NONE -> setStatus(R.string.title_not_connected)
                }
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray

                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)

                    val datas:List<String> = readMessage.split(":")
                    util.Print(datas[0].length.toString())
                    when(datas[0]) {
                        // ex)"TouchEvent:11.1,22.2"
                        "TouchEvent"->
                        {
                            util.Print("TouchEvent")
                            println(datas[1])
                            Thread{
                                val pos = datas[1].split(",")
                                val x:Float = pos[0].toFloat()
                                val y:Float = pos[1].toFloat()
                                sendMotionEvent(x, y)
                            }.start()
                            util.Print("TouchEvent Done")

                        }
                        else->{
                        }
                    }
//                    mConversationArrayAdapter!!.add("$mConnectedDeviceName:  ${readMessage}")
                }
                MESSAGE_WRITE -> {
                    println("MESSAGE_WRITE")
                    mChatService?.write( msg.obj as ByteArray)
                }

                MESSAGE_DEVICE_NAME -> {
                    // save the connected device's name
                    mConnectedDeviceName = msg.data.getString(DEVICE_NAME)
                    if (null != activity) {
                        util.Print("Connected to ${mConnectedDeviceName}")
//                        Toast.makeText(activity, "Connected to ${mConnectedDeviceName}", Toast.LENGTH_SHORT).show()
                    }
                }
                MESSAGE_TOAST -> if (null != activity) {
                    util.Print(msg.data.getString(TOAST)!!)
//                    Toast.makeText(
//                        activity, msg.data.getString(TOAST),
//                        Toast.LENGTH_SHORT
//                    ).show()
                }
            }
        }
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private fun setStatus(resId: Int) {
        val textView = this.findViewById<TextView>(R.id.connectState)
        util.Print(resId.toString())
        util.Print(getString(resId))
        textView.text = getString(resId)
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private fun setStatus(subTitle: CharSequence) {
        val textView = this.findViewById<TextView>(R.id.connectState)
        util.Print(subTitle.toString())
        textView.text = subTitle
    }

    /**
     * send motionEvent to the device.
     */
    fun sendMotionEvent(x:Float, y:Float) {
        // 現在時刻から10ms押下、10Delay、10ms押上
        val downStartTime = SystemClock.uptimeMillis()
        val downDoneTime = downStartTime + 10
        val upStartTime = downDoneTime + 10
        val upDoneTime = upStartTime + 10

        // タッチイベント座標
        val coordinateX = x * scaleToSurfaceX.toFloat()
        val coordinateY = y * scaleToSurfaceY.toFloat()

        println("Touch : $x,$y")
        println("Scale : $scaleToSurfaceX,$scaleToSurfaceX")
        println("Coordinate : $coordinateX,$coordinateY")

        val event1 = MotionEvent.obtain(
            downStartTime, downDoneTime, MotionEvent.ACTION_DOWN,
            coordinateX, coordinateY, 0
        )
        event1.source = InputDevice.SOURCE_TOUCHSCREEN

        val event2 = MotionEvent.obtain(
            upStartTime, upDoneTime, MotionEvent.ACTION_UP,
            coordinateX, coordinateY, 0
        )
        event2.source = InputDevice.SOURCE_TOUCHSCREEN

        try {
            // これはダイアログ？画面？に対してタッチできないが仕方なく使う
            val mInputManager = (getSystemService(Context.INPUT_SERVICE)) as InputManager
            mInputManager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Integer.TYPE).run{
                this.invoke(mInputManager, event1, 0)
                this.invoke(mInputManager, event2, 0)
            }
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        }
    }
}

