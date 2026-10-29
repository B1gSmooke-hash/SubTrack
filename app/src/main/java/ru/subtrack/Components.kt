package ru.subtrack

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val categoryColors = listOf(Color(0xFF176B5B), Color(0xFF7D68C4), Color(0xFFE5A341), Color(0xFF4C8CBA), Color(0xFF9B6878))
fun categoryColor(name: String) = categoryColors[categories.indexOf(name).coerceAtLeast(0)]

@Composable fun SubscriptionCard(item: Subscription, monthlyRub: BigDecimal?, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(item.nextDate))
    val target = if (days <= 3 && !item.archived) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val background by animateColorAsState(target, tween(350), label = "paymentStatus")
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(categoryColor(item.category), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text(item.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.category, style = MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (item.currency == "RUB") "${Money.display(BigDecimal.valueOf(item.amountMinor, 2))} ₽" else "—", style = MaterialTheme.typography.labelLarge)
            }
            Text(if (monthlyRub != null) "${Money.display(monthlyRub)} ₽ / мес." else "Укажите стоимость в рублях", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().background(background, RoundedCornerShape(10.dp)).padding(8.dp)) {
                Text(when { item.archived -> "В архиве"; days < 0 -> "Дата списания прошла"; days == 0L -> "Списание сегодня"; else -> "До списания: $days дн." }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (item.trial) Text("Пробный", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable fun SpendingRing(values: List<Pair<String, BigDecimal>>, total: BigDecimal) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animateFloatAsState(if (visible) 1f else 0f, tween(450), label = "spendingRing")
    val muted = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth().height(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp).semantics { contentDescription = "Расходы по категориям. Итого ${Money.display(total)} рублей" }) {
            val stroke = 22.dp.toPx()
            val diameter = size.minDimension - stroke
            val origin = Offset(stroke / 2, stroke / 2)
            drawArc(muted, 0f, 360f, false, origin, Size(diameter, diameter), style = Stroke(stroke))
            if (total.signum() > 0) {
                var start = -90f
                values.forEach { (name, amount) ->
                    val sweep = (amount.toDouble() / total.toDouble() * 360).toFloat()
                    val gap = if (values.count { it.second.signum()>0 } > 1) minOf(3f,sweep/5f) else 0f
                    if (amount.signum()>0) drawArc(categoryColor(name), start+gap/2, (sweep-gap) * progress,
                        false, origin, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Всего за период", style = MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${Money.display(total)} ₽", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable fun CategoryLegend(name: String, amount: BigDecimal, total: BigDecimal, selected: Boolean, onClick: () -> Unit) {
    val percent=if(total.signum()>0) amount.multiply(BigDecimal(100)).divide(total,1,RoundingMode.HALF_UP) else BigDecimal.ZERO
    Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,
        color=MaterialTheme.colorScheme.surface,
        border=if(selected) BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(12.dp).background(categoryColor(name),CircleShape))
            Column(Modifier.weight(1f)) {
                Text(name,style=MaterialTheme.typography.titleMedium)
                Text(if(amount.signum()==0) "Нет расходов" else "${percent.toPlainString().replace('.',',')} % от суммы",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${Money.display(amount)} ₽",style=MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable fun PeriodSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(4.dp)) {
        listOf(1 to "Месяц", 3 to "Квартал", 12 to "Год").forEach { (value, title) ->
            val color by animateColorAsState(if (selected == value) MaterialTheme.colorScheme.primary else Color.Transparent, label = "period")
            Box(Modifier.weight(1f).background(color, RoundedCornerShape(12.dp)).clickable { onSelect(value) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(title, color = if (selected == value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
