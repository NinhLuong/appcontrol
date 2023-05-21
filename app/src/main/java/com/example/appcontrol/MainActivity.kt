package com.example.appcontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.example.appcontrol.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.app.NotificationCompat
// Add this at the top of the file
import com.google.firebase.database.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.getValue


private lateinit var binding: ActivityMainBinding

class PowerStatusService : Service() {

    private lateinit var powerRef: DatabaseReference
    private lateinit var notificationManager: NotificationManager
    private val notificationChannelId = "PowerStatusChannel"
    private val powerStatusNotificationId = 1

    override fun onCreate() {
        super.onCreate()
        powerRef = FirebaseDatabase.getInstance().getReference("/Status/Power")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create a notification channel for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Power Status Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Channel for power status notifications"
                enableLights(true)
                lightColor = Color.RED
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        powerRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get the data from the snapshot
                val powerStatus = snapshot.getValue<String>()
                Log.d("value_powerStatus", "Value is: $powerStatus")

                // Update the UI based on the data
                when (powerStatus) {

                    "true" -> {
                        // Update the connected UI
                        binding.iconActiveDevices.setImageResource(R.drawable.woking_device)
                        binding.textActiveDevices.text = "Active Devices"
                        // Clear any existing notification
                        notificationManager.cancel(powerStatusNotificationId)
                    }
                    "false" -> {
                        // Update the disconnected UI
                        binding.iconActiveDevices.setImageResource(R.drawable.error)
                        binding.textActiveDevices.text = "Warning Devices"

                        // Create a notification
                        val notificationBuilder = NotificationCompat.Builder(applicationContext, notificationChannelId)
                            .setContentTitle("Power Status")
                            .setContentText("Power status is disconnected")
                            .setSmallIcon(R.drawable.error)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(getPendingIntent())

                        // Show the notification
                        notificationManager.notify(powerStatusNotificationId, notificationBuilder.build())
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })

        // Return START_STICKY to ensure the service keeps running even if the app is closed
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var startTime:String = "22:0"
        var endTime:String = "7:0"
        // Remove the title bar
        supportActionBar?.hide()
//        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = FirebaseDatabase.getInstance()

        binding.btnTimeOn.setOnClickListener {
            val currentTime = Calendar.getInstance()
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)
//            startTime ="$hour:$minute"

            val timePickerDialog = TimePickerDialog(this, TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                // Handle the selected time here
                // hourOfDay and minute are the selected time values
                // Format the time values with leading zeros if needed
                val formattedHour = String.format("%02d", hourOfDay)
                val formattedMinute = String.format("%02d", minute)

                 startTime = "$formattedHour:$formattedMinute"
                binding.btnTimeOn.text = "$formattedHour:$formattedMinute"

                // Save the selected time in SharedPreferences
                val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("selectedTimeOn","$formattedHour:$formattedMinute")
                editor.apply()
                val TimeOn = database.getReference("/Time/TimeOn")
                TimeOn.setValue(startTime)
                // Calculate the time interval
                calculateTimeInterval(startTime, endTime)
                // You can use the selectedTime for further processing or display it in your UI
            }, hour, minute, false)

