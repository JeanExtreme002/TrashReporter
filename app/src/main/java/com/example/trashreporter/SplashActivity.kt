package com.example.trashreporter

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoading: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Força dark mode sempre
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize views
        progressBar = findViewById(R.id.progress_bar)
        tvLoading = findViewById(R.id.tv_loading)

        // Start loading animation
        startLoadingAnimation()
    }

    private fun startLoadingAnimation() {
        val loadingTexts = arrayOf(
            "Carregando...",
            "Inicializando...",
            "Preparando ambiente...",
            "Quase pronto!"
        )
        
        var textIndex = 0
        
        // Thread para atualizar a progress bar
        Thread {
            while (progressStatus < 100) {
                progressStatus += 2 // Incrementa 2% a cada 30ms (1.5s total)
                
                handler.post {
                    progressBar.progress = progressStatus
                    
                    // Atualiza o texto a cada 25% de progresso
                    val newTextIndex = progressStatus / 25
                    if (newTextIndex < loadingTexts.size && newTextIndex != textIndex) {
                        textIndex = newTextIndex
                        tvLoading.text = loadingTexts[textIndex]
                    }
                }
                
                try {
                    Thread.sleep(30) // 30ms * 50 iterações = 1.5 segundos
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            
            // Quando terminar, verifica se o usuário está logado
            handler.postDelayed({
                checkUserSession()
            }, 100) // Pequeno delay após completar
            
        }.start()
    }

    private fun checkUserSession() {
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        val loginTime = prefs.getLong("login_time", 0)
        
        // Verifica se o token existe e não expirou (30 minutos)
        val isTokenValid = token != null && 
            (System.currentTimeMillis() - loginTime) < (30 * 60 * 1000)
        
        val intent = if (isTokenValid) {
            Intent(this@SplashActivity, MainActivity::class.java)
        } else {
            // Token expirado ou não existe, vai para login
            Intent(this@SplashActivity, LoginActivity::class.java)
        }
        
        startActivity(intent)
        finish()
        
        // Adiciona animação de transição suave
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Previne que o usuário volte durante o splash
        // (não faz nada)
    }
}
