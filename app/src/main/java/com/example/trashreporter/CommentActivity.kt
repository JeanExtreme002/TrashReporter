package com.example.trashreporter

import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.trashreporter.utils.ConfigReader
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class CommentActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView
    private lateinit var tvLocationInfo: TextView
    private lateinit var etComment: EditText
    private lateinit var tvCharCount: TextView
    private lateinit var btnCancel: Button
    private lateinit var btnSend: Button

    private var capturedImage: Bitmap? = null
    private var currentLocation: Location? = null
    private var macAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        // Força dark mode sempre
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comment)

        // Initialize views
        ivPreview = findViewById(R.id.iv_preview)
        tvLocationInfo = findViewById(R.id.tv_location_info)
        etComment = findViewById(R.id.et_comment)
        tvCharCount = findViewById(R.id.tv_char_count)
        btnCancel = findViewById(R.id.btn_cancel)
        btnSend = findViewById(R.id.btn_send)

        // Get data from intent
        getDataFromIntent()

        // Setup character counter
        setupCharacterCounter()

        // Setup button listeners
        setupButtonListeners()

        // Update location info
        updateLocationInfo()
    }

    private fun getDataFromIntent() {
        // Get image from intent
        val imageBytes = intent.getByteArrayExtra("image")
        if (imageBytes != null) {
            capturedImage = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ivPreview.setImageBitmap(capturedImage)
        }

        // Get location from intent
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        if (latitude != 0.0 && longitude != 0.0) {
            currentLocation = Location("").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
        }

        // Get MAC address
        macAddress = intent.getStringExtra("macAddress") ?: "02:00:00:00:00:00"

        Log.d("COMMENT_ACTIVITY", "Recebido - Lat: $latitude, Lng: $longitude, MAC: $macAddress")
    }

    private fun setupCharacterCounter() {
        etComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s?.length ?: 0
                tvCharCount.text = "$count/500"
                
                // Change color based on character count
                tvCharCount.setTextColor(
                    when {
                        count > 450 -> getColor(R.color.accent_red)
                        count > 350 -> getColor(R.color.accent_orange)
                        else -> getColor(R.color.text_secondary_dark)
                    }
                )
            }
        })
    }

    private fun setupButtonListeners() {
        btnCancel.setOnClickListener {
            // Just finish the activity, go back to MainActivity
            finish()
        }

        btnSend.setOnClickListener {
            sendDataToAPI()
        }
    }

    private fun updateLocationInfo() {
        if (currentLocation != null) {
            val lat = String.format("%.4f", currentLocation!!.latitude)
            val lng = String.format("%.4f", currentLocation!!.longitude)
            tvLocationInfo.text = "📍 Localização: $lat, $lng"
        } else {
            tvLocationInfo.text = "📍 Localização não disponível"
        }
    }

    private fun sendDataToAPI() {
        if (capturedImage == null) {
            Toast.makeText(this, "Erro: Imagem não encontrada", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable send button to prevent double-clicking
        btnSend.isEnabled = false
        btnSend.text = "📤 Enviando..."

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
                capturedImage!!.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val imageBytes = baos.toByteArray()
                val imageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                
                // Get comment text
                val comment = etComment.text.toString().trim()
                
                // Create JSON payload
                val jsonObject = JSONObject().apply {
                    put("image", imageBase64)
                    put("coords", JSONObject().apply {
                        put("latitude", currentLocation?.latitude ?: 0.0)
                        put("longitude", currentLocation?.longitude ?: 0.0)
                    })
                    put("id", macAddress)
                    put("comment", comment) // Add comment field
                }
                
                val jsonString = jsonObject.toString()
                connection.outputStream.write(jsonString.toByteArray())
                
                val responseCode = connection.responseCode
                Log.d("API_RESPONSE", "Response code: $responseCode")
                
                runOnUiThread {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(this@CommentActivity, "Report enviado com sucesso!", Toast.LENGTH_SHORT).show()
                        
                        // Return to MainActivity and start countdown
                        val intent = Intent(this@CommentActivity, MainActivity::class.java)
                        intent.putExtra("start_countdown", true)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@CommentActivity, "Erro ao enviar report (código: $responseCode)", Toast.LENGTH_SHORT).show()
                        btnSend.isEnabled = true
                        btnSend.text = "📤 Enviar Report"
                    }
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e("API_ERROR", "Erro de conexão: ${e.message}", e)
                    Toast.makeText(this@CommentActivity, "Erro de conexão: ${e.message}", Toast.LENGTH_LONG).show()
                    btnSend.isEnabled = true
                    btnSend.text = "📤 Enviar Report"
                }
            }
        }
    }

    private fun getApiBaseUrl(): String {
        // Similar to MainActivity, detect emulator vs physical device
        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") || 
                   android.os.Build.FINGERPRINT.contains("unknown") ||
                   android.os.Build.MODEL.contains("google_sdk") ||
                   android.os.Build.MODEL.contains("Emulator") ||
                   android.os.Build.MODEL.contains("Android SDK built for x86")
        
        return if (isEmulator) {
            "http://10.0.2.2:2000/api" // Emulador sempre usa este IP
        } else {
            // Para dispositivo físico, usa o IP do arquivo .env
            try {
                ConfigReader.getApiBaseUrl(this)
            } catch (e: Exception) {
                Log.e("API_URL", "Erro ao carregar configuração: ${e.message}", e)
                "http://10.208.16.44:2000/api" // Fallback
            }
        }
    }
}
