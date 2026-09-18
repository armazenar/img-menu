package com.playlyna.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject

@CapacitorPlugin(name = "PlaylynaPlayer")
class PlaylynaPlayerPlugin : Plugin() {

    private var service: PlaylynaPlayerService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as PlaylynaPlayerService.LocalBinder
            service = local.getService()
            service?.setListener { event, data ->
                val ret = JSObject()
                ret.put("event", event)
                if (data != null) ret.put("data", JSONObject(data))
                notifyListeners("playerEvent", ret)
            }
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    override fun load() {
        try {
            val intent = Intent(context, PlaylynaPlayerService::class.java)
            ContextCompat.startForegroundService(context, intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {}
    }

    override fun handleOnDestroy() {
        if (bound) {
            try { context.unbindService(connection) } catch (_: Exception) {}
            bound = false
        }
        super.handleOnDestroy()
    }

    private fun svc(call: PluginCall): PlaylynaPlayerService? {
        if (service == null) { call.reject("Servico nao pronto"); return null }
        return service
    }

    // ============== METODOS QUE O HTML CHAMA ==============

    @PluginMethod
    fun ping(call: PluginCall) {
        val r = JSObject()
        r.put("ok", true)
        call.resolve(r)
    }

    // Atualiza titulo/artista/album/duracao da notificacao
    @PluginMethod
    fun setMetadata(call: PluginCall) {
        val s = svc(call) ?: return
        s.setMetadata(
            title = call.getString("title") ?: "",
            artist = call.getString("artist") ?: "",
            album = call.getString("album") ?: "",
            durationMs = call.getLong("durationMs") ?: 0L,
            isPlaying = call.getBoolean("isPlaying") ?: false
        )
        call.resolve()
    }

    // Atualiza posicao atual da notificacao (chamado a cada segundo pelo HTML)
    @PluginMethod
    fun setPosition(call: PluginCall) {
        val s = svc(call) ?: return
        s.setPosition(
            positionMs = call.getLong("positionMs") ?: 0L,
            isPlaying = call.getBoolean("isPlaying") ?: false
        )
        call.resolve()
    }

    @PluginMethod
    fun setQueue(call: PluginCall) {
        val s = svc(call) ?: return
        val arr = call.getArray("tracks") ?: run { call.reject("tracks obrigatorio"); return }
        val list = mutableListOf<PlaylynaPlayerService.TrackInfo>()
        try {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(PlaylynaPlayerService.TrackInfo(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    uri = o.optString("uri"),
                    durationMs = o.optLong("durationMs", 0L)
                ))
            }
        } catch (e: Exception) { call.reject("Falha tracks: " + e.message); return }
        s.setQueue(list, call.getInt("startIndex") ?: 0)
        call.resolve()
    }

    @PluginMethod fun play(call: PluginCall) { svc(call)?.play(); call.resolve() }
    @PluginMethod fun pause(call: PluginCall) { svc(call)?.pause(); call.resolve() }
    @PluginMethod fun toggle(call: PluginCall) { svc(call)?.toggle(); call.resolve() }
    @PluginMethod fun next(call: PluginCall) { svc(call)?.notifyNext(); call.resolve() }
    @PluginMethod fun prev(call: PluginCall) { svc(call)?.notifyPrev(); call.resolve() }

    @PluginMethod
    fun seekTo(call: PluginCall) { svc(call)?.notifySeek(call.getLong("ms") ?: 0L); call.resolve() }

    @PluginMethod
    fun setShuffle(call: PluginCall) { svc(call)?.setShuffle(call.getBoolean("on") ?: false); call.resolve() }

    @PluginMethod
    fun setRepeatMode(call: PluginCall) { svc(call)?.setRepeatMode(call.getString("mode") ?: "off"); call.resolve() }

    @PluginMethod
    fun getState(call: PluginCall) {
        val s = svc(call) ?: return
        val r = JSObject()
        r.put("isPlaying", s.isPlaying())
        r.put("positionMs", s.getPosition())
        r.put("durationMs", s.getDuration())
        r.put("currentIndex", s.getCurrentIndex())
        call.resolve(r)
    }
}
