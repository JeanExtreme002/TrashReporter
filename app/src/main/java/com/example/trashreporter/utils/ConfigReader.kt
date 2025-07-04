package com.example.trashreporter.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader

object ConfigReader {
    private const val TAG = "ConfigReader"
    private var config: Map<String, String>? = null
    
    fun loadConfig(context: Context): Map<String, String> {
        if (config != null) {
            Log.d(TAG, "Usando configuração em cache")
            return config!!
        }
        
        val configMap = mutableMapOf<String, String>()
        
        // Primeiro, tenta carregar das SharedPreferences (configuração do usuário tem prioridade)
        val prefsConfig = loadFromPreferences(context)
        if (prefsConfig.isNotEmpty()) {
            Log.d(TAG, "🔧 CONFIGURAÇÃO CARREGADA DAS PREFERÊNCIAS DO USUÁRIO (ALTERAÇÃO VIA DIALOG)")
            Log.d(TAG, "🔍 Valores das preferências: $prefsConfig")
            configMap.putAll(prefsConfig)
            config = configMap
            return configMap
        }
        
        Log.d(TAG, "🚀 Nenhuma configuração encontrada nas SharedPreferences - carregando do .env")
        
        // Se não tem configuração do usuário, carrega do arquivo .env
        try {
            Log.d(TAG, "Tentando carregar arquivo .env dos assets...")
            val inputStream = context.assets.open(".env")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            Log.d(TAG, "Arquivo .env encontrado, lendo conteúdo...")
            reader.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    Log.d(TAG, "Linha $index: '$trimmed'")
                    
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("=", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            configMap[key] = value
                            Log.d(TAG, "Configuração lida: $key = $value")
                        }
                    }
                }
            }
            
            if (configMap.isEmpty()) {
                Log.w(TAG, "❌ Arquivo .env está vazio ou não contém configurações válidas")
                // Valores padrão
                configMap["API_HOST"] = "10.208.16.44"
                configMap["API_PORT"] = "2000"
                Log.d(TAG, "Usando valores padrão")
            } else {
                Log.d(TAG, "✅ CONFIGURAÇÃO CARREGADA DO ARQUIVO .ENV DOS ASSETS")
                Log.d(TAG, "🔍 Valores carregados: $configMap")
            }
            
            config = configMap
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar arquivo .env: ${e.message}", e)
            Log.w(TAG, "Usando valores padrão")
            // Valores padrão
            configMap["API_HOST"] = "10.208.16.44"
            configMap["API_PORT"] = "2000"
            
            config = configMap
        }
        
        return configMap
    }
    
    fun getApiHost(context: Context): String {
        val config = loadConfig(context)
        val host = config["API_HOST"] ?: "10.208.16.44"
        
        // Verifica a origem da configuração
        val isFromPreferences = hasPreferencesConfig(context)
        if (isFromPreferences) {
            Log.d(TAG, "🔧 API_HOST obtido das PREFERÊNCIAS DO USUÁRIO: $host")
        } else {
            Log.d(TAG, "📁 API_HOST obtido do ARQUIVO .ENV: $host")
        }
        
        return host
    }
    
    fun getApiPort(context: Context): String {
        val config = loadConfig(context)
        val port = config["API_PORT"] ?: "2000"
        
        // Verifica a origem da configuração
        val isFromPreferences = hasPreferencesConfig(context)
        if (isFromPreferences) {
            Log.d(TAG, "🔧 API_PORT obtido das PREFERÊNCIAS DO USUÁRIO: $port")
        } else {
            Log.d(TAG, "📁 API_PORT obtido do ARQUIVO .ENV: $port")
        }
        
        return port
    }
    
    fun getApiBaseUrl(context: Context): String {
        val host = getApiHost(context)
        val port = getApiPort(context)
        return "http://$host:$port/api"
    }
    
    fun setApiConfig(context: Context, host: String, port: String = "2000") {
        Log.d(TAG, "Definindo configuração programaticamente: $host:$port")
        val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("API_HOST", host)
            .putString("API_PORT", port)
            .apply()
        
        // Limpa o cache para forçar reload
        config = null
    }
    
    fun updateApiHost(context: Context, newHost: String) {
        Log.d(TAG, "Atualizando host da API para: $newHost")
        setApiConfig(context, newHost)
        
        // Force reload na próxima chamada
        config = null
        
        Toast.makeText(context, "IP atualizado para: $newHost", Toast.LENGTH_LONG).show()
    }
    
    fun getCurrentConfig(context: Context): String {
        val host = getApiHost(context)
        val port = getApiPort(context)
        return "$host:$port"
    }
    
    fun hasPreferencesConfig(context: Context): Boolean {
        val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
        return prefs.contains("API_HOST") || prefs.contains("API_PORT")
    }
    
    fun clearPreferencesConfig(context: Context) {
        Log.d(TAG, "Limpando configuração das preferências - voltando para o .env")
        val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        // Limpa o cache para forçar reload
        config = null
        
        Toast.makeText(context, "Configuração resetada - usando .env", Toast.LENGTH_LONG).show()
    }
    
    fun forceReloadConfig() {
        Log.d(TAG, "Forçando reload da configuração...")
        config = null
    }
    
    fun getConfigSource(context: Context): String {
        return if (hasPreferencesConfig(context)) {
            "SharedPreferences (alteração do usuário)"
        } else {
            "Arquivo .env dos assets"
        }
    }
    
    fun debugConfig(context: Context): String {
        val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
        val allPrefs = prefs.all
        
        val debugInfo = StringBuilder()
        debugInfo.append("=== DEBUG CONFIGURAÇÃO ===\n")
        debugInfo.append("SharedPreferences contém:\n")
        
        if (allPrefs.isEmpty()) {
            debugInfo.append("  (vazio - usando .env)\n")
        } else {
            allPrefs.forEach { (key, value) ->
                debugInfo.append("  $key = $value\n")
            }
        }
        
        debugInfo.append("\nConfiguração atual:\n")
        debugInfo.append("  API_HOST = ${getApiHost(context)}\n")
        debugInfo.append("  API_PORT = ${getApiPort(context)}\n")
        debugInfo.append("  Fonte = ${getConfigSource(context)}\n")
        
        return debugInfo.toString()
    }
    
    private fun loadFromPreferences(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
        val configMap = mutableMapOf<String, String>()
        
        val host = prefs.getString("API_HOST", null)
        val port = prefs.getString("API_PORT", null)
        
        if (host != null) {
            configMap["API_HOST"] = host
            Log.d(TAG, "Host carregado das preferências: $host")
        }
        
        if (port != null) {
            configMap["API_PORT"] = port
            Log.d(TAG, "Port carregado das preferências: $port")
        }
        
        return configMap
    }
}
