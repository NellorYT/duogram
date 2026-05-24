package com.latsudev.duogram

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment

class ChatsFragment : Fragment(R.layout.fragment_chats) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.openSavedMessagesButton).setOnClickListener {
            startActivity(Intent(requireContext(), SavedMessagesActivity::class.java))
        }
    }
}
