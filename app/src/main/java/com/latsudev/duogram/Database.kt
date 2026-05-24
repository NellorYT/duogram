package com.latsudev.duogram

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

enum class TransportMode { WEBRTC, VPS, SOCKS, TOR, LAN }

enum class PrivacyLevel { EVERYONE, FRIENDS_ONLY, NOBODY;
    companion object {
        fun fromIndex(index: Int): String = when (index) {
            1 -> FRIENDS_ONLY.name
            2 -> NOBODY.name
            else -> EVERYONE.name
        }
    }
}

class Converters {
    @TypeConverter
    fun stringToList(value: String?): List<String> = value?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

    @TypeConverter
    fun listToString(value: List<String>?): String = value?.joinToString("||") ?: ""
}

@Entity
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val username: String,
    val avatarUri: String?,
    val bio: String,
    val links: List<String>,
    val activeTransport: String,
    val avatarVisibility: String = PrivacyLevel.EVERYONE.name,
    val nameVisibility: String = PrivacyLevel.EVERYONE.name,
    val bioVisibility: String = PrivacyLevel.FRIENDS_ONLY.name,
    val linksVisibility: String = PrivacyLevel.FRIENDS_ONLY.name,
    val lastSeenVisibility: String = PrivacyLevel.FRIENDS_ONLY.name
)

@Entity
data class Contact(
    @PrimaryKey val contactId: String,
    val publicKey: String,
    val username: String,
    val displayName: String,
    val avatarUri: String?,
    val lastTransport: String,
    val privacyJson: String,
    val notificationsEnabled: Boolean = true,
    val pinnedOrder: Int? = null
)

@Entity
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val senderId: String,
    val encryptedText: String?,
    val mediaUri: String?,
    val timestamp: Long,
    val deliveryStatus: String,
    val outgoing: Boolean,
    val deleteForAllRequested: Boolean = false
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val title: String,
    val avatarUri: String?,
    val participants: List<String>,
    val admins: List<String>,
    val createdAt: Long
)

@Entity
data class SavedNote(
    @PrimaryKey(autoGenerate = true) val noteId: Long = 0,
    val chatId: String,
    val encryptedPayload: String,
    val createdAt: Long,
    val isPinned: Boolean = false
)

@Dao
interface DuogramDao {
    @Query("SELECT * FROM UserProfile WHERE id = 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserProfile(profile: UserProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedNote(note: SavedNote)

    @Query("DELETE FROM Message WHERE chatId = :chatId")
    suspend fun deleteMessagesByChat(chatId: String)

    @Query("SELECT COUNT(*) FROM Contact WHERE pinnedOrder IS NOT NULL")
    suspend fun countPinnedContacts(): Int

    @Query("UPDATE Contact SET pinnedOrder = :orderValue WHERE contactId = :contactId")
    suspend fun pinContact(contactId: String, orderValue: Int)
}

@Database(
    entities = [UserProfile::class, Contact::class, Message::class, GroupEntity::class, SavedNote::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun duogramDao(): DuogramDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            val passphrase = SQLiteDatabase.getBytes(("duogram_sqlcipher_" + CryptoManager.generateUsernameFromPublicKey()).toCharArray())
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, "duogram.db")
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        fun destroy(context: Context) {
            INSTANCE?.close()
            INSTANCE = null
            context.deleteDatabase("duogram.db")
        }
    }
}
