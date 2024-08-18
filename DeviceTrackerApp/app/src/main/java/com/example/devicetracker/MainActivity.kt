package com.example.devicetracker

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private var locationPermissionGranted: Boolean = false

    // Write a message to the database
    val database = Firebase.database
    val myRef = database.getReference("message")

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val latitude = intent.getDoubleExtra("latitude", 0.0)
            val longitude = intent.getDoubleExtra("longitude", 0.0)
            findViewById<TextView>(R.id.locationTextView).text = "Lat: $latitude, Lng: $longitude"

            // Upload location data to Firebase every time a new location is received
            uploadDataToDatabase(latitude, longitude)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            locationPermissionGranted = isGranted
            if (!isGranted) {
                Toast.makeText(this, "Location permission not granted", Toast.LENGTH_LONG).show()
            } else {
                startLocationService()
            }
        }

        val nameEditText = findViewById<EditText>(R.id.nameEditText)
        val submitButton = findViewById<Button>(R.id.submitButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val name = getStoredName()

        if (name != null) {
            nameEditText.setText(name)
            nameEditText.isEnabled = false
            submitButton.isEnabled = false
            if (isGooglePlayServicesAvailable()) {
                requestLocationPermission()
            }
        }

        submitButton.setOnClickListener {
            val enteredName = nameEditText.text.toString()
            if (enteredName.isNotBlank()) {
                saveName(enteredName)
                nameEditText.isEnabled = false
                submitButton.isEnabled = false
                if (isGooglePlayServicesAvailable()) {
                    requestLocationPermission()
                }
            } else {
                Toast.makeText(this, "Please enter a valid name", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            stopLocationService()
            finish()
        }
    }

    private fun uploadDataToDatabase(latitude: Double, longitude: Double) {
        // Modify this to use the stored name or another identifier as necessary
        val name = getStoredName() ?: "Unknown"
        myRef.setValue(
            hashMapOf(
                "Name" to name,
                "lat" to latitude,
                "lng" to longitude
            )
        )
    }

    private fun startLocationService() {
        if (locationPermissionGranted) {
            val serviceIntent = Intent(this, LocationService::class.java)
            startService(serviceIntent)
        } else {
            requestLocationPermission()
        }
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        stopService(serviceIntent)
    }

    private fun requestLocationPermission() {
        when {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                locationPermissionGranted = true
                startLocationService()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val status = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (status != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(this, status, 2404)?.show()
            } else {
                Toast.makeText(this, "Google Play Services not available.", Toast.LENGTH_LONG).show()
                finish()
            }
            return false
        }
        return true
    }

    private fun getStoredName(): String? {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        return sharedPref.getString("userName", null)
    }

    private fun saveName(name: String) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putString("userName", name)
            apply()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(locationReceiver, IntentFilter("com.example.devicetracker.LOCATION_UPDATE"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(locationReceiver)
    }
}
