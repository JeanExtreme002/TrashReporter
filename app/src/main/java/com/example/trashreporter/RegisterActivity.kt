package com.example.trashreporter

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import com.example.trashreporter.utils.ConfigReader

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var btnGoToLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        // Força dark mode sempre
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize views
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        btnRegister = findViewById(R.id.btn_register)
        btnGoToLogin = findViewById(R.id.btn_go_to_login)

        // Set click listeners
        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            
            if (validateInput(name, email, password, confirmPassword)) {
                performRegister(name, email, password)
            }
        }

        btnGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validateInput(name: String, email: String, password: String, confirmPassword: String): Boolean {
        if (name.isEmpty()) {
            etName.error = "Nome é obrigatório"
            etName.requestFocus()
            return false
        }

        if (name.length < 2) {
            etName.error = "Nome deve ter pelo menos 2 caracteres"
            etName.requestFocus()
            return false
        }

        if (email.isEmpty()) {
            etEmail.error = "Email é obrigatório"
            etEmail.requestFocus()
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Email inválido"
            etEmail.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            etPassword.error = "Senha é obrigatória"
            etPassword.requestFocus()
            return false
        }

        if (password.length < 6) {
            etPassword.error = "Senha deve ter pelo menos 6 caracteres"
            etPassword.requestFocus()
            return false
        }

        if (confirmPassword.isEmpty()) {
            etConfirmPassword.error = "Confirmação de senha é obrigatória"
            etConfirmPassword.requestFocus()
            return false
        }

        if (password != confirmPassword) {
            etConfirmPassword.error = "Senhas não coincidem"
            etConfirmPassword.requestFocus()
            return false
        }

        return true
    }

    private fun performRegister(name: String, email: String, password: String) {
        btnRegister.isEnabled = false
        btnRegister.text = "Cadastrando..."

        val executor = Executors.newSingleThreadExecutor()
        
        executor.execute {
            try {
                val apiUrl = "${getApiBaseUrl()}/auth/register"
                Log.d("REGISTER_API", "Fazendo cadastro em: $apiUrl")
                
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                // Create JSON body
                val jsonBody = JSONObject().apply {
                    put("name", name)
                    put("email", email)
                    put("password", password)
                }
                
                // Send request
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }
                
                val responseCode = connection.responseCode
                Log.d("REGISTER_API", "Response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonResponse = JSONObject(response)
                    
                    val token = jsonResponse.getString("access_token")
                    val userObj = jsonResponse.getJSONObject("user")
                    
                    // Save login data
                    saveUserSession(token, userObj.toString())
                    
                    runOnUiThread {
                        Toast.makeText(this@RegisterActivity, "Cadastro realizado com sucesso!", Toast.LENGTH_SHORT).show()
                        
                        // Go to main activity
                        val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.readText()
                    Log.e("REGISTER_API", "Erro na resposta: $errorResponse")
                    
                    val errorMessage = try {
                        val errorJson = JSONObject(errorResponse ?: "{}")
                        errorJson.getString("detail")
                    } catch (e: Exception) {
                        "Erro no cadastro"
                    }
                    
                    runOnUiThread {
                        Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e("REGISTER_ERROR", "Erro no cadastro: ${e.message}", e)
                    Toast.makeText(this@RegisterActivity, "Erro de conexão: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread {
                    btnRegister.isEnabled = true
                    btnRegister.text = "Cadastrar"
                }
            }
        }
    }

    private fun saveUserSession(token: String, userJson: String) {
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        
        // Parse user data to extract name and email
        try {
            val userObj = JSONObject(userJson)
            val userName = userObj.optString("name", "Usuário")
            val userEmail = userObj.optString("email", "usuario@email.com")
            
            prefs.edit().apply {
                putString("access_token", token)
                putString("user_data", userJson)
                putString("user_name", userName)
                putString("user_email", userEmail)
                putLong("login_time", System.currentTimeMillis())
                apply()
            }
            Log.d("USER_SESSION", "Sessão salva com sucesso - Nome: $userName, Email: $userEmail")
        } catch (e: Exception) {
            // Fallback se houver erro no parsing
            prefs.edit().apply {
                putString("access_token", token)
                putString("user_data", userJson)
                putString("user_name", "Usuário")
                putString("user_email", "usuario@email.com")
                putLong("login_time", System.currentTimeMillis())
                apply()
            }
            Log.e("USER_SESSION", "Erro ao fazer parse dos dados do usuário: ${e.message}")
        }
    }

    private fun getApiBaseUrl(): String {
        return try {
            ConfigReader.getApiBaseUrl(this).replace("/api", "")
        } catch (e: Exception) {
            Log.e("API_URL", "Erro ao obter URL da API: ${e.message}")
            "http://10.208.16.44:2000"
        }
    }
}
