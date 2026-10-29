package ru.subtrack

import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
            SideEffect {
                WindowCompat.getInsetsController(window,window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            SubTrackTheme(dark) { DismissInputOnOutsideTap { App(vm) } }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(vm: AppViewModel) {
    val user by vm.user.collectAsStateWithLifecycle()
    val initializing by vm.initializing.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    if (initializing) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
        return
    }
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
        LaunchedEffect(route) { focus.clearFocus(); keyboard?.hide() }
        val main = route in listOf("home", "stats", "archive")
        val title = when(route) { "login" -> "Вход"; "register" -> "Регистрация"; "home" -> "SubTrack"; "stats" -> "Статистика"; "archive" -> "Архив"; "settings" -> "Настройки"; "form/{id}" -> "Подписка"; else -> "Детали" }
        Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
            TopAppBar(title = { Text(title,style=MaterialTheme.typography.titleLarge) },
                colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background), navigationIcon = {
                if (!main && route != "login") IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            }, actions = {
                if (main) IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Настройки") }
            })
        }, bottomBar = {
            if (main) NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                listOf(Triple("home", "Подписки", Icons.Default.Home), Triple("stats", "Статистика", Icons.Default.PieChart), Triple("archive", "Архив", Icons.Default.Archive)).forEach { (r,t,i) ->
                    NavigationBarItem(selected = route == r, onClick = { nav.navigate(r) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(i,t) }, label = { Text(t) })
                }
            }
        }, floatingActionButton = {
            if (route == "home") ExtendedFloatingActionButton(onClick = { nav.navigate("form/0") },
                containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
                icon={Icon(Icons.Default.Add,null)},text={Text("Добавить")})
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                NavHost(navController = nav, startDestination = if(user == null) "login" else "home", modifier = Modifier.weight(1f),
                    enterTransition={ fadeIn(tween(220,delayMillis=60)) + slideInHorizontally(tween(280,easing=FastOutSlowInEasing)) { it/12 } },
                    exitTransition={ fadeOut(tween(140)) },
                    popEnterTransition={ fadeIn(tween(220,delayMillis=40)) },
                    popExitTransition={ fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it/12 } }
                ) {
                    // Separate graphs: an authenticated back stack never contains login/register.
                    if (user == null) {
                    composable("login") { Auth(false, busy, { n,e,p,c -> vm.auth(false,n,e,p,c) }, { nav.navigate("register") }) }
                    composable("register") { Auth(true, busy, { n,e,p,c -> vm.auth(true,n,e,p,c) }, { nav.popBackStack() }) }
                    } else {
                    composable("home") {
                        var query by rememberSaveable { mutableStateOf("") }
                        val active = items.filter { !it.archived && it.ownerId == user?.id }
                        Screen {
                            Text("Здравствуйте, ${user?.name.orEmpty()}", style = MaterialTheme.typography.titleLarge)
                            Text("Ваши регулярные расходы — в одном месте",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            val sum = active.mapNotNull(::monthly).fold(BigDecimal.ZERO, BigDecimal::add)
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary)) {
                                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text("В среднем за месяц")
                                    Text("${Money.display(sum)} ₽", style = MaterialTheme.typography.headlineLarge)
                                    Text("${active.size} активных подписок",style=MaterialTheme.typography.labelLarge)
                                }
                            }
                            if (active.any { monthly(it) == null }) Text("Для части старых записей нужно указать стоимость в рублях. Откройте их и нажмите «Редактировать».", color = MaterialTheme.colorScheme.error)
                            AppField(query,{query=it},"Найти подписку")
                            val filtered = active.filter { it.name.contains(query,true) }
                            if(filtered.isEmpty()) Text(if(active.isEmpty()) "Добавьте первую подписку кнопкой +" else "Ничего не найдено")
                            filtered.forEach { s -> SubscriptionCard(s,monthly(s)) { nav.navigate("detail/${s.id}") } }
                            Spacer(Modifier.height(88.dp))
                        }
                    }
                    composable("stats") {
                        var period by rememberSaveable { mutableIntStateOf(1) }
                        var category by rememberSaveable { mutableStateOf<String?>(null) }
                        val active = items.filter { !it.archived && it.ownerId == user?.id }
                        val grouped = categories.map { c -> c to active.filter { it.category == c }.mapNotNull(::monthly).fold(BigDecimal.ZERO,BigDecimal::add).multiply(BigDecimal(period)) }
                        Screen {
                            PeriodSelector(period) { period=it }
                            val total=grouped.fold(BigDecimal.ZERO) { acc,p -> acc+p.second }
                            Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
                                SpendingRing(grouped,total)
                            }
                            Text("Оценка регулярных расходов, а не история списаний", style=MaterialTheme.typography.bodySmall)
                            if(active.any { monthly(it)==null }) Text("Часть старых записей не включена: укажите их стоимость в рублях",color=MaterialTheme.colorScheme.error)
                            Text("По категориям",style=MaterialTheme.typography.titleMedium)
                            if(total.signum()==0) Text("Добавьте подписку, чтобы увидеть распределение расходов.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            grouped.forEach { (c,v) -> CategoryLegend(c,v,total,category==c) { category=if(category==c)null else c } }
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
}
