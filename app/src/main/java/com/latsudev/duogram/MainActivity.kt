package com.latsudev.duogram

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private val prefs by lazy { getSharedPreferences("duogram_prefs", MODE_PRIVATE) }
    private val securityPrefs by lazy { getSharedPreferences("duogram_security", MODE_PRIVATE) }

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) prefs.edit().putString("pending_avatar_uri", uri.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_main)

        startService(Intent(this, NetworkMonitorService::class.java))
        ensureFirstRunSetup()
        checkAppUnlock()
        setupBottomNavigation()
        setupFab()
    }

    private fun ensureFirstRunSetup() {
        if (prefs.getBoolean("initialized", false)) return

        CryptoManager.generateIdentityIfNeeded()
        val profileView = layoutInflater.inflate(R.layout.dialog_profile_setup, null)
        val nameInput = profileView.findViewById<EditText>(R.id.setupNameInput)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Первый запуск / First run")
            .setView(profileView)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty().ifBlank { "Duogram User" }
                val avatarUri = prefs.getString("pending_avatar_uri", null)
                val username = CryptoManager.generateUsernameFromPublicKey()

                lifecycleScope.launch {
                    AppDatabase.get(this@MainActivity).duogramDao().upsertUserProfile(
                        UserProfile(
                            id = 1,
                            displayName = name,
                            username = username,
                            avatarUri = avatarUri,
                            bio = "",
                            links = emptyList(),
                            activeTransport = TransportMode.WEBRTC.name
                        )
                    )
                    prefs.edit().putBoolean("initialized", true).remove("pending_avatar_uri").apply()
                }
            }
            .setNeutralButton("Выбрать аватар") { _, _ -> avatarPicker.launch("image/*") }
            .setCancelable(false)
            .show()
    }

    private fun setupBottomNavigation() {
        val bottom = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottom.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_chats -> switchFragment(ChatsFragment())
                R.id.nav_contacts -> switchFragment(ContactsFragment())
                R.id.nav_settings -> switchFragment(SettingsScreen())
            }
            true
        }
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            bottom.selectedItemId = R.id.nav_chats
        }
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.mainFab).setOnClickListener { anchor ->
            PopupMenu(this, anchor).apply {
                menu.add(Menu.NONE, 1, 1, "Новый чат")
                menu.add(Menu.NONE, 2, 2, "Новая группа")
                menu.add(Menu.NONE, 3, 3, "Добавить контакт")
                menu.add(Menu.NONE, 4, 4, "Избранное")
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        1 -> startActivity(Intent(this@MainActivity, ChatActivity::class.java))
                        2 -> Toast.makeText(this@MainActivity, "TODO: Создание группы", Toast.LENGTH_SHORT).show()
                        3 -> Toast.makeText(this@MainActivity, "TODO: Добавление контакта", Toast.LENGTH_SHORT).show()
                        4 -> startActivity(Intent(this@MainActivity, SavedMessagesActivity::class.java))
                    }
                    true
                }
                show()
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragmentContainer, fragment).commit()
    }

    private fun checkAppUnlock() {
        val pin = securityPrefs.getString("app_pin", null)
        if (pin.isNullOrBlank()) return

        val canUseBiometric = securityPrefs.getBoolean("app_bio", false) &&
            BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

        if (canUseBiometric) {
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationFailed() = handleFailedUnlock()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = showPinDialog(pin)
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Разблокировка Duogram")
                    .setSubtitle("Подтвердите биометрию")
                    .setNegativeButtonText("Использовать PIN")
                    .build()
            )
        } else showPinDialog(pin)
    }

    private fun showPinDialog(pin: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Введите PIN")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                if (input.text?.toString() == pin) {
                    securityPrefs.edit().putInt("fail_count", 0).apply()
                } else {
                    handleFailedUnlock()
                    showPinDialog(pin)
                }
            }
            .show()
    }

    private fun handleFailedUnlock() {
        val count = securityPrefs.getInt("fail_count", 0) + 1
        securityPrefs.edit().putInt("fail_count", count).apply()
        if (securityPrefs.getBoolean("wipe_after_5", false) && count >= 5) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.destroy(this@MainActivity)
                    filesDir.deleteRecursively()
                    cacheDir.deleteRecursively()
                }
                getSharedPreferences("duogram_prefs", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("duogram_security", MODE_PRIVATE).edit().clear().apply()
                Toast.makeText(this@MainActivity, "Данные удалены", Toast.LENGTH_LONG).show()
                finishAffinity()
            }
        }
    }
}
