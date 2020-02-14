/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mirroringsample.encode

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.Log

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
class BluetoothChatService

/**
 * Constructor. Prepares a new BluetoothChat session.
 *
 * @param context The UI Activity Context
 * @param handler A Handler to send messages back to the UI Activity
 */
    (context: Context, private val mHandler: Handler) {

    // Member fields
    private val mAdapter: BluetoothAdapter
    private var mSecureAcceptThread: AcceptThread? = null
    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

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
     * Return the current connection state.
     */
//    @get:Synchronized
    var stateSession: Int = 0

//        private set
    private var mNewState: Int = 0

    var test = 0


    init {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
        stateSession = STATE_NONE
        mNewState = stateSession
    }

    /**
     * Update UI title according to the current state of the chat connection
     */
    @Synchronized
    private fun updateUserInterfaceTitle() {
//        state = state
        Log.d(TAG, "updateUserInterfaceTitle() $mNewState -> $stateSession")
        mNewState = stateSession

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget()
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
    fun start() {
        Log.d(TAG, "start")

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = AcceptThread(true)
            Log.d("AASSAA", "mSecureAcceptThread Start!")
            mSecureAcceptThread!!.start()
        }
//        if (mInsecureAcceptThread == null) {
//            mInsecureAcceptThread = AcceptThread(false)
//            Log.d("AASSAA", "mInSecureAcceptThread Start!")
//            mInsecureAcceptThread!!.start()
//        }
        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    @Synchronized
    fun connect(device: BluetoothDevice, secure: Boolean) {
        Log.d(TAG, "connect to: $device")

        // Cancel any thread attempting to make a connection
        if (stateSession == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device, secure)
        mConnectThread!!.start()
        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice, socketType: String) {
        Log.d(TAG, "connected, Socket Type:$socketType")

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            Log.d("AASSAA", "mSecureAcceptThread Cancel")
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }
//        if (mInsecureAcceptThread != null) {
//            Log.d("AASSAA", "mSecureAcceptThread Cancel")
//            mInsecureAcceptThread!!.cancel()
//            mInsecureAcceptThread = null
//        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket, socketType)
        mConnectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(DEVICE_NAME, device.name)
        msg.data = bundle
        mHandler.sendMessage(msg)
        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        Log.d(TAG, "stop")

        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }
        stateSession = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray) {
        // Create temporary object
        val r: ConnectedThread?
        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (stateSession != STATE_CONNECTED) return
            r = mConnectedThread
        }
        // Perform the write unsynchronized
        r!!.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed() {
        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Unable to connect device")
        msg.data = bundle
        mHandler.sendMessage(msg)

        stateSession = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()

        // Start the service over to restart listening mode
        this@BluetoothChatService.start()
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Device connection was lost")
        msg.data = bundle
        mHandler.sendMessage(msg)

        stateSession = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()

        // Start the service over to restart listening mode
        this@BluetoothChatService.start()
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private inner class AcceptThread(secure: Boolean) : Thread() {
        // The local server socket
        private val mmServerSocket: BluetoothServerSocket?
        private val mSocketType: String

        init {
            var tmp: BluetoothServerSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(
                        NAME_SECURE,
                        MY_UUID_SECURE
                    )
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                        NAME_INSECURE, MY_UUID_INSECURE
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e)
            }

            mmServerSocket = tmp
            stateSession = STATE_LISTEN
            Log.d("AASSAA", "AcceptThread init")
        }

        override fun run() {
            Log.d(
                TAG, "Socket Type: " + mSocketType +
                        "BEGIN mAcceptThread" + this
            )
            Log.d("AASSAA", "AcceptThread Run")
            name = "AcceptThread$mSocketType"

            var socket: BluetoothSocket? = null

            // Listen to the server socket if we're not connected
            while (stateSession != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket!!.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e)
                    break
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized(this@BluetoothChatService) {
                        when (stateSession) {
                            STATE_LISTEN, STATE_CONNECTING ->
                                // Situation normal. Start the connected thread.
                                connected(
                                    socket, socket.remoteDevice,
                                    mSocketType
                                )
                            STATE_NONE, STATE_CONNECTED ->
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }

                            else ->
                            {
                                // DoNothing
                            }

                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: $mSocketType")

        }

        fun cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this)
            try {
                Log.d("AASSAA", "Socket Close")
                mmServerSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e)
            }

        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(private val mmDevice: BluetoothDevice, secure: Boolean) : Thread() {
        private val mmSocket: BluetoothSocket?
        private val mSocketType: String

        init {
            var tmp: BluetoothSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = mmDevice.createRfcommSocketToServiceRecord(
                        MY_UUID_SECURE
                    )
                } else {
                    tmp = mmDevice.createInsecureRfcommSocketToServiceRecord(
                        MY_UUID_INSECURE
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e)
            }

            mmSocket = tmp
            stateSession = STATE_CONNECTING
        }

        override fun run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:$mSocketType")
            name = "ConnectThread$mSocketType"

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket!!.connect()
            } catch (e: IOException) {
                // Close the socket
                try {
                    mmSocket!!.close()
                } catch (e2: IOException) {
                    Log.d(
                        TAG, "unable to close() " + mSocketType +
                                " socket during connection failure", e2
                    )
                }

                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothChatService) {
                mConnectThread = null
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType)
        }

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect $mSocketType socket failed", e)
            }

        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket, socketType: String) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            Log.d(TAG, "create ConnectedThread: $socketType")
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmSocket.inputStream
                tmpIn.mark(4096)
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
            }

            mmInStream = tmpIn
            mmOutStream = tmpOut
            stateSession = STATE_CONNECTED
        }

        override fun run() {
            val buffer = ByteArray(4096)
            var bytes: Int
            var lastTime = System.currentTimeMillis()

            // Keep listening to the InputStream while connected
            while (stateSession == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    if(mmInStream!!.available() != 0)
                    {
                        bytes = mmInStream!!.read(buffer, 0, 4096)
//                        Log.d("AASSAA", "ReadSize : " + bytes.toString() + ", Bin : " + buffer[0].toString())
                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget()
                    }
                    else
                    {
                        var currentTime = System.currentTimeMillis()
//                        if(currentTime > (lastTime + 1000))
//                        {
//                            mHandler.obtainMessage(MESSAGE_WRITE, 5, -1, "aaaaa")
//                                .sendToTarget()
//                            lastTime = currentTime
//                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }

            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        fun write(buffer: ByteArray) {
            try {
                mmOutStream?.write(buffer)
                mmOutStream?.flush()

                // Share the sent message back to the UI Activity
//                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
//                    .sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }

        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }

        }
    }

    fun chkState():String{
        return when(stateSession){
            STATE_NONE ->{
                "STATE_NONE"
            }
            STATE_LISTEN ->{
                "STATE_LISTEN"
            }
            STATE_CONNECTING ->{
                "STATE_CONNECTING"
            }
            STATE_CONNECTED ->{
                "STATE_CONNECTED"
            }
            else->{
                return "Null"
            }
        }

    }

    companion object {
        // Debugging
        private val TAG = "BluetoothChatService"

        // Name for the SDP record when creating server socket
        private val NAME_SECURE = "BluetoothChatSecure"
        private val NAME_INSECURE = "BluetoothChatInsecure"

        // Unique UUID for this application
        private val MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        private val MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

        // Constants that indicate the current connection state
        val STATE_NONE = 0       // we're doing nothing
        val STATE_LISTEN = 1     // now listening for incoming connections
        val STATE_CONNECTING = 2 // now initiating an outgoing connection
        val STATE_CONNECTED = 3  // now connected to a remote device
    }
}
