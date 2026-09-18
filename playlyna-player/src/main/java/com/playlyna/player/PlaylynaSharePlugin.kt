package com.playlyna.player

import android.content.Intent
import android.net.Uri
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "PlaylynaShare")
class PlaylynaSharePlugin : Plugin() {

    @PluginMethod
    fun ping(call: PluginCall) {
        val r = JSObject()
        r.put("ok", true)
        call.resolve(r)
    }

    @PluginMethod
    fun shareFile(call: PluginCall) {
        val contentUri = call.getString("contentUri")
        val mime = call.getString("mime") ?: "audio/*"
        if (contentUri.isNullOrEmpty()) {
            call.reject("contentUri obrigatorio")
            return
        }
        try {
            val uri = Uri.parse(contentUri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartilhar")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            call.resolve(JSObject())
        } catch (e: Exception) {
            call.reject("Falha compartilhar: " + e.message)
        }
    }

    @PluginMethod
    fun deleteFile(call: PluginCall) {
        val contentUri = call.getString("contentUri")
        if (contentUri.isNullOrEmpty()) { call.reject("contentUri obrigatorio"); return }
        try {
            val rows = context.contentResolver.delete(Uri.parse(contentUri), null, null)
            val r = JSObject()
            r.put("deleted", rows > 0)
            call.resolve(r)
        } catch (e: Exception) {
            call.reject("Falha excluir: " + e.message)
        }
    }
}
