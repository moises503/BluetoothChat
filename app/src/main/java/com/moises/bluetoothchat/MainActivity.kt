package com.moises.bluetoothchat

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5

        const val DEVICE_NAME = "device_name"
        const val TOAST = "toast"
    }

    private val REQUEST_CONNECT_DEVICE_SECURE = 1
    private val REQUEST_CONNECT_DEVICE_INSECURE = 2
    private val REQUEST_ENABLE_BT = 3

    private var lvMainChat: ListView? = null
    private var etMain: EditText? = null
    private var btnSend: Button? = null

    private var connectedDeviceName: String? = null
    private var chatArrayAdapter: ArrayAdapter<String>? = null

    private var outStringBuffer: StringBuffer? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var chatService: ChatService? = null

    private val handler = Handler(Handler.Callback { msg ->
        when (msg.what) {
            MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                ChatService.STATE_CONNECTED -> {
                    setStatus(getString(R.string.title_connected_to,
                            connectedDeviceName))
                    chatArrayAdapter!!.clear()
                }
                ChatService.STATE_CONNECTING -> setStatus(R.string.title_connecting)
                ChatService.STATE_LISTEN, ChatService.STATE_NONE -> setStatus(R.string.title_not_connected)
            }
            MESSAGE_WRITE -> {
                val writeBuf = msg.obj as ByteArray
                val writeMessage = String(writeBuf)
                chatArrayAdapter!!.add("Me:  $writeMessage")
            }
            MESSAGE_READ -> {
                val readBuf = msg.obj as ByteArray
                val readMessage = String(readBuf, 0, msg.arg1)
                chatArrayAdapter!!.add("$connectedDeviceName:  $readMessage")
            }
            MESSAGE_DEVICE_NAME -> {
                connectedDeviceName = msg.data.getString(DEVICE_NAME)
                Toast.makeText(applicationContext,
                        "Connected to $connectedDeviceName",
                        Toast.LENGTH_SHORT).show()
            }
            MESSAGE_TOAST -> Toast.makeText(applicationContext,
                    msg.data.getString(TOAST), Toast.LENGTH_SHORT)
                    .show()
        }
        false
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        getWidgetReferences()
        bindEventHandler()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available",
                    Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }

    private fun getWidgetReferences() {
        lvMainChat = findViewById(R.id.lvMainChat) as ListView
        etMain = findViewById(R.id.etMain) as EditText
        btnSend = findViewById(R.id.btnSend) as Button
    }

    private fun bindEventHandler() {
        etMain!!.setOnEditorActionListener(mWriteListener)
        btnSend!!.setOnClickListener {
            val message = etMain!!.text.toString()
            sendMessage(message)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CONNECT_DEVICE_SECURE -> if (resultCode == Activity.RESULT_OK) {
                connectDevice(data!!, true)
            }
            REQUEST_CONNECT_DEVICE_INSECURE -> if (resultCode == Activity.RESULT_OK) {
                connectDevice(data!!, false)
            }
            REQUEST_ENABLE_BT -> if (resultCode == Activity.RESULT_OK) {
                setupChat()
            } else {
                Toast.makeText(this, R.string.bt_not_enabled_leaving,
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun connectDevice(data: Intent, secure: Boolean) {
        val address = data.extras!!.getString(
                DeviceListActivity.DEVICE_ADDRESS)
        val device = bluetoothAdapter!!.getRemoteDevice(address)
        chatService!!.connect(device, secure)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.option_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var serverIntent: Intent? = null
        when (item.itemId) {
            R.id.secure_connect_scan -> {
                serverIntent = Intent(this, DeviceListActivity::class.java)
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE)
                return true
            }
            R.id.insecure_connect_scan -> {
                serverIntent = Intent(this, DeviceListActivity::class.java)
                startActivityForResult(serverIntent,
                        REQUEST_CONNECT_DEVICE_INSECURE)
                return true
            }
            R.id.discoverable -> {
                ensureDiscoverable()
                return true
            }
        }
        return false
    }

    private fun ensureDiscoverable() {
        if (bluetoothAdapter!!.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            val discoverableIntent = Intent(
                    BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(
                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            startActivity(discoverableIntent)
        }
    }

    private fun sendMessage(message: String) {
        if (chatService!!.getState() !== ChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
                    .show()
            return
        }
        if (message.length > 0) {
            val send = message.toByteArray()
            chatService!!.write(send)
            outStringBuffer!!.setLength(0)
            etMain!!.setText(outStringBuffer)
        }
    }

    private val mWriteListener = OnEditorActionListener { view, actionId, event ->
        if (actionId == EditorInfo.IME_NULL
                && event.action == KeyEvent.ACTION_UP) {
            val message = view.text.toString()
            sendMessage(message)
        }
        true
    }

    private fun setStatus(resId: Int) {
        val actionBar: ActionBar? = supportActionBar
        actionBar?.setSubtitle(resId)
    }

    private fun setStatus(subTitle: CharSequence) {
        val actionBar: ActionBar? = supportActionBar
        actionBar?.setSubtitle(subTitle)
    }

    private fun setupChat() {
        chatArrayAdapter = ArrayAdapter(this, R.layout.message)
        lvMainChat!!.adapter = chatArrayAdapter
        chatService = ChatService(this, handler)
        outStringBuffer = StringBuffer("")
    }

    override fun onStart() {
        super.onStart()
        if (!bluetoothAdapter!!.isEnabled) {
            val enableIntent = Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        } else {
            if (chatService == null) setupChat()
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        if (chatService != null) {
            if (chatService!!.getState() === ChatService.STATE_NONE) {
                chatService!!.start()
            }
        }
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (chatService != null) chatService!!.stop()
    }

}
