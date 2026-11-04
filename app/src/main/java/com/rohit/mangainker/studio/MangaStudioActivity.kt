package com.rohit.mangainker.studio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.rohit.mangainker.core.SessionStore
import java.util.concurrent.Executors

@SuppressLint("SetTextI18n")
class MangaStudioActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var editor: MangaStudioView
    private lateinit var status: TextView
    private lateinit var panelSpinner: Spinner
    private lateinit var toneSpinner: Spinner
    private lateinit var tonePanelSpinner: Spinner
    private lateinit var densitySeek: SeekBar
    private lateinit var opacitySeek: SeekBar
    private lateinit var gutterSeek: SeekBar
    private lateinit var borderSeek: SeekBar
    private lateinit var typeSpinner: Spinner
    private lateinit var letteringInput: EditText
    private var selectedTone: TonePattern? = null
    private var exportBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SessionStore.sourceBitmap
        if (page == null || page.isRecycled) {
            Toast.makeText(this, "Import an outlined page in Manga Inker first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        buildScreen(page)
    }

    private fun buildScreen(page: Bitmap) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 18, 24))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(30))
        }
        content.addView(text("MANGA STUDIO  /  PAGE FINISHER", 12f, Color.rgb(112, 216, 232), true))
        content.addView(text("Panels, tones & lettering", 25f, Color.WHITE, true).apply {
            setPadding(0, dp(10), 0, dp(4))
        })
        content.addView(text("Add non-destructive comic finishing elements to your outlined page. Tap the preview to place lettering, drag it to reposition, then export a flattened PNG when ready.", 13f, Color.rgb(166, 180, 195), false).apply {
            setPadding(0, 0, 0, dp(12))
        })

        editor = MangaStudioView(this).apply {
            setPage(page)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(460)).apply {
                bottomMargin = dp(12)
            }
        }
        content.addView(editor)
        status = text("Preview ready · original artwork stays on this device", 12f, Color.rgb(152, 170, 189), false)
        content.addView(status)

        content.addView(section("PANEL LAYOUT"))
        panelSpinner = spinner(PanelPreset.values().map { it.label })
        content.addView(panelSpinner)
        gutterSeek = seek(0, 60, 18)
        content.addView(sliderLabel("Panel gutter", "Changes the white space between panels."))
        content.addView(gutterSeek)
        content.addView(sliderLabel("Panel border thickness", "Adjusts the black frame lines added by this studio."))
        borderSeek = seek(1, 24, 5)
        content.addView(borderSeek)
        panelSpinner.onItemSelectedListener = SimpleSelection { index ->
            editor.setPanelPreset(PanelPreset.values()[index])
            refreshTonePanelOptions()
        }
        gutterSeek.setOnSeekBarChangeListener(simpleSeek { editor.setGutter(it) })
        borderSeek.setOnSeekBarChangeListener(simpleSeek { editor.setBorderWidth(it) })

        content.addView(section("SCREENTONE & EFFECTS"))
        toneSpinner = spinner(listOf("Tone off") + TonePattern.values().map { it.label })
        content.addView(toneSpinner)
        tonePanelSpinner = spinner(listOf("All panels"))
        content.addView(tonePanelSpinner)
        content.addView(sliderLabel("Tone density", "Higher density creates a tighter dot or line pattern."))
        densitySeek = seek(6, 64, 24)
        content.addView(densitySeek)
        content.addView(sliderLabel("Tone opacity", "Keep tones subtle so the line art remains readable."))
        opacitySeek = seek(0, 60, 18)
        content.addView(opacitySeek)
        toneSpinner.onItemSelectedListener = SimpleSelection { index ->
            selectedTone = if (index == 0) null else TonePattern.values()[index - 1]
            applyTone()
        }
        tonePanelSpinner.onItemSelectedListener = SimpleSelection { applyTone() }
        densitySeek.setOnSeekBarChangeListener(simpleSeek { applyTone() })
        opacitySeek.setOnSeekBarChangeListener(simpleSeek { applyTone() })

        content.addView(section("LETTERING & SOUND EFFECTS"))
        typeSpinner = spinner(LetteringType.values().map { it.label })
        content.addView(typeSpinner)
        letteringInput = EditText(this).apply {
            hint = "Write dialogue, narration or SFX"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(120, 135, 151))
            setSingleLine(false)
            minLines = 2
            maxLines = 4
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(31, 39, 50))
        }
        content.addView(letteringInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(90)))
        content.addView(actionButton("Place lettering on page") {
            val value = letteringInput.text?.toString().orEmpty().trim()
            if (value.isBlank()) {
                toast("Enter some text first.")
            } else {
                val type = LetteringType.values()[typeSpinner.selectedItemPosition]
                editor.setPendingLettering(value, type)
                status.text = "Tap a position on the page to place ${type.label.lowercase()}."
                toast("Tap the page where you want the lettering.")
            }
        })
        val letteringActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        letteringActions.addView(actionButton("Undo last item") { if (!editor.removeLastLettering()) toast("There is no lettering to remove.") }, LinearLayout.LayoutParams(0, dp(48), 1f))
        letteringActions.addView(actionButton("Clear lettering") { editor.clearLettering(); status.text = "Lettering cleared." }, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(letteringActions)

        content.addView(section("EXPORT"))
        content.addView(text("Export creates a new PNG copy. It does not overwrite your source outline or alter your ibisPaint project.", 12f, Color.rgb(152, 170, 189), false).apply {
            setPadding(0, 0, 0, dp(10))
        })
        content.addView(actionButton("Export finished page as PNG") { exportPage() })
        content.addView(actionButton("Back to Manga Inker") { finish() })

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        refreshTonePanelOptions()
        applyTone()
    }

    private fun refreshTonePanelOptions() {
        if (!::tonePanelSpinner.isInitialized || !::panelSpinner.isInitialized) return
        val selectedPreset = PanelPreset.values()[panelSpinner.selectedItemPosition]
        val count = PanelLayout.rectangles(selectedPreset, gutterSeek.progress / 1000f).size
        val options = if (selectedPreset == PanelPreset.KEEP_ORIGINAL) {
            listOf("Whole page")
        } else {
            listOf("All panels") + (1..count).map { "Panel $it" }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        tonePanelSpinner.adapter = adapter
        applyTone()
    }

    private fun applyTone() {
        if (!::editor.isInitialized || !::toneSpinner.isInitialized || !::tonePanelSpinner.isInitialized) return
        val panelIndex = tonePanelSpinner.selectedItemPosition - 1
        editor.setTone(selectedTone, densitySeek.progress + 6, opacitySeek.progress, panelIndex)
        status.text = if (selectedTone == null) "Screentone off" else "${selectedTone?.label} · ${if (panelIndex < 0) "all panels" else "panel ${panelIndex + 1}"}"
    }

    private fun exportPage() {
        status.text = "Preparing PNG locally…"
        val bitmap = editor.exportBitmap() ?: run {
            toast("There is no page to export.")
            return
        }
        exportBitmap?.takeIf { !it.isRecycled }?.recycle()
        exportBitmap = bitmap
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
            putExtra(Intent.EXTRA_TITLE, "manga-page-finished.png")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    @Deprecated("Uses the Android system document picker to save the exported PNG locally.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT) return
        val uri = data?.data
        val bitmap = exportBitmap
        exportBitmap = null
        if (resultCode != RESULT_OK || uri == null || bitmap == null) {
            bitmap?.takeIf { !it.isRecycled }?.recycle()
            status.text = "Export cancelled."
            return
        }
        // Ownership moves to the worker before leaving this activity result callback.
        // onDestroy therefore cannot recycle the bitmap while compression is running.
        worker.execute {
            var failure: String? = null
            try {
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw IllegalStateException("PNG encoder could not write the page.")
                    }
                } ?: throw IllegalStateException("Android could not open the selected save location.")
            } catch (error: Exception) {
                failure = error.message ?: "Could not export the page."
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            if (!isFinishing && !isDestroyed) runOnUiThread {
                if (failure == null) {
                    status.text = "PNG exported. Your original outline is unchanged."
                    toast("Finished page saved.")
                } else {
                    status.text = failure
                    toast(failure ?: "Could not export the page.")
                }
            }
        }
    }

    private fun section(value: String): TextView = text(value, 11f, Color.rgb(112, 216, 232), true).apply {
        setPadding(0, dp(20), 0, dp(8))
        letterSpacing = 0.09f
    }

    private fun sliderLabel(title: String, detail: String): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        column.addView(text(title, 13f, Color.WHITE, true))
        column.addView(text(detail, 11f, Color.rgb(142, 157, 174), false))
        return column
    }

    private fun spinner(items: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MangaStudioActivity, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun seek(minimum: Int, maximum: Int, initial: Int): SeekBar = SeekBar(this).apply {
        this.max = maximum - minimum
        progress = initial - minimum
        tag = minimum
    }

    private fun simpleSeek(onValue: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser && seekBar != null) onValue(progress + (seekBar.tag as? Int ?: 0))
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        minHeight = dp(44)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
            bottomMargin = dp(6)
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        worker.shutdown()
        exportBitmap?.takeIf { !it.isRecycled }?.recycle()
        exportBitmap = null
        super.onDestroy()
    }

    private class SimpleSelection(private val action: (Int) -> Unit) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }

    companion object { private const val REQUEST_EXPORT = 52 }
}
