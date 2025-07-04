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
import com.example.trashreporter.utils.ConfigReader

// Data class para representar um registro
data class ReportRecord(
    val coords: String,
    val datetime: String,
    val status: String,
    val image: String? = null,  // Base64 encoded image
    val comment: String? = null  // User comment
)

// Adapter para a RecyclerView
class RecordsAdapter(private val records: List<ReportRecord>) : 
    RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCoords: TextView = view.findViewById(R.id.tv_coords)
        val tvDatetime: TextView = view.findViewById(R.id.tv_datetime)
        val tvStatus: TextView = view.findViewById(R.id.tv_status)
        val tvComment: TextView = view.findViewById(R.id.tv_comment)
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
        
        // Lidar com comentário
        if (record.comment != null && record.comment.isNotEmpty()) {
            holder.tvComment.text = "💬 Comentário: ${record.comment}"
            holder.tvComment.visibility = View.VISIBLE
        } else {
            holder.tvComment.visibility = View.GONE
        }
        
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
    private lateinit var btnResetCountdown: Button
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
                openCommentScreen(it)
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
        btnResetCountdown = findViewById(R.id.btn_reset_countdown)
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
        
        // Long press no botão de report para mostrar/esconder botão de reset (modo debug)
        btnReport.setOnLongClickListener {
            if (btnResetCountdown.visibility == View.GONE) {
                btnResetCountdown.visibility = View.VISIBLE
                
                // Também força a configuração do IP se necessário
                val currentIp = getCurrentNetworkIp()
                if (currentIp != null) {
                    ConfigReader.setApiConfig(this, currentIp)
                    Log.d("CONFIG_DEBUG", "IP atualizado para: $currentIp")
                    Toast.makeText(this, "Modo debug ativado - IP: $currentIp", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Modo debug ativado", Toast.LENGTH_SHORT).show()
                }
            } else {
                btnResetCountdown.visibility = View.GONE
                Toast.makeText(this, "Modo debug desativado", Toast.LENGTH_SHORT).show()
            }
            true
        }
        
        // Reset countdown button click
        btnResetCountdown.setOnClickListener {
            resetCountdown()
            Toast.makeText(this, "Countdown resetado!", Toast.LENGTH_SHORT).show()
        }
        
        // Double tap no countdown para alterar IP (modo debug)
        tvCountdown.setOnClickListener {
            showIpConfigDialog()
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
        
        // Check if we should start countdown (returning from CommentActivity)
        if (intent.getBooleanExtra("start_countdown", false)) {
            startCountdown()
        }
        
        // Start with report screen
        showReportScreen()
        
        // Teste de configuração (debug)
        testConfigReading()
        
        // Testar leitura da configuração (modo debug)
        testConfigReading()
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
    
    private fun openCommentScreen(image: Bitmap) {
        if (currentLocation == null) {
            Toast.makeText(this, "Erro: Localização não disponível", Toast.LENGTH_SHORT).show()
            return
        }

        // Convert bitmap to byte array to pass via intent
        val baos = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val imageBytes = baos.toByteArray()

        val intent = Intent(this, CommentActivity::class.java).apply {
            putExtra("image", imageBytes)
            putExtra("latitude", currentLocation!!.latitude)
            putExtra("longitude", currentLocation!!.longitude)
            putExtra("macAddress", getMacAddress())
        }
        
        startActivity(intent)
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
        // Log detalhado para debug
        Log.d("API_URL", "=== DEBUG DETECÇÃO DE DISPOSITIVO ===")
        Log.d("API_URL", "Build.FINGERPRINT: ${Build.FINGERPRINT}")
        Log.d("API_URL", "Build.MODEL: ${Build.MODEL}")
        Log.d("API_URL", "Build.PRODUCT: ${Build.PRODUCT}")
        Log.d("API_URL", "Build.DEVICE: ${Build.DEVICE}")
        
        // Detecta automaticamente se está rodando no emulador ou dispositivo físico
        val isEmulator = Build.FINGERPRINT.contains("generic") || 
                   Build.FINGERPRINT.contains("unknown") ||
                   Build.MODEL.contains("google_sdk") ||
                   Build.MODEL.contains("Emulator") ||
                   Build.MODEL.contains("Android SDK built for x86")
        
        Log.d("API_URL", "É emulador? $isEmulator")
        
        return if (isEmulator) {
            Log.d("API_URL", "Detectado emulador, usando 10.0.2.2")
            "http://10.0.2.2:2000/api" // Emulador sempre usa este IP
        } else {
            // Para dispositivo físico, usa o IP do arquivo .env
            Log.d("API_URL", "Detectado dispositivo físico, carregando configuração...")
            
            try {
                val apiUrl = ConfigReader.getApiBaseUrl(this)
                val host = ConfigReader.getApiHost(this)
                val port = ConfigReader.getApiPort(this)
                val configSource = ConfigReader.getConfigSource(this)
                
                Log.d("API_URL", "Host: $host (de: $configSource)")
                Log.d("API_URL", "Port: $port") 
                Log.d("API_URL", "URL final: $apiUrl")
                
                apiUrl
            } catch (e: Exception) {
                Log.e("API_URL", "Erro ao carregar configuração: ${e.message}", e)
                val fallbackUrl = "http://10.208.16.44:2000/api"
                Log.w("API_URL", "Usando URL de fallback: $fallbackUrl")
                fallbackUrl
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
        btnResetCountdown.visibility = View.VISIBLE // Mostra o botão de reset quando countdown está ativo
        
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
                btnResetCountdown.visibility = View.GONE // Esconde o botão quando countdown termina
                
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
                btnResetCountdown.visibility = View.VISIBLE // Mostra o botão de reset
                
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
                        btnResetCountdown.visibility = View.GONE // Esconde o botão
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
    
    private fun resetCountdown() {
        // Para o timer atual se estiver rodando
        countDownTimer?.cancel()
        
        // Limpa as preferências salvas
        val prefs = getSharedPreferences("countdown_prefs", MODE_PRIVATE)
        prefs.edit().remove("countdown_end_time").apply()
        
        // Reabilita o botão e esconde o countdown
        btnReport.isEnabled = true
        tvCountdown.visibility = View.GONE
        btnResetCountdown.visibility = View.GONE // Esconde o botão de reset
        
        Log.d("COUNTDOWN_RESET", "Countdown foi resetado")
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
                                    item.getString("image") else null,
                                comment = if (item.has("comment") && !item.isNull("comment")) 
                                    item.getString("comment") else null
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
    
    private fun getCurrentNetworkIp(): String? {
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            
            if (ipAddress != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e("NETWORK_IP", "Erro ao obter IP: ${e.message}")
        }
        
        return null
    }
    
    private fun testConfigReading() {
        Log.d("CONFIG_TEST", "=== TESTE DE CONFIGURAÇÃO ===")
        try {
            val config = ConfigReader.loadConfig(this)
            val source = ConfigReader.getConfigSource(this)
            val hasPrefs = ConfigReader.hasPreferencesConfig(this)
            
            Log.d("CONFIG_TEST", "Configuração carregada: $config")
            Log.d("CONFIG_TEST", "Origem da configuração: $source")
            Log.d("CONFIG_TEST", "Tem configuração em SharedPreferences: $hasPrefs")
            
            val host = ConfigReader.getApiHost(this)
            val port = ConfigReader.getApiPort(this)
            val url = ConfigReader.getApiBaseUrl(this)
            
            Log.d("CONFIG_TEST", "Host: $host")
            Log.d("CONFIG_TEST", "Port: $port")
            Log.d("CONFIG_TEST", "URL: $url")
            
            Toast.makeText(this, "Config ($source): $host:$port", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("CONFIG_TEST", "Erro no teste: ${e.message}", e)
            Toast.makeText(this, "Erro ao ler config: ${e.message}", Toast.LENGTH_LONG).show()
        }
        Log.d("CONFIG_TEST", "=== FIM DO TESTE ===")
    }
    
    private fun showIpConfigDialog() {
        val currentConfig = ConfigReader.getCurrentConfig(this)
        val currentHost = ConfigReader.getApiHost(this)
        val configSource = ConfigReader.getConfigSource(this)
        val debugInfo = ConfigReader.debugConfig(this)
        
        Log.d("IP_CONFIG", debugInfo)
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("🔧 Configurar IP da API")
        builder.setMessage("IP atual: $currentConfig\nOrigem: $configSource\n\n⚠️ Se alterou o .env, use 'Reset' primeiro")
        
        val input = android.widget.EditText(this)
        input.hint = "Ex: 10.208.16.44"
        input.setText(currentHost)
        builder.setView(input)
        
        builder.setPositiveButton("✅ Salvar") { _, _ ->
            val newHost = input.text.toString().trim()
            if (newHost.isNotEmpty()) {
                ConfigReader.updateApiHost(this, newHost)
                Log.d("IP_CONFIG", "IP alterado para: $newHost")
            }
        }
        
        builder.setNeutralButton("🔄 Reset para .env") { _, _ ->
            ConfigReader.clearPreferencesConfig(this)
            val newDebugInfo = ConfigReader.debugConfig(this)
            Log.d("IP_CONFIG", "Após reset:\n$newDebugInfo")
        }
        
        builder.setNegativeButton("� Debug Info") { _, _ ->
            Toast.makeText(this, debugInfo, Toast.LENGTH_LONG).show()
            Log.d("IP_CONFIG", debugInfo)
        }
        
        builder.show()
    }
}