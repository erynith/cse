package com.erynith

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.erynith.settings.SettingsFragment

@CloudstreamPlugin
class DebridStreamExProvider: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("DebridStreamEx", Context.MODE_PRIVATE)
        registerMainAPI(DebridStreamEx(sharedPref))

        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}
