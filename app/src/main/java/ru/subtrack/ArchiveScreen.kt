package ru.subtrack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

@Composable fun ArchiveScreen(items: List<Subscription>, onOpen: (Subscription) -> Unit) {
    Screen {
        GreenHeader {
            Box(Modifier.size(48.dp).background(Color.White.copy(alpha=.16f),MaterialTheme.shapes.small),contentAlignment=Alignment.Center) {
                Icon(Icons.Default.Archive,null,Modifier.size(28.dp))
            }
            Text("Сохранённые подписки",style=MaterialTheme.typography.titleLarge)
            Text("Записей в архиве: ${items.size}",style=MaterialTheme.typography.bodyLarge)
            Text("Не учитываются в текущих расходах. Их можно восстановить в любой момент.",
                style=MaterialTheme.typography.bodyMedium,color=Color.White.copy(alpha=.85f))
        }
        if(items.isEmpty()) {
            Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal=24.dp,vertical=32.dp),horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Inventory2,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
                    Text("Архив пока пуст",style=MaterialTheme.typography.titleMedium)
                    Text("Перенесённые в архив подписки появятся здесь. Все данные о них сохранятся.",
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                }
            }
        } else {
            Text("В архиве",style=MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Card(onClick={onOpen(item)},modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).background(categoryColor(item.category),MaterialTheme.shapes.small),contentAlignment=Alignment.Center) {
                                Text(item.name.take(1).uppercase(),color=Color.White,fontWeight=FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                Text(item.name,style=MaterialTheme.typography.titleMedium)
                                Text(item.category,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Archive,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if(item.currency=="RUB") "${Money.display(BigDecimal.valueOf(item.amountMinor,2))} ₽ / ${periods[item.period].orEmpty().lowercase()}"
                            else "Укажите стоимость в рублях",style=MaterialTheme.typography.bodyLarge)
                        HorizontalDivider(color=MaterialTheme.colorScheme.surfaceVariant)
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text("Открыть детали",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
