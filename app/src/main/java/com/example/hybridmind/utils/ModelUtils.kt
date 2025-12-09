package com.example.hybridmind.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ModelUtils {
    
    private const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"
    
    /**
     * Copies the model from assets to internal storage if not already present.
     * Returns the absolute path to the model file.
     */
    fun getModelPath(context: Context): String {
        val destFile = File(context.filesDir, MODEL_FILENAME)
        
        // If already copied, return the path
        if (destFile.exists()) {
            return destFile.absolutePath
        }
        
        // Copy from assets to internal storage
        context.assets.open(MODEL_FILENAME).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        
        return destFile.absolutePath
    }
    
    /**
     * Checks if the model file exists in assets or internal storage.
     */
    fun isModelAvailable(context: Context): Boolean {
        // Check if already in internal storage
        val destFile = File(context.filesDir, MODEL_FILENAME)
        if (destFile.exists()) {
            return true
        }
        
        // Check if in assets
        return try {
            context.assets.open(MODEL_FILENAME).use { true }
        } catch (e: Exception) {
            false
        }
    }
}
