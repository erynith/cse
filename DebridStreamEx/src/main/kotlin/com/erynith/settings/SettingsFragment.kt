package com.erynith.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.erynith.BuildConfig
import com.erynith.DebridStreamExProvider

class SettingsFragment(
    private val plugin: DebridStreamExProvider,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")

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

        // ===== DEBRID SERVICES =====
        val debridSpinner = root.findView<Spinner>("debrid_service_spinner")
        val debridServices = listOf(
            "Real-Debrid", "TorBox", "All-Debrid", "Debrid-Link",
            "Premiumize", "Debrider", "EasyDebrid", "Offcloud", "PikPak"
        )
        debridSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, debridServices).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        val savedDebrid = sharedPref.getString("debrid_service", null)
        if (savedDebrid != null) {
            val pos = debridServices.indexOf(savedDebrid)
            if (pos >= 0) debridSpinner.setSelection(pos)
        }
        debridSpinner.makeTvCompatible()

        val debridKeyInput = root.findView<EditText>("debrid_key_input")
        debridKeyInput.setText(sharedPref.getString("debrid_key", ""))
        debridKeyInput.makeTvCompatible()

        // ===== SAVE =====
        val saveBtn = root.findView<ImageView>("save")
        saveBtn.setImageDrawable(getDrawable("save_icon"))
        saveBtn.makeTvCompatible()
        saveBtn.setOnClickListener {
            sharedPref.edit {
                putString("debrid_service", debridSpinner.selectedItem?.toString() ?: "")
                putString("debrid_key", debridKeyInput.text.toString())
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Restart Required")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them?")
                .setPositiveButton("Yes") { _, _ ->
                    showToast("Saved and Restarting...")
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No") { dialog, _ ->
                    showToast("Saved. Restart later to apply changes.")
                    dialog.dismiss()
                    dismiss()
                }
                .show()
        }

        // ===== RESET =====
        val resetBtn = root.findView<View>("delete_img")
        resetBtn.makeTvCompatible()
        resetBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset")
                .setMessage("This will delete all saved settings.")
                .setPositiveButton("Reset") { _, _ ->
                    sharedPref.edit().clear().commit()
                    debridSpinner.setSelection(0, false)
                    debridKeyInput.text.clear()
                    restartApp()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

    private fun restartApp() {
        val context = requireContext().applicationContext
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        if (componentName != null) {
            val restartIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(restartIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
