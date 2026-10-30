package ru.subtrack

import android.app.Application
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class User(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String,
    val email: String, val salt: String, val passwordHash: String)
@Entity(tableName = "subscriptions", indices = [Index("ownerId")], foreignKeys = [ForeignKey(
    entity = User::class, parentColumns = ["id"], childColumns = ["ownerId"], onDelete = ForeignKey.CASCADE)])
data class Subscription(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ownerId: Long,
    val name: String, val amountMinor: Long, val currency: String = "RUB", val period: String,
    val nextDate: String, val category: String, val trial: Boolean = false, val archived: Boolean = false)
@Dao interface Store {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1") suspend fun user(email: String): User?
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1") suspend fun userById(id: Long): User?
    @Insert suspend fun addUser(user: User): Long
    @Query("SELECT * FROM subscriptions WHERE ownerId = :owner ORDER BY nextDate, name")
    fun subscriptions(owner: Long): Flow<List<Subscription>>
    @Insert suspend fun add(item: Subscription)
    @Query("UPDATE subscriptions SET name=:name, amountMinor=:amount, currency='RUB', period=:period, nextDate=:date, category=:category, trial=:trial WHERE id=:id AND ownerId=:owner")
    suspend fun edit(id: Long, owner: Long, name: String, amount: Long, period: String, date: String, category: String, trial: Boolean)
    @Query("UPDATE subscriptions SET archived=:archived WHERE id=:id AND ownerId=:owner")
    suspend fun archive(id: Long, owner: Long, archived: Boolean)
    @Query("DELETE FROM subscriptions WHERE id=:id AND ownerId=:owner") suspend fun delete(id: Long, owner: Long)
}
@androidx.room.Database(entities = [User::class, Subscription::class], version = 2, exportSchema = true)
abstract class Database : RoomDatabase() { abstract fun store(): Store }
// Preserve user records during upgrade. Only the unused exchange-rate cache is removed.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("DROP TABLE IF EXISTS rates") }
}
val Context.preferences by preferencesDataStore("settings")

class Repository(private val context: Context, val store: Store) {
    private val sessionKey = longPreferencesKey("sessionUserId")
    val session = context.preferences.data.map { it[sessionKey] }.distinctUntilChanged()
        .map { id -> id?.let { store.userById(it) } }
    suspend fun startSession(user: User) { context.preferences.edit { it[sessionKey] = user.id } }
    suspend fun endSession() { context.preferences.edit { it.remove(sessionKey) } }
    val dark = context.preferences.data.map { it[booleanPreferencesKey("dark")] ?: false }
    suspend fun setDark(value: Boolean) { context.preferences.edit { it[booleanPreferencesKey("dark")] = value } }
    suspend fun register(name: String, email: String, password: String): User = withContext(Dispatchers.IO) {
        require(name.trim().length in 2..60) { "Имя должно содержать от 2 до 60 символов" }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { "Проверьте адрес электронной почты" }
        require(Passwords.valid(password)) { "Пароль должен содержать от 6 до 128 символов" }
        val normalized = email.trim().lowercase(java.util.Locale.ROOT)
        require(store.user(normalized) == null) { "Этот адрес уже зарегистрирован на устройстве" }
        val salt = Passwords.salt()
        val user = User(name = name.trim(), email = normalized, salt = salt, passwordHash = Passwords.hash(password, salt))
        user.copy(id = store.addUser(user))
    }
    suspend fun login(email: String, password: String): User = withContext(Dispatchers.IO) {
        val user = store.user(email.trim().lowercase(java.util.Locale.ROOT))
        require(user != null && Passwords.verify(password, user.salt, user.passwordHash)) { "Неверный адрес или пароль" }
        user
    }
}

class SubTrackApp : Application() {
    val repository by lazy { Repository(this, Room.databaseBuilder(this, Database::class.java, "subtrack.db").addMigrations(MIGRATION_1_2).build().store()) }
}
