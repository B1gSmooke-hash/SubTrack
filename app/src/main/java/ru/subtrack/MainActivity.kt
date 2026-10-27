package ru.subtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import java.math.BigDecimal
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repo = (application as SubTrackApp).repository
            val vm: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo) as T
            })
            val dark by vm.dark.collectAsStateWithLifecycle()
            val colors = if (dark) darkColorScheme(primary = Color(0xFF8ED5C1)) else lightColorScheme(
                primary = Color(0xFF176B5B), background = Color(0xFFF5F7F4), surface = Color(0xFFF5F7F4), secondaryContainer = Color(0xFFD9EBE4))
            MaterialTheme(colorScheme = colors) { App(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(vm: AppViewModel) {
    val user by vm.user.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val dark by vm.dark.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    fun monthly(s: Subscription) = Money.monthlyRub(s.amountMinor, s.period, s.currency)
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.message.value = null } }
    key(user?.id) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: if (user == null) "login" else "home"
        val main = route in listOf("home", "stats", "archive")
        val title = when(route) { "login" -> "Вход"; "register" -> "Регистрация"; "home" -> "SubTrack"; "stats" -> "Статистика"; "archive" -> "Архив"; "settings" -> "Настройки"; "form/{id}" -> "Подписка"; else -> "Детали" }
        Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = {
                if (!main && route != "login") IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            }, actions = {
                if (main) IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Настройки") }
            })
        }, bottomBar = {
            if (main) NavigationBar {
                listOf(Triple("home", "Подписки", Icons.Default.Home), Triple("stats", "Статистика", Icons.Default.PieChart), Triple("archive", "Архив", Icons.Default.Archive)).forEach { (r,t,i) ->
                    NavigationBarItem(selected = route == r, onClick = { nav.navigate(r) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(i,t) }, label = { Text(t) })
                }
            }
        }, floatingActionButton = {
            if (route == "home") FloatingActionButton(onClick = { nav.navigate("form/0") }) { Icon(Icons.Default.Add, "Добавить подписку") }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                NavHost(navController = nav, startDestination = if(user == null) "login" else "home", modifier = Modifier.weight(1f)) {
                    composable("login") { Auth(false, busy, { n,e,p,c -> vm.auth(false,n,e,p,c) }, { nav.navigate("register") }) }
                    composable("register") { Auth(true, busy, { n,e,p,c -> vm.auth(true,n,e,p,c) }, { nav.popBackStack() }) }
                    composable("home") {
                        var query by rememberSaveable { mutableStateOf("") }
                        val active = items.filter { !it.archived && it.ownerId == user?.id }
                        Screen {
                            Text("Здравствуйте, ${user?.name.orEmpty()}", style = MaterialTheme.typography.titleLarge)
                            val sum = active.mapNotNull(::monthly).fold(BigDecimal.ZERO, BigDecimal::add)
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(20.dp)) {
                                    Text("В среднем за месяц")
                                    Text("${Money.display(sum)} ₽", style = MaterialTheme.typography.headlineLarge)
                                    Text("Активных подписок: ${active.size}")
                                }
                            }
                            if (active.any { monthly(it) == null }) Text("Для части старых записей нужно указать стоимость в рублях. Откройте их и нажмите «Редактировать».", color = MaterialTheme.colorScheme.error)
                            OutlinedTextField(query,{query=it},label={Text("Найти подписку")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                            val filtered = active.filter { it.name.contains(query,true) }
                            if(filtered.isEmpty()) Text(if(active.isEmpty()) "Добавьте первую подписку кнопкой +" else "Ничего не найдено")
                            filtered.forEach { s -> SubscriptionCard(s,monthly(s)) { nav.navigate("detail/${s.id}") } }
                            Spacer(Modifier.height(64.dp))
                        }
                    }
                    composable("stats") {
                        var period by rememberSaveable { mutableIntStateOf(1) }
                        var category by rememberSaveable { mutableStateOf<String?>(null) }
                        val active = items.filter { !it.archived && it.ownerId == user?.id }
                        val grouped = categories.map { c -> c to active.filter { it.category == c }.mapNotNull(::monthly).fold(BigDecimal.ZERO,BigDecimal::add).multiply(BigDecimal(period)) }
                        Screen {
                            PeriodSelector(period) { period=it }
                            SpendingRing(grouped,grouped.fold(BigDecimal.ZERO) { acc,p -> acc+p.second })
                            Text("Оценка регулярных расходов, а не история списаний", style=MaterialTheme.typography.bodySmall)
                            if(active.any { monthly(it)==null }) Text("Часть старых записей не включена: укажите их стоимость в рублях",color=MaterialTheme.colorScheme.error)
                            grouped.forEach { (c,v) -> OutlinedButton(onClick={category=if(category==c)null else c},modifier=Modifier.fillMaxWidth()) { Text("$c — ${Money.display(v)} ₽") } }
                            category?.let { c -> active.filter{it.category==c}.forEach { s -> SubscriptionCard(s,monthly(s)) {nav.navigate("detail/${s.id}")} } }
                        }
                    }
                    composable("archive") { Screen {
                        val archived=items.filter{it.archived && it.ownerId == user?.id}
                        Text("Архивные подписки исключены из текущих расходов.")
                        if(archived.isEmpty()) Text("Архив пока пуст")
                        archived.forEach { s -> SubscriptionCard(s,monthly(s)) {nav.navigate("detail/${s.id}")} }
                    } }
                    composable("settings") { Screen {
                        Text("${user?.name}\n${user?.email}",style=MaterialTheme.typography.titleMedium)
                        Text("Локальный профиль на этом устройстве. Данные не синхронизируются.")
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("Тёмная тема",Modifier.weight(1f));Switch(dark,{vm.theme(it)},enabled=!busy) }
                        Text("Валюта: российский рубль (₽)")
                        OutlinedButton(onClick={vm.logout()},enabled=!busy) {Text("Выйти")}
                    } }
                    composable("form/{id}") { back ->
                        val id=back.arguments?.getString("id")?.toLongOrNull()?.takeIf{it!=0L}
                        val item=items.firstOrNull{it.id==id && it.ownerId==user?.id}
                        if(id!=null && item==null) Screen {Text("Подписка не найдена")}
                        else Form(item,busy) { n,a,p,d,cat,t -> vm.save(id,n,a,p,d,cat,t) {nav.popBackStack()} }
                    }
                    composable("detail/{id}") { back ->
                        val item=items.firstOrNull{it.id==back.arguments?.getString("id")?.toLongOrNull() && it.ownerId==user?.id}
                        var confirm by remember {mutableStateOf(false)}
                        Screen {
                            if(item==null) Text("Подписка не найдена") else {
                                Text(item.name,style=MaterialTheme.typography.headlineMedium)
                                Text(if (item.currency == "RUB") "${Money.display(BigDecimal.valueOf(item.amountMinor,2))} ₽ / ${periods[item.period]}" else "Укажите стоимость в рублях через «Редактировать»")
                                Text("Следующее списание: ${item.nextDate}\nКатегория: ${item.category}\nПробный период: ${if(item.trial)"да" else "нет"}")
                                monthly(item)?.let { Text("Оценка за год: ${Money.display(it.multiply(BigDecimal(12)))} ₽") }
                                Button(onClick={nav.navigate("form/${item.id}")},enabled=!busy) { Text("Редактировать") }
                                OutlinedButton(onClick={vm.archive(item)},enabled=!busy) {Text(if(item.archived)"Восстановить" else "В архив")}
                                TextButton(onClick={confirm=true},enabled=!busy) {Text("Удалить",color=MaterialTheme.colorScheme.error)}
                                if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Удалить подписку?")},text={Text("Запись будет удалена с этого устройства.")},confirmButton={TextButton(onClick={confirm=false;vm.delete(item){nav.popBackStack()}}){Text("Удалить")}},dismissButton={TextButton(onClick={confirm=false}){Text("Отмена")}})
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}
@Composable fun Auth(register: Boolean,busy: Boolean,submit:(String,String,String,String)->Unit,switch:()->Unit) {
    var name by rememberSaveable {mutableStateOf("")};var email by rememberSaveable {mutableStateOf("")}
    // Passwords are intentionally excluded from saved instance state.
    var pass by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    Screen {
        Text("Все подписки под контролем",style=MaterialTheme.typography.headlineMedium)
        Text("Создайте локальный профиль. Адрес почты используется как логин и не проверяется отправкой письма.")
        if(register) OutlinedTextField(name,{name=it},label={Text("Имя")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(email,{email=it},label={Text("Электронная почта")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(pass,{pass=it},label={Text("Пароль")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
        if(register) {
            Text("От 10 до 128 символов",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(confirm,{confirm=it},label={Text("Повторите пароль")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        }
        Button(onClick={submit(name,email,pass,confirm)},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text(if(register)"Создать профиль" else "Войти")}
        TextButton(onClick=switch,enabled=!busy) {Text(if(register)"Уже есть профиль" else "Создать профиль")}
    }
}
@Composable fun Choice(label: String,options: Map<String,String>,value: String,onChange:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box { OutlinedButton(onClick={expanded=true}) {Text("$label: ${options[value]}")}
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {options.forEach{(k,v)->DropdownMenuItem(text={Text(v)},onClick={onChange(k);expanded=false})}}
    }
}
@Composable fun Form(item:Subscription?,busy:Boolean,save:(String,String,String,String,String,Boolean)->Unit) {
    var name by rememberSaveable(item?.id) {mutableStateOf(item?.name.orEmpty())}
    var amount by rememberSaveable(item?.id) {mutableStateOf(item?.takeIf{it.currency == "RUB"}?.let{BigDecimal.valueOf(it.amountMinor,2).toPlainString()}.orEmpty())}
    var period by rememberSaveable(item?.id) {mutableStateOf(item?.period?:"MONTH")}
    var date by rememberSaveable(item?.id) {mutableStateOf(item?.nextDate?:LocalDate.now().plusDays(1).toString())}
    var category by rememberSaveable(item?.id) {mutableStateOf(item?.category?:"Прочее")}
    var trial by rememberSaveable(item?.id) {mutableStateOf(item?.trial?:false)}
    Screen {
        OutlinedTextField(name,{name=it},label={Text("Название сервиса")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(amount,{amount=it},label={Text("Стоимость, ₽")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
        if (item != null && item.currency != "RUB") Text("Введите стоимость этой записи в рублях. До сохранения она не учитывается в итогах.")
        Choice("Период",periods,period){period=it}
        OutlinedTextField(date,{date=it},label={Text("Дата списания ГГГГ-ММ-ДД")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Choice("Категория",categories.associateWith{it},category){category=it}
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(trial,{trial=it});Text("Пробный период")}
        Button(onClick={save(name,amount,period,date,category,trial)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Сохранить")}
    }
}
