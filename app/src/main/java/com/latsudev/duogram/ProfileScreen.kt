package com.latsudev.duogram

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ProfileScreen : Fragment(R.layout.fragment_profile_screen) {

    private val prefs by lazy { requireContext().getSharedPreferences("duogram_prefs", android.content.Context.MODE_PRIVATE) }

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && view != null) {
            view?.findViewById<ShapeableImageView>(R.id.avatarView)?.load(uri)
            viewLifecycleOwner.lifecycleScope.launch {
                val dao = AppDatabase.get(requireContext()).duogramDao()
                val profile = dao.getUserProfile() ?: return@launch
                dao.upsertUserProfile(profile.copy(avatarUri = uri.toString()))
            }
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val avatar = view.findViewById<ShapeableImageView>(R.id.avatarView)
        val name = view.findViewById<EditText>(R.id.profileName)
        val username = view.findViewById<TextView>(R.id.profileUsername)
        val bio = view.findViewById<EditText>(R.id.profileBio)
        val links = view.findViewById<EditText>(R.id.profileLinks)
        val activeTransport = view.findViewById<TextView>(R.id.profileTransportValue)

        val visibilityOptions = listOf("Всем", "Только друзьям", "Никому")
        val avatarVisibility = view.findViewById<Spinner>(R.id.avatarVisibilitySpinner)
        val nameVisibility = view.findViewById<Spinner>(R.id.nameVisibilitySpinner)
        val bioVisibility = view.findViewById<Spinner>(R.id.bioVisibilitySpinner)
        val linksVisibility = view.findViewById<Spinner>(R.id.linksVisibilitySpinner)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, visibilityOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        listOf(avatarVisibility, nameVisibility, bioVisibility, linksVisibility).forEach { it.adapter = adapter }

        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.get(requireContext()).duogramDao()
            val profile = dao.getUserProfile() ?: UserProfile(
                id = 1,
                displayName = "Duogram User",
                username = CryptoManager.generateUsernameFromPublicKey(),
                avatarUri = null,
                bio = "",
                links = emptyList(),
                activeTransport = prefs.getString("active_transport", TransportMode.WEBRTC.name) ?: TransportMode.WEBRTC.name
            )
            if (dao.getUserProfile() == null) dao.upsertUserProfile(profile)

            name.setText(profile.displayName)
            username.text = profile.username
            bio.setText(profile.bio)
            links.setText(profile.links.joinToString("\n"))
            activeTransport.text = profile.activeTransport
            profile.avatarUri?.let { avatar.load(it) }

            avatarVisibility.setSelection(profile.avatarVisibility.index())
            nameVisibility.setSelection(profile.nameVisibility.index())
            bioVisibility.setSelection(profile.bioVisibility.index())
            linksVisibility.setSelection(profile.linksVisibility.index())
        }

        view.findViewById<Button>(R.id.changeAvatarButton).setOnClickListener { avatarPicker.launch("image/*") }

        view.findViewById<Button>(R.id.saveProfileButton).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val dao = AppDatabase.get(requireContext()).duogramDao()
                val current = dao.getUserProfile() ?: return@launch
                val updated = current.copy(
                    displayName = name.text.toString().trim().ifBlank { current.displayName },
                    bio = bio.text.toString().take(200),
                    links = links.text.toString().lines().map { it.trim() }.filter { it.isNotBlank() }.take(5),
                    avatarVisibility = PrivacyLevel.fromIndex(avatarVisibility.selectedItemPosition),
                    nameVisibility = PrivacyLevel.fromIndex(nameVisibility.selectedItemPosition),
                    bioVisibility = PrivacyLevel.fromIndex(bioVisibility.selectedItemPosition),
                    linksVisibility = PrivacyLevel.fromIndex(linksVisibility.selectedItemPosition)
                )
                dao.upsertUserProfile(updated)
                Toast.makeText(requireContext(), "Профиль сохранён", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.resetUsernameButton).setOnClickListener {
            val now = System.currentTimeMillis()
            val lastReset = prefs.getLong("username_last_reset", 0L)
            val days30 = 30L * 24 * 60 * 60 * 1000
            if (now - lastReset < days30) {
                Toast.makeText(requireContext(), "Username можно менять раз в 30 дней", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val dao = AppDatabase.get(requireContext()).duogramDao()
                val current = dao.getUserProfile() ?: return@launch
                val next = current.copy(username = CryptoManager.generateUsernameFromPublicKey())
                dao.upsertUserProfile(next)
                prefs.edit().putLong("username_last_reset", now).apply()
                username.text = next.username
            }
        }

        view.findViewById<Button>(R.id.exportProfileButton).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val profile = AppDatabase.get(requireContext()).duogramDao().getUserProfile() ?: return@launch
                val payload = JSONObject().apply {
                    put("displayName", profile.displayName)
                    put("username", profile.username)
                    put("avatarUri", profile.avatarUri)
                    put("bio", profile.bio)
                    put("links", JSONArray(profile.links))
                    put("activeTransport", profile.activeTransport)
                    put("privacy", JSONObject().apply {
                        put("avatar", profile.avatarVisibility)
                        put("name", profile.nameVisibility)
                        put("bio", profile.bioVisibility)
                        put("links", profile.linksVisibility)
                    })
                }
                startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, payload.toString(2))
                })
            }
        }
    }

    private fun String.index(): Int = when (this) {
        PrivacyLevel.EVERYONE.name -> 0
        PrivacyLevel.FRIENDS_ONLY.name -> 1
        PrivacyLevel.NOBODY.name -> 2
        else -> 0
    }
}
