package com.pasban.keyboard

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream

class PasbanImeService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private lateinit var candidatesView: RecyclerView
    private val adapter = CandidateAdapter { picked -> applyCandidate(picked) }

    private var db: SQLiteDatabase? = null

    override fun onCreate() {
        super.onCreate()
        db = openDbFromAssets()
    }

    override fun onDestroy() {
        db?.close()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.input_view, null)
        keyboardView = v.findViewById(R.id.keyboardView)
        candidatesView = v.findViewById(R.id.candidates)

        keyboard = Keyboard(this, R.xml.keyboard)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)

        candidatesView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        candidatesView.adapter = adapter

        return v
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        hideCandidates()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
                hideCandidates()
            }
            10 -> { // Enter
                ic.commitText("\n", 1)
                hideCandidates()
            }
            32 -> { // Space
                ic.commitText(" ", 1)
                // After finishing a word/phrase, try to suggest replacement
                suggestForTail()
            }
            else -> {
                val ch = primaryCode.toChar().toString()
                ic.commitText(ch, 1)
                hideCandidates()
            }
        }
    }

    private fun suggestForTail() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: return
        val norm = faNormalize(before)
        val tokens = norm.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return

        // Try up to last 4 tokens as phrases (longest-first)
        val max = minOf(4, tokens.size)
        for (k in max downTo 1) {
            val phrase = tokens.takeLast(k).joinToString(" ")
            val suggestions = lookupPhrase(phrase)
            if (suggestions.isNotEmpty()) {
                showCandidates(phrase, suggestions)
                return
            }
        }
    }

    private var activePhrase: String? = null

    private fun showCandidates(phrase: String, suggestions: List<String>) {
        activePhrase = phrase
        adapter.submit(suggestions)
        candidatesView.visibility = View.VISIBLE
    }

    private fun hideCandidates() {
        activePhrase = null
        adapter.submit(emptyList())
        candidatesView.visibility = View.GONE
    }

    private fun applyCandidate(picked: String) {
        val phrase = activePhrase ?: return
        val ic = currentInputConnection ?: return

        // Delete phrase length (including spaces inside phrase) from before cursor.
        // Note: we assume cursor is right after a space we just inserted.
        val toDelete = phrase.length + 1 // +1 for that final space
        ic.deleteSurroundingText(toDelete, 0)
        ic.commitText(picked + " ", 1)
        hideCandidates()
    }

    private fun lookupPhrase(phrase: String): List<String> {
        val database = db ?: return emptyList()
        val cursor: Cursor = database.rawQuery(
            "SELECT parsi FROM words WHERE word = ? LIMIT 1",
            arrayOf(phrase)
        )
        cursor.use {
            if (!it.moveToFirst()) return emptyList()
            val parsi = it.getString(0) ?: return emptyList()
            return parsi.split("،").map { s -> s.trim() }.filter { s -> s.isNotBlank() }.distinct().take(8)
        }
    }

    private fun openDbFromAssets(): SQLiteDatabase? {
        return try {
            val outFile = File(filesDir, "pasban.db")
            if (!outFile.exists()) {
                assets.open("pasban.db").use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            SQLiteDatabase.openDatabase(outFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            null
        }
    }

    private fun faNormalize(s: String): String {
        var x = s
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('\u0640', ' ')
            .replace(Regex("[\u064B-\u065F\u0670]"), "")
            .replace('\u200C', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        return x
    }

    // Unused callbacks
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
