package com.moises.bluetoothchat

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DeviceListActivity : AppCompatActivity() {

    private var tvDeviceListPairedDeviceTitle: TextView? =
        null
    private var tvDeviceListNewDeviceTitle:TextView? = null
    private var lvDeviceListPairedDevice: ListView? =
        null
    private  var lvDeviceListNewDevice:android.widget.ListView? = null

    private var btnDeviceListScan: Button? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var pairedDevicesArrayAdapter: ArrayAdapter<String>? = null
    private var newDevicesArrayAdapter: ArrayAdapter<String>? = null

    companion object {
        const val DEVICE_ADDRESS = "deviceAddress"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
        setContentView(R.layout.activity_device_list)
        setResult(Activity.RESULT_CANCELED)
        getWidgetReferences()
        bindEventHandler()
        initializeValues()
    }

    private fun getWidgetReferences() {
        tvDeviceListPairedDeviceTitle =
            findViewById<View>(R.id.tvDeviceListPairedDeviceTitle) as TextView
        tvDeviceListNewDeviceTitle =
            findViewById<View>(R.id.tvDeviceListNewDeviceTitle) as TextView
        lvDeviceListPairedDevice =
            findViewById<View>(R.id.lvDeviceListPairedDevice) as ListView
        lvDeviceListNewDevice =
            findViewById<View>(R.id.lvDeviceListNewDevice) as ListView
        btnDeviceListScan =
            findViewById<View>(R.id.btnDeviceListScan) as Button
    }

    private fun bindEventHandler() {
        lvDeviceListPairedDevice!!.onItemClickListener = mDeviceClickListener
        lvDeviceListNewDevice?.setOnItemClickListener(mDeviceClickListener)
        btnDeviceListScan!!.setOnClickListener {
            startDiscovery()
            btnDeviceListScan!!.visibility = View.GONE
        }
    }

    private fun initializeValues() {
        pairedDevicesArrayAdapter = ArrayAdapter(
            this,
            R.layout.device_name
        )
        newDevicesArrayAdapter = ArrayAdapter(
            this,
            R.layout.device_name
        )
        lvDeviceListPairedDevice!!.adapter = pairedDevicesArrayAdapter
        lvDeviceListNewDevice?.setAdapter(newDevicesArrayAdapter)
        // Register for broadcasts when a device is discovered
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(discoveryFinishReceiver, filter)
        // Register for broadcasts when discovery has finished
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoveryFinishReceiver, filter)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = bluetoothAdapter?.bondedDevices
        // If there are paired devices, add each one to the ArrayAdapter
        pairedDevices?.let {
            if (it.isNotEmpty()) {
                tvDeviceListPairedDeviceTitle!!.visibility = View.VISIBLE
                it.forEach { device ->
                    pairedDevicesArrayAdapter!!.add(
                        device.name + "\n"
                                + device.address
                    )
                }
            } else {
                val noDevices = resources.getText(R.string.none_paired)
                    .toString()
                pairedDevicesArrayAdapter!!.add(noDevices)
            }
        }
    }

    private fun startDiscovery() {
        setProgressBarIndeterminateVisibility(true)
        setTitle(R.string.scanning)
        tvDeviceListNewDeviceTitle?.setVisibility(View.VISIBLE)
        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()
    }

    private val mDeviceClickListener =
        OnItemClickListener { av, v, arg2, arg3 ->
            bluetoothAdapter!!.cancelDiscovery()
            val info = (v as TextView).text.toString()
            val address = info.substring(info.length - 17)
            val intent = Intent()
            intent.putExtra(DEVICE_ADDRESS, address)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

    private val discoveryFinishReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent
                    .getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    newDevicesArrayAdapter!!.add(
                        device.name + "\n"
                                + device.address
                    )
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                == action
            ) {
                setProgressBarIndeterminateVisibility(false)
                setTitle(R.string.select_device)
                if (newDevicesArrayAdapter!!.count == 0) {
                    val noDevices = resources.getText(
                        R.string.none_found
                    ).toString()
                    newDevicesArrayAdapter!!.add(noDevices)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bluetoothAdapter != null) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        unregisterReceiver(discoveryFinishReceiver)
    }
}
