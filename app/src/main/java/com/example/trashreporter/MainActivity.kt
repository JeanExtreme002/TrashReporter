package com.example.trashreporter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.graphics.BitmapFactory
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// Data class para representar um registro
data class ReportRecord(
    val coords: String,
    val datetime: String,
    val status: String,
    val image: String? = null  // Base64 encoded image
)

// Adapter para a RecyclerView
class RecordsAdapter(private val records: List<ReportRecord>) : 
    RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCoords: TextView = view.findViewById(R.id.tv_coords)
        val tvDatetime: TextView = view.findViewById(R.id.tv_datetime)
        val tvStatus: TextView = view.findViewById(R.id.tv_status)
        val ivReportImage: ImageView = view.findViewById(R.id.iv_report_image)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        holder.tvCoords.text = "Coordenadas: ${record.coords}"
        holder.tvDatetime.text = "Data: ${record.datetime}"
        holder.tvStatus.text = "Status: ${record.status}"
        
        // Lidar com a imagem
        if (record.image != null && record.image.isNotEmpty()) {
            try {
                // Decodifica a imagem base64
                val imageBytes = Base64.decode(record.image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                
                holder.ivReportImage.setImageBitmap(bitmap)
                holder.ivReportImage.visibility = View.VISIBLE
            } catch (e: Exception) {
                // Se há erro ao decodificar, esconde a imagem
                holder.ivReportImage.visibility = View.GONE
                Log.e("IMAGE_DECODE", "Erro ao decodificar imagem: ${e.message}")
            }
        } else {
            // Se não há imagem, esconde a ImageView
            holder.ivReportImage.visibility = View.GONE
        }
    }
    
    override fun getItemCount() = records.size
}

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var btnReport: Button
    private lateinit var tvCountdown: TextView
    private lateinit var locationManager: LocationManager
    
    // Navigation
    private lateinit var btnNavReport: Button
    private lateinit var btnNavRecords: Button
    private lateinit var reportScreen: View
    private lateinit var recordsScreen: View
    private lateinit var rvRecords: RecyclerView
    
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
        // Força dark mode sempre
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Initialize views
        btnReport = findViewById(R.id.btn_report)
        tvCountdown = findViewById(R.id.tv_countdown)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Navigation views
        btnNavReport = findViewById(R.id.btn_nav_report)
        btnNavRecords = findViewById(R.id.btn_nav_records)
        reportScreen = findViewById(R.id.report_screen)
        recordsScreen = findViewById(R.id.records_screen)
        rvRecords = findViewById(R.id.rv_records)
        
        // Setup RecyclerView
        rvRecords.layoutManager = LinearLayoutManager(this)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Report button click
        btnReport.setOnClickListener {
            if (checkPermissions()) {
                getCurrentLocation()
            } else {
                requestPermissions()
            }
        }
        
        // Navigation clicks
        btnNavReport.setOnClickListener {
            showReportScreen()
        }
        
        btnNavRecords.setOnClickListener {
            showRecordsScreen()
        }
        
        // Check if there's an active countdown
        checkCountdownState()
        
        // Start with report screen
        showReportScreen()
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
        try {
            // Try to get last known location first
            val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (lastKnownLocation != null) {
                currentLocation = lastKnownLocation
                openCamera()
            } else {
                // Request location updates
                Toast.makeText(this, "Obtendo localização...", Toast.LENGTH_SHORT).show()
                
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        1f,
                        this
                    )
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000L,
                        1f,
                        this
                    )
                } else {
                    Toast.makeText(this, "GPS e rede desabilitados", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Erro de permissão de localização", Toast.LENGTH_SHORT).show()
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
    
    private fun getApiBaseUrl(): String {
        // Detecta automaticamente se está rodando no emulador ou dispositivo físico
        return if (Build.FINGERPRINT.contains("generic") || 
                   Build.FINGERPRINT.contains("unknown") ||
                   Build.MODEL.contains("google_sdk") ||
                   Build.MODEL.contains("Emulator") ||
                   Build.MODEL.contains("Android SDK built for x86")) {
            Log.d("API_URL", "Detectado emulador, usando 10.0.2.2")
            "http://10.0.2.2:2000/api" // Emulador
        } else {
            Log.d("API_URL", "Detectado dispositivo físico, usando IP da rede local")
            "http://192.168.0.13:2000/api" // IP fixo da máquina
        }
    }
    
    private fun sendDataToAPI(image: Bitmap) {
        val executor = Executors.newSingleThreadExecutor()
        
        executor.execute {
            try {
                val apiUrl = getApiBaseUrl()
                Log.d("API_CONNECTION", "Tentando conectar em: $apiUrl")
                
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000 // 10 segundos
                connection.readTimeout = 10000
                
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
                Log.d("API_RESPONSE", "Response code: $responseCode")
                
                runOnUiThread {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(this@MainActivity, "Dados enviados com sucesso!", Toast.LENGTH_SHORT).show()
                        startCountdown()
                    } else {
                        Toast.makeText(this@MainActivity, "Erro ao enviar dados (código: $responseCode)", Toast.LENGTH_SHORT).show()
                    }
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e("API_ERROR", "Erro de conexão: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "Erro de conexão: ${e.message}", Toast.LENGTH_LONG).show()
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
    
    // Navigation methods
    private fun showReportScreen() {
        reportScreen.visibility = View.VISIBLE
        recordsScreen.visibility = View.GONE
        
        // Update navigation buttons appearance
        btnNavReport.setTextColor(ContextCompat.getColor(this, R.color.navigation_selected))
        btnNavReport.alpha = 1.0f
        btnNavRecords.setTextColor(ContextCompat.getColor(this, R.color.navigation_unselected_dark))
        btnNavRecords.alpha = 0.6f
    }
    
    private fun showRecordsScreen() {
        reportScreen.visibility = View.GONE
        recordsScreen.visibility = View.VISIBLE
        
        // Update navigation buttons appearance
        btnNavReport.setTextColor(ContextCompat.getColor(this, R.color.navigation_unselected_dark))
        btnNavReport.alpha = 0.6f
        btnNavRecords.setTextColor(ContextCompat.getColor(this, R.color.navigation_selected))
        btnNavRecords.alpha = 1.0f
        
        // Load records
        loadRecords()
    }
    
    private fun loadRecords() {
        val executor = Executors.newSingleThreadExecutor()
        
        executor.execute {
            try {
                val macAddress = getMacAddress()
                val apiUrl = "${getApiBaseUrl()}/$macAddress"
                Log.d("API_CONNECTION", "Carregando registros de: $apiUrl")
                
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Log.d("API_RESPONSE", "Response code para loadRecords: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonArray = JSONArray(response)
                    
                    val records = mutableListOf<ReportRecord>()
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        records.add(
                            ReportRecord(
                                coords = item.getString("coords"),
                                datetime = item.getString("datetime"),
                                status = item.getString("status"),
                                image = if (item.has("image") && !item.isNull("image")) 
                                    item.getString("image") else null
                            )
                        )
                    }
                    
                    runOnUiThread {
                        val adapter = RecordsAdapter(records)
                        rvRecords.adapter = adapter
                        
                        if (records.isEmpty()) {
                            Toast.makeText(this@MainActivity, "Nenhum registro encontrado", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Erro ao carregar registros (código: $responseCode)", Toast.LENGTH_SHORT).show()
                    }
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e("API_ERROR", "Erro ao carregar registros: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "Erro de conexão ao carregar registros: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // LocationListener methods
    override fun onLocationChanged(location: Location) {
        currentLocation = location
        locationManager.removeUpdates(this)
        openCamera()
    }
    
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        locationManager.removeUpdates(this)
    }
}