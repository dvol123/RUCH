package com.ruch.translator.data

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ruch.translator.R
import com.ruch.translator.RUCHApplication
import com.ruch.translator.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class ModelDownloadService : Service() {
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    companion object {
        const val ACTION_DOWNLOAD = "com.ruch.translator.DOWNLOAD_MODELS"
        const val EXTRA_DOWNLOAD_URL = "download_url"
        
        // Model URLs (these would be actual URLs in production)
        const val WHISPER_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        const val NLLB_MODEL_URL = "https://huggingface.co/facebook/nllb-200-distilled-600M/resolve/main/onnx.tar.gz"
        
        private const val NOTIFICATION_ID = 1001
        
        fun startDownload(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
            }
            context.startService(intent)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> startModelDownload()
        }
        return START_NOT_STICKY
    }
    
    private fun startModelDownload() {
        startForeground(NOTIFICATION_ID, createNotification(0, getString(R.string.model_downloading)))
        
        serviceScope.launch {
            try {
                val modelsDir = File(filesDir, "models")
                if (!modelsDir.exists()) {
                    modelsDir.mkdirs()
                }
                
                // Download Whisper model
                updateNotification(10, "Загрузка Whisper модели...")
                downloadModel(WHISPER_MODEL_URL, File(modelsDir, "whisper-small.bin"))
                
                // Download NLLB model
                updateNotification(40, "Загрузка NLLB модели...")
                downloadAndExtractModel(NLLB_MODEL_URL, File(modelsDir, "nllb"))
                
                // Download TTS models
                updateNotification(70, "Загрузка TTS моделей...")
                // TTS model download would go here
                
                updateNotification(100, getString(R.string.model_ready))
                
                // Mark models as downloaded
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                prefs.edit().putBoolean("models_downloaded", true).apply()
                
                Thread.sleep(2000)
                
            } catch (e: Exception) {
                updateNotification(0, getString(R.string.model_error))
            }
            
            stopSelf()
        }
    }
    
    private fun downloadModel(url: String, targetFile: File) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    
    private fun downloadAndExtractModel(url: String, targetDir: File) {
        // Download and extract zip/tar.gz file
        val tempFile = File(cacheDir, "model_temp.zip")
        
        try {
            downloadModel(url, tempFile)
            
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            
            // Extract zip file
            ZipInputStream(tempFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        FileOutputStream(file).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } finally {
            tempFile.delete()
        }
    }
    
    private fun createNotification(progress: Int, message: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, RUCHApplication.CHANNEL_MODEL_DOWNLOAD)
            .setContentTitle(getString(R.string.model_download_title))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(progress: Int, message: String) {
        val notification = createNotification(progress, message)
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
