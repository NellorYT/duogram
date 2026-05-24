package com.latsudev.duogram

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsScreen : Fragment(R.layout.fragment_settings_screen) {

    private val prefs by lazy { requireContext().getSharedPreferences("duogram_prefs", android.content.Context.MODE_PRIVATE) }
    private val securityPrefs by lazy { requireContext().getSharedPreferences("duogram_security", android.content.Context.MODE_PRIVATE) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val transportGroup = view.findViewById<RadioGroup>(R.id.transportGroup)
        val vpsHost = view.findViewById<EditText>(R.id.vpsHostInput)
        val vpsToken = view.findViewById<EditText>(R.id.vpsTokenInput)
        val socksHost = view.findViewById<EditText>(R.id.socksHostInput)
        val socksPort = view.findViewById<EditText>(R.id.socksPortInput)
        val socksLogin = view.findViewById<EditText>(R.id.socksLoginInput)
        val socksPassword = view.findViewById<EditText>(R.id.socksPasswordInput)

        val pinInput = view.findViewById<EditText>(R.id.pinInput)
        val appBioSwitch = view.findViewById<Switch>(R.id.appBioSwitch)
        val wipeSwitch = view.findViewById<Switch>(R.id.selfDestructSwitch)
        val savedPinInput = view.findViewById<EditText>(R.id.savedPinInput)
        val savedBioSwitch = view.findViewById<Switch>(R.id.savedBioSwitch)

        restoreTransportSelection(transportGroup)

        view.findViewById<Button>(R.id.saveTransportButton).setOnClickListener {
            val selected = when (transportGroup.checkedRadioButtonId) {
                R.id.transportWebrtc -> TransportMode.WEBRTC
                R.id.transportVps -> TransportMode.VPS
                R.id.transportSocks -> TransportMode.SOCKS
                R.id.transportTor -> TransportMode.TOR
                R.id.transportLan -> TransportMode.LAN
                else -> TransportMode.WEBRTC
            }
            prefs.edit()
                .putString("active_transport", selected.name)
                .putString("vps_host", vpsHost.text.toString())
                .putString("vps_token", vpsToken.text.toString())
                .putString("socks_host", socksHost.text.toString())
                .putString("socks_port", socksPort.text.toString())
                .putString("socks_login", socksLogin.text.toString())
                .putString("socks_password", socksPassword.text.toString())
                .apply()

            viewLifecycleOwner.lifecycleScope.launch {
                val dao = AppDatabase.get(requireContext()).duogramDao()
                val profile = dao.getUserProfile()
                if (profile != null) dao.upsertUserProfile(profile.copy(activeTransport = selected.name))
            }
            Toast.makeText(requireContext(), "Настройки транспорта сохранены", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.testConnectionButton).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val selected = when (transportGroup.checkedRadioButtonId) {
                    R.id.transportVps -> TransportMode.VPS
                    R.id.transportSocks -> TransportMode.SOCKS
                    R.id.transportTor -> TransportMode.TOR
                    R.id.transportLan -> TransportMode.LAN
                    else -> TransportMode.WEBRTC
                }
                val ok = when (selected) {
                    TransportMode.VPS -> VpsRelayConnector(vpsHost.text.toString(), vpsToken.text.toString()).healthCheck()
                    TransportMode.SOCKS, TransportMode.TOR -> {
                        SocksConnector.testConnection(
                            SocksConfig(
                                host = socksHost.text.toString(),
                                port = socksPort.text.toString().toIntOrNull() ?: 1080,
                                username = socksLogin.text.toString().ifBlank { null },
                                password = socksPassword.text.toString().ifBlank { null }
                            ),
                            "https://example.com"
                        )
                    }
                    TransportMode.LAN -> true
                    TransportMode.WEBRTC -> true
                }
                Toast.makeText(requireContext(), if (ok) "Подключение успешно" else "Подключение не удалось", Toast.LENGTH_LONG).show()
            }
        }

        view.findViewById<Button>(R.id.saveSecurityButton).setOnClickListener {
            securityPrefs.edit()
                .putString("app_pin", pinInput.text.toString().ifBlank { null })
                .putBoolean("app_bio", appBioSwitch.isChecked)
                .putBoolean("wipe_after_5", wipeSwitch.isChecked)
                .putString("saved_pin", savedPinInput.text.toString().ifBlank { null })
                .putBoolean("saved_bio", savedBioSwitch.isChecked)
                .apply()
            Toast.makeText(requireContext(), "Безопасность обновлена", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.savePrivacyButton).setOnClickListener {
            val avatarPrivacy = if (view.findViewById<CheckBox>(R.id.privacyAvatarAll).isChecked) "EVERYONE" else "FRIENDS_ONLY"
            val linksPrivacy = if (view.findViewById<CheckBox>(R.id.privacyLinksOff).isChecked) "NOBODY" else "FRIENDS_ONLY"
            prefs.edit().putString("privacy_avatar", avatarPrivacy).putString("privacy_links", linksPrivacy).apply()
            Toast.makeText(requireContext(), "Приватность сохранена", Toast.LENGTH_SHORT).show()
        }

        loadSavedValues(vpsHost, vpsToken, socksHost, socksPort, socksLogin, socksPassword, pinInput, appBioSwitch, wipeSwitch, savedPinInput, savedBioSwitch)
    }

    private fun restoreTransportSelection(group: RadioGroup) {
        when (prefs.getString("active_transport", TransportMode.WEBRTC.name)) {
            TransportMode.WEBRTC.name -> group.check(R.id.transportWebrtc)
            TransportMode.VPS.name -> group.check(R.id.transportVps)
            TransportMode.SOCKS.name -> group.check(R.id.transportSocks)
            TransportMode.TOR.name -> group.check(R.id.transportTor)
            TransportMode.LAN.name -> group.check(R.id.transportLan)
        }
    }

    private fun loadSavedValues(
        vpsHost: EditText,
        vpsToken: EditText,
        socksHost: EditText,
        socksPort: EditText,
        socksLogin: EditText,
        socksPassword: EditText,
        pinInput: EditText,
        appBioSwitch: Switch,
        wipeSwitch: Switch,
        savedPinInput: EditText,
        savedBioSwitch: Switch
    ) {
        vpsHost.setText(prefs.getString("vps_host", ""))
        vpsToken.setText(prefs.getString("vps_token", ""))
        socksHost.setText(prefs.getString("socks_host", ""))
        socksPort.setText(prefs.getString("socks_port", "1080"))
        socksLogin.setText(prefs.getString("socks_login", ""))
        socksPassword.setText(prefs.getString("socks_password", ""))

        pinInput.setText(securityPrefs.getString("app_pin", ""))
        appBioSwitch.isChecked = securityPrefs.getBoolean("app_bio", false)
        wipeSwitch.isChecked = securityPrefs.getBoolean("wipe_after_5", false)
        savedPinInput.setText(securityPrefs.getString("saved_pin", ""))
        savedBioSwitch.isChecked = securityPrefs.getBoolean("saved_bio", false)
    }
}
