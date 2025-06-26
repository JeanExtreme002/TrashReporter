package com.example.trashreporter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var btnReport: Button
    private lateinit var tvCountdown: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var countDownTimer: CountDownTimer? = null
    private var currentLocation: Location? = null
    private var capturedImage: Bitmap? = null
    
    private val REQUEST_PERMISSIONS = 1001
    private val CAMERA_REQUEST_CODE = 1002
    
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            capturedImage = data?.extras?.get("data") as? Bitmap
            capturedImage?.let {
                sendDataToAPI(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        btnReport = findViewById(R.id.btn_report)
        tvCountdown = findViewById(R.id.tv_countdown)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        btnReport.setOnClickListener {
            if (checkPermissions()) {
                getCurrentLocation()
            } else {
                requestPermissions()
            }
        }
        
        // Check if there's an active countdown
        checkCountdownState()
    }
    
    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        
        return permissions.all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
        }
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Permissões necessárias para continuar", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = location
                    openCamera()
                } else {
                    Toast.makeText(this, "Não foi possível obter a localização", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Erro ao obter localização", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            takePictureLauncher.launch(takePictureIntent)
        } else {
            Toast.makeText(this, "Camera não disponível", Toast.LENGTH_SHORT).show()
        }
    }
    
    @SuppressLint("HardwareIds")
    private fun getMacAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        return try {
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo.macAddress ?: "02:00:00:00:00:00"
        } catch (e: Exception) {
            "02:00:00:00:00:00" // Fallback MAC address
        }
    }
    
    private fun sendDataToAPI(image: Bitmap) {
        val executor = Executors.newSingleThreadExecutor()
        
        executor.execute {
            try {
                val url = URL("http://localhost:2000/api")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                // Convert image to base64
                val baos = ByteArrayOutputStream()
                image.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val imageBytes = baos.toByteArray()
                val imageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                
                // Create JSON payload
                val jsonObject = JSONObject().apply {
                    put("image", imageBase64)
                    put("coords", JSONObject().apply {
                        put("latitude", currentLocation?.latitude ?: 0.0)
                        put("longitude", currentLocation?.longitude ?: 0.0)
                    })
                    put("id", getMacAddress())
                }
                
                val jsonString = jsonObject.toString()
                connection.outputStream.write(jsonString.toByteArray())
                
                val responseCode = connection.responseCode
                
                runOnUiThread {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(this@MainActivity, "Dados enviados com sucesso!", Toast.LENGTH_SHORT).show()
                        startCountdown()
                    } else {
                        Toast.makeText(this@MainActivity, "Erro ao enviar dados", Toast.LENGTH_SHORT).show()
                    }
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Erro de conexão: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun startCountdown() {
        val oneHourInMillis = 60 * 60 * 1000L // 1 hora em millisegundos
        
        // Save countdown end time
        val prefs = getSharedPreferences("countdown_prefs", MODE_PRIVATE)
        val endTime = System.currentTimeMillis() + oneHourInMillis
        prefs.edit().putLong("countdown_end_time", endTime).apply()
        
        btnReport.isEnabled = false
        tvCountdown.visibility = View.VISIBLE
        
        countDownTimer = object : CountDownTimer(oneHourInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / (1000 * 60 * 60)
                val minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60)
                val seconds = (millisUntilFinished % (1000 * 60)) / 1000
                
                tvCountdown.text = String.format("Próximo report em: %02d:%02d:%02d", hours, minutes, seconds)
            }
            
            override fun onFinish() {
                btnReport.isEnabled = true
                tvCountdown.visibility = View.GONE
                
                // Clear saved countdown
                val prefs = getSharedPreferences("countdown_prefs", MODE_PRIVATE)
                prefs.edit().remove("countdown_end_time").apply()
                
                Toast.makeText(this@MainActivity, "Você pode reportar novamente!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
    
    private fun checkCountdownState() {
        val prefs = getSharedPreferences("countdown_prefs", MODE_PRIVATE)
        val endTime = prefs.getLong("countdown_end_time", 0L)
        
        if (endTime > 0) {
            val remainingTime = endTime - System.currentTimeMillis()
            
            if (remainingTime > 0) {
                // Continue countdown
                btnReport.isEnabled = false
                tvCountdown.visibility = View.VISIBLE
                
                countDownTimer = object : CountDownTimer(remainingTime, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val hours = millisUntilFinished / (1000 * 60 * 60)
                        val minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60)
                        val seconds = (millisUntilFinished % (1000 * 60)) / 1000
                        
                        tvCountdown.text = String.format("Próximo report em: %02d:%02d:%02d", hours, minutes, seconds)
                    }
                    
                    override fun onFinish() {
                        btnReport.isEnabled = true
                        tvCountdown.visibility = View.GONE
                        prefs.edit().remove("countdown_end_time").apply()
                        Toast.makeText(this@MainActivity, "Você pode reportar novamente!", Toast.LENGTH_SHORT).show()
                    }
                }.start()
            } else {
                // Countdown expired, clear it
                prefs.edit().remove("countdown_end_time").apply()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}