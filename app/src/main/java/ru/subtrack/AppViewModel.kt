package ru.subtrack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(private val repo: Repository) : ViewModel() {
    val user = MutableStateFlow<User?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val items = user.flatMapLatest { u -> if (u == null) flowOf(emptyList()) else repo.store.subscriptions(u.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dark = repo.dark.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { block() } catch(e: CancellationException) { throw e }
            catch(e: Exception) { message.value = e.message ?: "Операция не выполнена" }
            finally { busy.value = false }
        }
    }
    fun auth(register: Boolean, name: String, email: String, pass: String, confirm: String) = action {
        if (register) require(pass == confirm) { "Пароли не совпадают" }
        user.value = if (register) repo.register(name, email.trim(), pass) else repo.login(email, pass)

    }
    fun logout() { if (!busy.value) user.value = null }
    fun save(id: Long?, name: String, amount: String, period: String,
             date: String, category: String, trial: Boolean, done: () -> Unit) = action {
        val owner = requireNotNull(user.value).id
        require(name.trim().length in 1..60) { "Введите название до 60 символов" }
        val minor = requireNotNull(Money.minor(amount)) { "Введите положительную сумму с точностью до копеек" }
        require(period in listOf("MONTH", "QUARTER", "YEAR", "WEEK"))
        require(category in categories)
        require(runCatching { LocalDate.parse(date) }.isSuccess) { "Дата должна иметь формат ГГГГ-ММ-ДД" }
        if (id == null) repo.store.add(Subscription(ownerId=owner, name=name.trim(), amountMinor=minor,
            period=period, nextDate=date, category=category, trial=trial))
        else repo.store.edit(id,owner,name.trim(),minor,period,date,category,trial)
        done()
    }
    fun archive(item: Subscription) = action { repo.store.archive(item.id,requireNotNull(user.value).id,!item.archived) }
    fun delete(item: Subscription, done: () -> Unit) = action { repo.store.delete(item.id,requireNotNull(user.value).id); done() }
    fun theme(value: Boolean) = action { repo.setDark(value) }
}
val categories = listOf("Музыка", "Видео", "Облако", "Обучение", "Прочее")
val periods = linkedMapOf("MONTH" to "Месяц", "QUARTER" to "Квартал", "YEAR" to "Год", "WEEK" to "Неделя")
