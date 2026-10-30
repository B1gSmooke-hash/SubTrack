package ru.subtrack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private class FieldFocus { var bounds: Rect? = null }
private val LocalFieldFocus = staticCompositionLocalOf { FieldFocus() }

// Observe touches before children, without consuming them: buttons and scrolling still work.
@Composable fun DismissInputOnOutsideTap(content: @Composable () -> Unit) {
    val tracked = remember { FieldFocus() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    CompositionLocalProvider(LocalFieldFocus provides tracked) {
        Box(Modifier.fillMaxSize().pointerInput(focus, keyboard) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                    if (down != null && tracked.bounds?.contains(down.position) == false) {
                        focus.clearFocus(); keyboard?.hide(); tracked.bounds = null
                    }
                }
            }
        }) { content() }
    }
}

@Composable fun AppField(value: String, change: (String) -> Unit, label: String,
    keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false) {
    val tracked = LocalFieldFocus.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var focused by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (focused) tracked.bounds = null } }
    OutlinedTextField(value=value, onValueChange=change, label={Text(label)}, singleLine=true,
        modifier=Modifier.fillMaxWidth().onGloballyPositioned {
            bounds=it.boundsInRoot(); if (focused) tracked.bounds=bounds
        }.onFocusChanged {
            focused=it.isFocused
            if (focused) tracked.bounds=bounds
            else if (tracked.bounds==bounds) tracked.bounds=null
        }, shape=MaterialTheme.shapes.medium,
        colors=OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor=Color.Transparent,
            unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            focusedContainerColor=MaterialTheme.colorScheme.surface),
        keyboardOptions=KeyboardOptions(keyboardType=keyboardType, imeAction=ImeAction.Done),
        keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}),
        visualTransformation=if(password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon=if(password) { {
            IconButton(onClick={visible=!visible}) {
                Icon(if(visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    if(visible) "Скрыть пароль: $label" else "Показать пароль: $label")
            }
        } } else null
    )
}

@Composable fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}

@Composable fun Auth(register: Boolean,busy: Boolean,submit:(String,String,String,String)->Unit,switch:()->Unit) {
    var name by rememberSaveable {mutableStateOf("")}; var email by rememberSaveable {mutableStateOf("")}
    // Never persist passwords or their visibility in saved instance state.
    var pass by remember {mutableStateOf("")}; var confirm by remember {mutableStateOf("")}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    Screen {
        Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large) {
            Icon(Icons.Default.AccountBalanceWallet,null,Modifier.padding(16.dp).size(32.dp),tint=MaterialTheme.colorScheme.primary)
        }
        Text(if(register) "Начнём с профиля" else "Подписки\nпод контролем",style=MaterialTheme.typography.headlineLarge)
        Text(if(register) "Сохраняйте подписки и следите за расходами в одном месте." else "Войдите, чтобы увидеть свои подписки и ближайшие платежи.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(register) AppField(name,{name=it},"Имя")
        AppField(email,{email=it},"Электронная почта",KeyboardType.Email)
        AppField(pass,{pass=it},"Пароль",KeyboardType.Password,password=true)
        if(register) {
            Text("От 6 до 128 символов",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            AppField(confirm,{confirm=it},"Повторите пароль",KeyboardType.Password,password=true)
        }
        Button(onClick={focus.clearFocus();keyboard?.hide();submit(name,email,pass,confirm)},enabled=!busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=MaterialTheme.shapes.medium) {
            Text(if(busy) "Подождите…" else if(register)"Создать профиль" else "Войти")
        }
        TextButton(onClick=switch,enabled=!busy,modifier=Modifier.align(Alignment.CenterHorizontally)) {
            Text(if(register)"Уже есть профиль? Войти" else "Нет профиля? Создать")
        }
        Text("Профиль хранится на этом устройстве. Почта служит логином — письмо не отправляется.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun Choice(label: String,options: Map<String,String>,value: String,onChange:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {
        Surface(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium) {
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(options[value].orEmpty(),style=MaterialTheme.typography.titleMedium)
                }
                Icon(Icons.Default.ExpandMore,"Выбрать: $label")
            }
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false}) {
            options.forEach{(k,v)->DropdownMenuItem(text={Text(v)},onClick={onChange(k);expanded=false})}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PaymentDateField(value: String, change: (String) -> Unit) {
    var open by rememberSaveable {mutableStateOf(false)}
    val selected=LocalDate.parse(value)
    Surface(onClick={open=true},modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Дата списания",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected.format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.forLanguageTag("ru"))),style=MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Default.CalendarMonth,"Открыть календарь",tint=MaterialTheme.colorScheme.primary)
        }
    }
    if(open) {
        val today=LocalDate.now()
        val dates=remember(today) { object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !PaymentDates.fromUtc(utcTimeMillis).isBefore(LocalDate.now())
            override fun isSelectableYear(year: Int) = year >= LocalDate.now().year
        } }
        val state=rememberDatePickerState(
            initialSelectedDateMillis=PaymentDates.utcMillis(maxOf(selected,today)),
            yearRange=today.year..maxOf(today.year+100,selected.year),selectableDates=dates)
        DatePickerDialog(onDismissRequest={open=false},confirmButton={
            TextButton(enabled=state.selectedDateMillis?.let { PaymentDates.allowed(PaymentDates.fromUtc(it).toString()) } == true,
                onClick={state.selectedDateMillis?.let { change(PaymentDates.fromUtc(it).toString()) };open=false}) {Text("Выбрать")}
        },dismissButton={TextButton(onClick={open=false}) {Text("Отмена")}}) {
            DatePicker(state=state,title={Text("Дата списания",Modifier.padding(start=24.dp,top=16.dp))},showModeToggle=false)
        }
    }
}

