package com.erynith.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.erynith.BuildConfig
import com.erynith.FavoriteList
import com.erynith.Favoritism
import com.erynith.FavoritismProvider
import com.erynith.fetchFavoritesText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment(
    private val plugin: FavoritismProvider,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = res.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        return inflater.inflate(res.getLayout(id), container, false)
    }

    private fun getDrawable(name: String): Drawable {
        val id = res.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return res.getDrawable(id, null)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View?.makeTvCompatible() {
        if (this == null) return
        val outlineId = res.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (outlineId != 0) {
            val drawable = res.getDrawable(outlineId, null)
            if (drawable != null) background = drawable
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = getLayout("settings", inflater, container)

        // ===== DEBRID SERVICE =====
        val debridSpinner = root.findView<Spinner>("debrid_service_spinner")
        val debridServices = listOf(
            "Real-Debrid", "TorBox", "All-Debrid", "Debrid-Link",
            "Premiumize", "Debrider", "EasyDebrid", "Offcloud", "PikPak"
        )
        debridSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, debridServices).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        sharedPref.getString(Favoritism.PREF_SERVICE, null)?.let { saved ->
            val pos = debridServices.indexOf(saved)
            if (pos >= 0) debridSpinner.setSelection(pos)
        }
        debridSpinner.makeTvCompatible()

        val debridKeyInput = root.findView<EditText>("debrid_key_input")
        debridKeyInput.setText(sharedPref.getString(Favoritism.PREF_KEY, ""))
        debridKeyInput.makeTvCompatible()

        // ===== FAVORITES =====
        val favUrlInput = root.findView<EditText>("favorites_url_input")
        favUrlInput.setText(sharedPref.getString(Favoritism.PREF_FAV_URL, Favoritism.DEFAULT_FAV_URL))
        favUrlInput.makeTvCompatible()

        val favLocalInput = root.findView<EditText>("favorites_local_input")
        favLocalInput.setText(sharedPref.getString(Favoritism.PREF_FAV_LOCAL, ""))
        favLocalInput.makeTvCompatible()

        val checkBtn = root.findView<Button>("check_btn")
        checkBtn.makeTvCompatible()
        checkBtn.setOnClickListener {
            checkFavorites(favUrlInput.text.toString().trim(), favLocalInput.text.toString())
        }

        // ===== SAVE =====
        val saveBtn = root.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            sharedPref.edit {
                putString(Favoritism.PREF_SERVICE, debridSpinner.selectedItem?.toString() ?: "")
                putString(Favoritism.PREF_KEY, debridKeyInput.text.toString().trim())
                putString(Favoritism.PREF_FAV_URL, favUrlInput.text.toString().trim())
                putString(Favoritism.PREF_FAV_LOCAL, favLocalInput.text.toString())
                putLong(Favoritism.PREF_FAV_REV, System.currentTimeMillis())
            }
            showToast("Saved")
            dismiss()
        }

        // ===== RESET =====
        val resetBtn = root.findView<View>("delete_img")
        resetBtn.makeTvCompatible()
        resetBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset")
                .setMessage("This will delete all saved settings, including your extra favorites.")
                .setPositiveButton("Reset") { _, _ ->
                    sharedPref.edit().clear().commit()
                    debridSpinner.setSelection(0, false)
                    debridKeyInput.text.clear()
                    favUrlInput.setText(Favoritism.DEFAULT_FAV_URL)
                    favLocalInput.text.clear()
                    showToast("Settings reset")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return root
    }

    /** Parses both lists (without saving) and shows what was understood. */
    private fun checkFavorites(url: String, localText: String) {
        showToast("Checking…")
        scope.launch {
            val local = FavoriteList.parse(localText)
            val remoteText = if (url.isEmpty()) null else withContext(Dispatchers.IO) { fetchFavoritesText(url) }
            val remote = remoteText?.let { FavoriteList.parse(it) }

            val msg = buildString {
                append("Extra favorites: ${local.rules.size} entries")
                if (local.problems.isNotEmpty()) append(", ${local.problems.size} problem(s)")
                append("\n")
                when {
                    url.isEmpty() -> append("URL list: not set\n")
                    remote == null -> append("URL list: couldn't download it\n")
                    else -> {
                        append("URL list: ${remote.rules.size} entries")
                        if (remote.problems.isNotEmpty()) append(", ${remote.problems.size} problem(s)")
                        append("\n")
                    }
                }
                if (local.problems.isNotEmpty()) {
                    append("\nExtra favorites problems:\n")
                    local.problems.take(15).forEach { append("• $it\n") }
                }
                if (remote != null && remote.problems.isNotEmpty()) {
                    append("\nURL list problems:\n")
                    remote.problems.take(15).forEach { append("• $it\n") }
                }
            }

            if (!isAdded) return@launch
            AlertDialog.Builder(requireContext())
                .setTitle("Favorites check")
                .setMessage(msg.trim())
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroyView() {
        scope.cancel()
        super.onDestroyView()
    }
}
