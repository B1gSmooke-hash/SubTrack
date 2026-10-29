package ru.subtrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// The chosen design uses a deep-green header in both themes; foreground stays white.
@Composable internal fun GreenHeader(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(Brush.linearGradient(listOf(Color(0xFF125440),Color(0xFF1B775B))))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha=.045f),size.width*.56f,Offset(size.width*1.07f,size.height*1.15f))
            drawCircle(Color.White.copy(alpha=.035f),size.width*.44f,Offset(size.width*.96f,size.height*1.13f))
        }
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
        }
    }
}

@Composable private fun SettingRow(icon: ImageVector, label: String, subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Icon(icon,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(label,style=MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

@Composable fun SettingsScreen(user: User?, dark: Boolean, busy: Boolean, onTheme: (Boolean) -> Unit, onLogout: () -> Unit) {
    val context=LocalContext.current
    val version=remember(context) { context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty() }
    Screen {
        GreenHeader {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(60.dp).background(Color(0xFFDDF2E6),CircleShape),contentAlignment=Alignment.Center) {
                    Text(user?.name?.trim()?.take(1)?.uppercase().orEmpty(),style=MaterialTheme.typography.headlineMedium,color=Color(0xFF125440))
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(user?.name.orEmpty(),style=MaterialTheme.typography.titleLarge)
                    Text(user?.email.orEmpty(),style=MaterialTheme.typography.bodyMedium,color=Color.White.copy(alpha=.85f))
                }
            }
        }
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
            Column {
                SettingRow(Icons.Default.DarkMode,"Тёмная тема") {
                    Switch(dark,onTheme,enabled=!busy)
                }
                HorizontalDivider(Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.surfaceVariant)
                SettingRow(Icons.Default.CurrencyRuble,"Валюта","Российский рубль ₽")
                HorizontalDivider(Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.surfaceVariant)
                SettingRow(Icons.Default.Smartphone,"Данные хранятся\nна этом устройстве","Без облачной синхронизации")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick=onLogout,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=MaterialTheme.shapes.medium) {
            Text("Выйти")
        }
        Text("SubTrack $version",modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=12.dp),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun serviceIcon(category: String): ImageVector = when(category) {
    "Музыка" -> Icons.Default.MusicNote
    "Видео" -> Icons.Default.Movie
    "Облако" -> Icons.Default.Cloud
    "Обучение" -> Icons.Default.School
    else -> Icons.Default.Subscriptions
}

@Composable private fun DetailTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier=modifier,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(icon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,style=MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable fun SubscriptionDetailsScreen(item: Subscription?, busy: Boolean,
    onEdit: (Subscription) -> Unit, onArchive: (Subscription) -> Unit, onDelete: (Subscription) -> Unit) {
    var confirm by remember(item?.id) { mutableStateOf(false) }
    Screen {
        if(item==null) {
            Text("Подписка не найдена")
        } else {
            val monthly=Money.monthlyRub(item.amountMinor,item.period,item.currency)
            GreenHeader {
                Box(Modifier.size(48.dp).background(Color.White.copy(alpha=.16f),MaterialTheme.shapes.small),contentAlignment=Alignment.Center) {
                    Icon(serviceIcon(item.category),null,Modifier.size(30.dp))
                }
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(item.name,style=MaterialTheme.typography.titleLarge)
                    Text(item.category,style=MaterialTheme.typography.bodyMedium,color=Color.White.copy(alpha=.8f))
                }
                if(item.archived) Text("В архиве",style=MaterialTheme.typography.labelLarge)
                Column {
                    Text(if(item.currency=="RUB") "${Money.display(BigDecimal.valueOf(item.amountMinor,2))} ₽" else "Укажите цену в ₽",
                        style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text(when(item.period) { "YEAR"->"в год"; "QUARTER"->"в квартал"; "WEEK"->"в неделю"; else->"в месяц" },
                        style=MaterialTheme.typography.bodyLarge,color=Color.White.copy(alpha=.8f))
                }
            }
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.CalendarMonth,null,tint=MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("Следующее списание",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(LocalDate.parse(item.nextDate).format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.forLanguageTag("ru"))),
                            style=MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                DetailTile(Icons.Default.Science,"Пробный период",if(item.trial) "Да" else "Нет",Modifier.weight(1f).fillMaxHeight())
                DetailTile(Icons.Default.Calculate,"Оценка за год",monthly?.let { "${Money.display(it.multiply(BigDecimal(12)))} ₽" } ?: "Укажите цену в ₽",
                    Modifier.weight(1f).fillMaxHeight())
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={onEdit(item)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=MaterialTheme.shapes.medium) {
                    Text("Редактировать")
                }
                OutlinedButton(onClick={onArchive(item)},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=MaterialTheme.shapes.medium) {
                    Icon(if(item.archived) Icons.Default.Unarchive else Icons.Default.Archive,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp));Text(if(item.archived) "Восстановить" else "В архив")
                }
                TextButton(onClick={confirm=true},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.DeleteOutline,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Удалить")
                }
            }
            if(confirm) AlertDialog(onDismissRequest={confirm=false},
                title={Text("Удалить подписку?")},text={Text("Запись «${item.name}» будет удалена с этого устройства.")},
                confirmButton={TextButton(onClick={confirm=false;onDelete(item)},enabled=!busy) {Text("Удалить",color=MaterialTheme.colorScheme.error)}},
                dismissButton={TextButton(onClick={confirm=false}) {Text("Отмена")}})
        }
    }
}
