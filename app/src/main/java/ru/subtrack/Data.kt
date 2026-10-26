package ru.subtrack

import android.app.Application
import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.xmlpull.v1.XmlPullParser

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class User(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String,
    val email: String, val salt: String, val passwordHash: String)
@Entity(tableName = "subscriptions", indices = [Index("ownerId")], foreignKeys = [ForeignKey(
    entity = User::class, parentColumns = ["id"], childColumns = ["ownerId"], onDelete = ForeignKey.CASCADE)])
data class Subscription(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ownerId: Long,
    val name: String, val amountMinor: Long, val currency: String, val period: String,
    val nextDate: String, val category: String, val trial: Boolean = false, val archived: Boolean = false)
@Entity(tableName = "rates")
data class Rate(@PrimaryKey val code: String, val rubPerUnit: String, val date: String, val fetchedAt: Long)

@Dao interface Store {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1") suspend fun user(email: String): User?
    @Insert suspend fun addUser(user: User): Long
    @Query("SELECT * FROM subscriptions WHERE ownerId = :owner ORDER BY nextDate, name")
    fun subscriptions(owner: Long): Flow<List<Subscription>>
    @Insert suspend fun add(item: Subscription)
    @Query("UPDATE subscriptions SET name=:name, amountMinor=:amount, currency=:currency, period=:period, nextDate=:date, category=:category, trial=:trial WHERE id=:id AND ownerId=:owner")
    suspend fun edit(id: Long, owner: Long, name: String, amount: Long, currency: String, period: String, date: String, category: String, trial: Boolean)
    @Query("UPDATE subscriptions SET archived=:archived WHERE id=:id AND ownerId=:owner")
    suspend fun archive(id: Long, owner: Long, archived: Boolean)
    @Query("DELETE FROM subscriptions WHERE id=:id AND ownerId=:owner") suspend fun delete(id: Long, owner: Long)
    @Query("SELECT * FROM rates") fun rates(): Flow<List<Rate>>
    @Query("SELECT MAX(fetchedAt) FROM rates") suspend fun lastFetch(): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRates(rates: List<Rate>)
}
@androidx.room.Database(entities = [User::class, Subscription::class, Rate::class], version = 1, exportSchema = true)
abstract class Database : RoomDatabase() { abstract fun store(): Store }
interface CurrencyApi { @GET("scripts/XML_daily.asp") suspend fun daily(): ResponseBody }
val Context.preferences by preferencesDataStore("settings")

class Repository(private val context: Context, val store: Store) {
    private val api = Retrofit.Builder().baseUrl("https://www.cbr.ru/")
        .client(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build())
        .build().create(CurrencyApi::class.java)
    val dark = context.preferences.data.map { it[booleanPreferencesKey("dark")] ?: false }
    suspend fun setDark(value: Boolean) { context.preferences.edit { it[booleanPreferencesKey("dark")] = value } }
    suspend fun register(name: String, email: String, password: String): User = withContext(Dispatchers.IO) {
        require(name.trim().length in 2..60) { "Имя должно содержать от 2 до 60 символов" }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { "Проверьте адрес электронной почты" }
        require(Passwords.valid(password)) { "Пароль должен содержать от 10 до 128 символов" }
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
    suspend fun refresh(force: Boolean): String = withContext(Dispatchers.IO) {
        val last = store.lastFetch() ?: 0L
        if (!force && System.currentTimeMillis() - last < 86400000L) return@withContext "Используются сохранённые курсы"
        api.daily().use { body ->
            // Android's pull parser does not fetch external entities; DTD processing stays off.
            val xml = android.util.Xml.newPullParser()
            xml.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            xml.setInput(body.byteStream(), null)
            var date = ""
            var code = ""
            var nominal = ""
            var value = ""
            val values = mutableListOf<Rate>()
            var event = xml.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) when (xml.name) {
                    "ValCurs" -> date = xml.getAttributeValue(null, "Date").orEmpty()
                    "Valute" -> { code = ""; nominal = ""; value = "" }
                    "CharCode" -> code = xml.nextText().trim()
                    "Nominal" -> nominal = xml.nextText().trim()
                    "Value" -> value = xml.nextText().trim()
                }
                if (event == XmlPullParser.END_TAG && xml.name == "Valute") {
                    val units = nominal.toBigDecimal()
                    val rubles = value.replace(',', '.').toBigDecimal()
                    require(units.signum() > 0 && rubles.signum() > 0 && code.length == 3)
                    values.add(Rate(code, rubles.divide(units, 8, java.math.RoundingMode.HALF_UP).toPlainString(), date, System.currentTimeMillis()))
                }
                event = xml.next()
            }
            require(date.isNotBlank())
            require(values.isNotEmpty()) { "Сервис вернул пустой список курсов" }
            store.putRates(values)
            "Курсы Банка России на $date сохранены"
        }
    }
}
class SubTrackApp : Application() {
    val repository by lazy { Repository(this, Room.databaseBuilder(this, Database::class.java, "subtrack.db").build().store()) }
}
