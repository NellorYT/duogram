package com.latsudev.duogram

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class SavedMessagesActivity : AppCompatActivity() {

    private val securityPrefs by lazy { getSharedPreferences("duogram_security", MODE_PRIVATE) }
    private val notes = mutableListOf<ChatActivity.MessageUi>()
    private lateinit var adapter: ChatActivity.ChatMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_saved_messages)

        enforceSavedMessagesLock()

        val savedId = CryptoManager.getSavedMessagesId()
        findViewById<TextView>(R.id.savedTitle).text = "Saved Messages ($savedId)"

        val recycler = findViewById<RecyclerView>(R.id.savedRecycler)
        adapter = ChatActivity.ChatMessageAdapter(notes)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val input = findViewById<EditText>(R.id.savedInput)
        findViewById<ImageButton>(R.id.savedSendButton).setOnClickListener {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@setOnClickListener
            lifecycleScope.launch {
                val cipher = CryptoManager.encryptSavedNote(text)
                AppDatabase.get(this@SavedMessagesActivity).duogramDao().upsertSavedNote(
                    SavedNote(
                        noteId = 0,
                        chatId = savedId,
                        encryptedPayload = cipher,
                        createdAt = System.currentTimeMillis(),
                        isPinned = false
                    )
                )
                notes.add(ChatActivity.MessageUi("🔐 ${text.take(40)}", true))
                adapter.notifyItemInserted(notes.lastIndex)
                input.setText("")
            }
        }
    }

    private fun enforceSavedMessagesLock() {
        val pin = securityPrefs.getString("saved_pin", null)
        if (pin.isNullOrBlank() && !securityPrefs.getBoolean("saved_bio", false)) return

        val canBio = securityPrefs.getBoolean("saved_bio", false) &&
            BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

        if (canBio) {
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!pin.isNullOrBlank()) showPinDialog(pin)
                    }

                    override fun onAuthenticationFailed() {
                        if (!pin.isNullOrBlank()) showPinDialog(pin)
                    }
                }
            ).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Saved Messages")
                    .setSubtitle("Подтвердите доступ")
                    .setNegativeButtonText("PIN")
                    .build()
            )
        } else if (!pin.isNullOrBlank()) {
            showPinDialog(pin)
        }
    }

    private fun showPinDialog(pin: String) {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this)
            .setTitle("PIN для Избранного")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                if (input.text?.toString() != pin) finish()
            }
            .show()
    }
}
