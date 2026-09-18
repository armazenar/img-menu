package com.playlyna.player

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import java.util.concurrent.Executors

@CapacitorPlugin(
    name = "PlaylynaNative",
    permissions = [
        Permission(strings = [Manifest.permission.READ_MEDIA_AUDIO], alias = "audio"),
        Permission(strings = [Manifest.permission.READ_EXTERNAL_STORAGE], alias = "audio_legacy"),
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = "notif")
    ]
)
class PlaylynaNativePlugin : Plugin() {
    private val io = Executors.newSingleThreadExecutor()

    @PluginMethod
    fun ping(call: PluginCall) {
        val r = JSObject()
        r.put("ok", true)
        call.resolve(r)
    }

    @PluginMethod
    fun pedirPermissoes(call: PluginCall) {
        val audioAlias = if (Build.VERSION.SDK_INT >= 33) "audio" else "audio_legacy"
        val granted = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissionForAlias(audioAlias, call, "afterPermissions")
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val nGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!nGranted) {
                requestPermissionForAlias("notif", call, "afterPermissions")
                return
            }
        }
        val r = JSObject()
        r.put("granted", true)
        call.resolve(r)
    }

    @com.getcapacitor.annotation.PermissionCallback
    private fun afterPermissions(call: PluginCall) {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        val r = JSObject()
        r.put("granted", granted)
        call.resolve(r)
    }

    @PluginMethod
    fun scanMusic(call: PluginCall) {
        io.execute {
            try {
                val list = queryMediaStore()
                val result = JSObject()
                result.put("tracks", list)
                call.resolve(result)
            } catch (e: Exception) {
                call.reject("Erro MediaStore: " + e.message)
            }
        }
    }

    private fun queryMediaStore(): JSArray {
        val out = JSArray()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATA
        )
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor = context.contentResolver.query(
            uri, projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dispCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val relCol = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val o = JSObject()
                o.put("id", id.toString())
                o.put("title", c.getString(titleCol) ?: "")
                o.put("artist", c.getString(artistCol) ?: "")
                o.put("album", c.getString(albumCol) ?: "")
                o.put("duration", c.getLong(durCol) / 1000.0)
                o.put("fileName", c.getString(dispCol) ?: "")
                val rel = if (relCol >= 0) c.getString(relCol) ?: "" else ""
                o.put("folder", rel.trimEnd('/'))
                val dataPath = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                o.put("path", dataPath)
                o.put("contentUri", ContentUris.withAppendedId(uri, id).toString())
                out.put(o)
            }
        }
        return out
    }
}
