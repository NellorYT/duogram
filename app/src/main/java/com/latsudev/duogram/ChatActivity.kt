package com.latsudev.duogram

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val messages = mutableListOf<MessageUi>()
    private lateinit var adapter: ChatMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_chat)

        val contactName = intent.getStringExtra("contact_name") ?: "Контакт"
        val transport = intent.getStringExtra("contact_transport") ?: TransportMode.WEBRTC.name

        findViewById<TextView>(R.id.chatTitle).text = contactName
        findViewById<TextView>(R.id.chatTransportIcon).text = transport.toEmoji()

        val recycler = findViewById<RecyclerView>(R.id.messagesRecycler)
        adapter = ChatMessageAdapter(messages)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val input = findViewById<EditText>(R.id.messageInput)
        findViewById<ImageButton>(R.id.sendButton).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            val encrypted = CryptoManager.encryptForDirectMessage(text.toByteArray())
            messages.add(MessageUi(text = "📨 ${encrypted.take(14)}...", outgoing = true))
            adapter.notifyItemInserted(messages.lastIndex)
            input.setText("")
        }

        findViewById<ImageButton>(R.id.photoButton).setOnClickListener {
            Toast.makeText(this, "TODO: добавить отправку фото CameraX", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_pin_chat -> {
                val contactId = intent.getStringExtra("contact_id") ?: return true
                lifecycleScope.launch {
                    val dao = AppDatabase.get(this@ChatActivity).duogramDao()
                    val pinnedCount = dao.countPinnedContacts()
                    if (pinnedCount >= 5) {
                        Toast.makeText(this@ChatActivity, "Максимум 5 закреплённых чатов", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    dao.pinContact(contactId, pinnedCount + 1)
                    Toast.makeText(this@ChatActivity, "Чат закреплён", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            R.id.action_clear_history -> {
                messages.clear()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "История очищена локально", Toast.LENGTH_SHORT).show()
                return true
            }

            R.id.action_delete_chat -> {
                lifecycleScope.launch {
                    val chatId = intent.getStringExtra("chat_id") ?: return@launch
                    AppDatabase.get(this@ChatActivity).duogramDao().deleteMessagesByChat(chatId)
                    Toast.makeText(this@ChatActivity, "Чат удалён", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return true
            }

            R.id.action_export_keys -> {
                val bundle = CryptoManager.exportPublicBundle()
                Toast.makeText(this, bundle.take(64), Toast.LENGTH_LONG).show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun String.toEmoji(): String = when (this) {
        TransportMode.WEBRTC.name -> "🌍"
        TransportMode.VPS.name -> "🖥️"
        TransportMode.SOCKS.name -> "🔌"
        TransportMode.TOR.name -> "🧅"
        TransportMode.LAN.name -> "📶"
        else -> "🌍"
    }

    data class MessageUi(val text: String, val outgoing: Boolean)

    class ChatMessageAdapter(private val data: List<MessageUi>) : RecyclerView.Adapter<ChatViewHolder>() {
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ChatViewHolder {
            val layout = if (viewType == 1) R.layout.item_message_outgoing else R.layout.item_message_incoming
            val view = android.view.LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) = holder.bind(data[position])
        override fun getItemCount(): Int = data.size
        override fun getItemViewType(position: Int): Int = if (data[position].outgoing) 1 else 0
    }

    class ChatViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: MessageUi) {
            itemView.findViewById<TextView>(R.id.messageText).text = item.text
        }
    }
}