            timePickerDialog.show()
        }

        binding.btnTimeOff.setOnClickListener {

            val currentTime = Calendar.getInstance()
            val hour = currentTime.get(Calendar.HOUR_OF_DAY)
            val minute = currentTime.get(Calendar.MINUTE)

//            endTime ="$hour:$minute"

            val timePickerDialog = TimePickerDialog(this, TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                // Handle the selected time here
                // hourOfDay and minute are the selected time values
                // Format the time values with leading zeros if needed
                val formattedHour = String.format("%02d", hourOfDay)
                val formattedMinute = String.format("%02d", minute)

                binding.btnTimeOff.text = "$formattedHour:$formattedMinute"

                 endTime = "$formattedHour:$formattedMinute"

                // Save the selected time in SharedPreferences
                val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putString("selectedTimeOff", "$formattedHour:$formattedMinute")

                editor.apply()

                val TimeOff = database.getReference("/Time/TimeOff")
                TimeOff.setValue(endTime)
                // You can use the selectedTime for further processing or display it in your UI
                // Calculate the time interval
                calculateTimeInterval(startTime, endTime)
            }, hour, minute, false)

            timePickerDialog.show()
        }

        val autoRef = database.getReference("/Status/Auto")
        val ledRef = database.getReference("/Status/Led")
        binding.switchAuto.setOnCheckedChangeListener { _, isChecked ->
            // Save the state of the Switch in SharedPreferences
            val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean("switchState", isChecked)
            editor.apply()
            if (isChecked) {
                // Switch is checked, show contentAuto and hide contentCustomer
                binding.contentCustome.visibility = View.GONE
                binding.contentAuto.visibility = View.VISIBLE

                // Turn off switch4
//                binding.switch4.isChecked = false

                // Update the dataset in Firebase
                ledRef.setValue("false")
                autoRef.setValue("true")
            }
            else {
                // Switch is unchecked, show constraintLayout and hide linearLayout
                binding.contentCustome.visibility = View.VISIBLE
                binding.contentAuto.visibility = View.GONE

                autoRef.setValue("false")
            }
        }


        binding.switchLed.setOnCheckedChangeListener { _, isChecked ->
            // Save the state of the Switch in SharedPreferences
            val sharedPreferences = getSharedPreferences("ledstatus", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean("switchLedState", isChecked)
            editor.apply()
            if (isChecked) {
                ledRef.setValue("true")
            } else {
                ledRef.setValue("false")
            }
        }


        ledRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val switchState = snapshot.getValue<String>()
                val isChecked = switchState == "true"
                binding.switchLed.isChecked = isChecked
                val sharedPreferences = getSharedPreferences("ledstatus", Context.MODE_PRIVATE)
                val editor = sharedPreferences.edit()
                editor.putBoolean("switchLedState", isChecked)
                editor.apply()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })

        // Retrieve the state of the Switch3 from SharedPreferences
        val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val switchState = sharedPreferences.getBoolean("switchState", false) // false is the default value
        binding.switchAuto.isChecked = switchState

// Retrieve the state of Switch4 from SharedPreferences
        val sharedPreferencesLed = getSharedPreferences("ledstatus", Context.MODE_PRIVATE)
        val switchLedState = sharedPreferencesLed.getBoolean("switchLedState", false) // false is the default value
        binding.switchLed.isChecked = switchLedState

        // Initialize the Firebase database reference
         val databasechild = FirebaseDatabase.getInstance().reference

        val edtActiveTime = findViewById<EditText>(R.id.edtActiveTime)

        // Retrieve the saved value from Firebase and set it to the EditText
        databasechild.child("/Time/TimeActive").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeTime = snapshot.getValue(String::class.java)
                edtActiveTime.setText(activeTime)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle any error during data retrieval
            }
        })

        edtActiveTime.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val input = edtActiveTime.text.toString()
                if (input.matches(Regex("^([01]\\d|2[0-3]):([0-5]\\d)$"))) {
                    // Save the entered data to Firebase
                    databasechild.child("/Time/TimeActive").setValue(input)

                    // Hide the keyboard and clear focus from the EditText
                    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(edtActiveTime.windowToken, 0)
                    edtActiveTime.clearFocus()
                }
                true
            } else {
                false
            }
        }



// Add a listener to listen for data changes
        // Create a reference to your Firebase Realtime Database

        val loraRef = database.getReference("/Status/Lora")


// Add a listener to listen for data changes
        loraRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get the data from the snapshot
                val loraStatus = snapshot.getValue<String>()
                Log.d("value_loraStatus", "Value is: $loraStatus")

                // Update the UI based on the data
                when(loraStatus){
                    "completed" -> {
                        // Update the connected UI
                        binding.icConnectedDevices.setImageResource(R.drawable.connect)
                        binding.txtConnectedDevices.text = "Connected Devices"
                    }

                    "failed" -> {
                        // Update the disconnected UI
                        binding.icConnectedDevices.setImageResource(R.drawable.disconnect)
                        binding.txtConnectedDevices.text = "Disonnected Devices"
                    }

                    "loading" -> {
                        // Update the disconnected UI
                        binding.icConnectedDevices.setImageResource(R.drawable.loading)
                        binding.txtConnectedDevices.text = "Loading Devices"
                    }


                }

            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })

        val serviceIntent = Intent(this, PowerStatusService::class.java)
        startService(serviceIntent)
        /*
                val powerRef = database.getReference("/Status/Power")

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notificationChannelId = "PowerStatusChannel"
                val powerStatusNotificationId = 1

        // Create a notification channel for Android Oreo and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        notificationChannelId,
                        "Power Status Channel",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Channel for power status notifications"
                        enableLights(true)
                        lightColor = Color.RED
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                powerRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        // Get the data from the snapshot
                        val powerStatus = snapshot.getValue<String>()
                        Log.d("value_powerStatus", "Value is: $powerStatus")

                        // Update the UI based on the data
                        when (powerStatus) {
                            "true" -> {
                                // Update the connected UI
                                binding.iconActiveDevices.setImageResource(R.drawable.woking_device)
                                binding.textActiveDevices.text = "Active Devices"

                                // Clear any existing notification
                                notificationManager.cancel(powerStatusNotificationId)
                            }

                            "false" -> {
                                // Update the disconnected UI
                                binding.iconActiveDevices.setImageResource(R.drawable.error)
                                binding.textActiveDevices.text = "Warning Devices"

                                // Create a notification
                                val notificationBuilder = NotificationCompat.Builder(this@MainActivity, notificationChannelId)
                                    .setContentTitle("Power Status")
                                    .setContentText("Power status is disconnected")
                                    .setSmallIcon(R.drawable.error)
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                                // Show the notification
                                notificationManager.notify(powerStatusNotificationId, notificationBuilder.build())
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Handle error
                    }
                })


        // Add a listener to listen for data changes
                powerRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        // Get the data from the snapshot
                        val powerStatus = snapshot.getValue<String>()
                        Log.d("value_powerStatus", "Value is: $powerStatus")

                        // Update the UI based on the data
                        when(powerStatus){
                            "true" -> {
                                // Update the connected UI
                                binding.iconActiveDevices.setImageResource(R.drawable.woking_device)
                                binding.textActiveDevices.text = "Active Devices"
                            }

                            "false" -> {
                                // Update the disconnected UI
                                binding.iconActiveDevices.setImageResource(R.drawable.error)
                                binding.textActiveDevices.text = "Warning Devices"
                            }


                        }

                    }

                    override fun onCancelled(error: DatabaseError) {
                        // Handle error
                    }
                })
        */

        val rainRef = database.getReference("/Status/Rain")