@Composable fun Form(item:Subscription?,busy:Boolean,save:(String,String,String,String,String,Boolean)->Unit) {
    var name by rememberSaveable(item?.id) {mutableStateOf(item?.name.orEmpty())}
    var amount by rememberSaveable(item?.id) {mutableStateOf(item?.takeIf{it.currency=="RUB"}?.let{BigDecimal.valueOf(it.amountMinor,2).toPlainString()}.orEmpty())}
    var period by rememberSaveable(item?.id) {mutableStateOf(item?.period?:"MONTH")}
    var date by rememberSaveable(item?.id) {mutableStateOf(item?.nextDate?:LocalDate.now().plusDays(1).toString())}
    var category by rememberSaveable(item?.id) {mutableStateOf(item?.category?:"Прочее")}
    var trial by rememberSaveable(item?.id) {mutableStateOf(item?.trial?:false)}
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    Screen {
        Text(if(item==null) "Новая подписка" else "Изменить подписку",style=MaterialTheme.typography.headlineMedium)
        Text("Укажите стоимость и дату следующего платежа.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        AppField(name,{name=it},"Название сервиса")
        AppField(amount,{amount=it},"Стоимость, ₽",KeyboardType.Decimal)
        if(item!=null && item.currency!="RUB") Text("Укажите стоимость в рублях, чтобы включить запись в расчёт.")
        Choice("Период",periods,period){period=it}
        PaymentDateField(date){date=it}
        if(!PaymentDates.allowed(date)) Text("Дата уже прошла. Выберите сегодняшнюю или будущую дату.",color=MaterialTheme.colorScheme.error)
        Choice("Категория",categories.associateWith{it},category){category=it}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Пробный период",Modifier.weight(1f));Switch(trial,{trial=it})
        }
        Button(onClick={focus.clearFocus();keyboard?.hide();save(name,amount,period,date,category,trial)},
            enabled=!busy && PaymentDates.allowed(date),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=MaterialTheme.shapes.medium) {
            Text(if(busy) "Сохранение…" else "Сохранить")
        }
    }
}