// Add a listener to listen for data changes
        rainRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get the data from the snapshot
                val rainStatus = snapshot.getValue<String>()
                Log.d("value_rainStatus", "Value is: $rainStatus")

                // Update the UI based on the data
                when(rainStatus){
                    "true" -> {
                        // Update the connected UI
                        binding.iconRainyNight.setImageResource(R.drawable.rainy_night)
                        binding.textRainyNight.text = "Rainy           Night"
                    }

                    "false" -> {
                        // Update the disconnected UI
                        binding.iconRainyNight.setImageResource(R.drawable.night)
                        binding.textRainyNight.text = "Clouse           Night"
                    }

                }

            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })



    }
    // Function to calculate time interval between two times
    fun calculateTimeInterval(startTime: String, endTime: String) {
        val format = SimpleDateFormat("HH:mm")
        val calendar = Calendar.getInstance()

        // Parse the start time and set it to the calendar
        calendar.time = format.parse(startTime)
        val startTimeInMillis = calendar.timeInMillis

        // Parse the end time and set it to the calendar
        calendar.time = format.parse(endTime)
        val endTimeInMillis = calendar.timeInMillis

        // Calculate the time interval
        val interval = if (endTimeInMillis >= startTimeInMillis) {
            endTimeInMillis - startTimeInMillis
        } else {
            // Add 24 hours to the end time and calculate the interval
            val adjustedEndTimeInMillis = endTimeInMillis + (24 * 60 * 60 * 1000)
            adjustedEndTimeInMillis - startTimeInMillis
        }

        // Convert the interval to hours and minutes
        val hours = interval / (1000 * 60 * 60)
        val minutes = (interval % (1000 * 60 * 60)) / (1000 * 60)

        // Format the interval as HH:mm
        val formattedInterval = String.format("%02d:%02d", hours, minutes)
        val database = FirebaseDatabase.getInstance()
        val timeActive = database.getReference("/Time/TimeActive")
        timeActive.setValue(formattedInterval)

        // Save the selected time in SharedPreferences
        val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("timeActive", formattedInterval)

        editor.apply()
        // Update the UI with the calculated interval
        binding.edtHour.setText(formattedInterval)
    }

    override fun onResume() {
        super.onResume()

        // Retrieve the saved time values from SharedPreferences
        val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val selectedTimeOn = sharedPreferences.getString("selectedTimeOn", "")
        val selectedTimeOff = sharedPreferences.getString("selectedTimeOff", "")
        val rstimeActive = sharedPreferences.getString("timeActive", "")

        // Display the retrieved time values in your UI
        if (!selectedTimeOn.isNullOrEmpty() && !selectedTimeOff.isNullOrEmpty()) {
            // Update your UI with the retrieved time values, e.g., setText() on TextViews
            // Note: This is just an example, you may need to update your UI based on your app's UI components

            binding.btnTimeOn.text = "$selectedTimeOn"
            binding.btnTimeOff.text = "$selectedTimeOff"
            binding.edtHour.setText(rstimeActive)
        }
    }

}