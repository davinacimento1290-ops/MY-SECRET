package com.mysecret.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysecret.app.data.local.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FinanceGreen = Color(0xFF63D99A)
private val FinanceRed = Color(0xFFFF8585)

private fun parseMoneyCents(value: String): Long {
    val normalized = value.trim().replace(".", "").replace(",", ".")
    val amount = BigDecimal(normalized)
    require(amount > BigDecimal.ZERO) { "Informe um valor maior que zero." }
    return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
}

private fun money(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)

@Composable
internal fun FinanceScreen(dao: PrivateDao, scope: CoroutineScope, onNotice: (String) -> Unit, modifier: Modifier = Modifier) {
    val transactions by dao.observeFinanceTransactions().collectAsState(initial = emptyList())
    val goals by dao.observeFinanceGoals().collectAsState(initial = emptyList())
    val bills by dao.observeFinanceBills().collectAsState(initial = emptyList())

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Geral") }
    var note by remember { mutableStateOf("") }
    var goalTitle by remember { mutableStateOf("") }
    var goalTarget by remember { mutableStateOf("") }
    var billTitle by remember { mutableStateOf("") }
    var billAmount by remember { mutableStateOf("") }
    var billDays by remember { mutableStateOf("7") }
    var billCategory by remember { mutableStateOf("Geral") }
    var contribution by remember { mutableStateOf("") }

    val monthStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val monthTransactions = transactions.filter { it.occurredAt >= monthStart }
    val income = monthTransactions.filter { it.type == "INCOME" }.sumOf { it.amountCents }
    val expenses = monthTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
    val balance = income - expenses
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Controle financeiro", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Seus dados financeiros ficam no banco local criptografado do secret.", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RESUMO DESTE MÊS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(money(balance), color = if (balance >= 0) FinanceGreen else FinanceRed, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Receitas", color = Muted, fontSize = 11.sp); Text(money(income), color = FinanceGreen, fontWeight = FontWeight.Bold) }
                        Column { Text("Despesas", color = Muted, fontSize = 11.sp); Text(money(expenses), color = FinanceRed, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nova movimentação", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Descrição") }, singleLine = true)
                    OutlinedTextField(amount, { amount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Valor (ex.: 25,90)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(category, { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Categoria") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Observação (opcional)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    dao.saveFinanceTransaction(FinanceTransactionEntity(title = title.trim(), amountCents = parseMoneyCents(amount), type = "INCOME", category = category.trim().ifBlank { "Geral" }, note = note.trim()))
                                    title = ""; amount = ""; note = ""; onNotice("Receita registrada.")
                                } catch (e: Exception) { onNotice(e.message ?: "Não foi possível salvar a receita.") }
                            }
                        }, modifier = Modifier.weight(1f), enabled = title.isNotBlank() && amount.isNotBlank()) { Text("+ Receita") }
                        Button(onClick = {
                            scope.launch {
                                try {
                                    dao.saveFinanceTransaction(FinanceTransactionEntity(title = title.trim(), amountCents = parseMoneyCents(amount), type = "EXPENSE", category = category.trim().ifBlank { "Geral" }, note = note.trim()))
                                    title = ""; amount = ""; note = ""; onNotice("Despesa registrada.")
                                } catch (e: Exception) { onNotice(e.message ?: "Não foi possível salvar a despesa.") }
                            }
                        }, modifier = Modifier.weight(1f), enabled = title.isNotBlank() && amount.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B4354))) { Text("− Despesa") }
                    }
                }
            }
        }
        item { Text("Movimentações", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (transactions.isEmpty()) item { EmptyCard("Sem movimentações", "Registre sua primeira receita ou despesa.") }
        items(transactions, key = { it.id }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${item.category} • ${dateFormat.format(Date(item.occurredAt))}", color = Muted, fontSize = 11.sp)
                        if (item.note.isNotBlank()) Text(item.note, color = Muted, fontSize = 11.sp)
                    }
                    Column {
                        Text((if (item.type == "INCOME") "+" else "−") + money(item.amountCents), color = if (item.type == "INCOME") FinanceGreen else FinanceRed, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { scope.launch { dao.deleteFinanceTransaction(item); onNotice("Movimentação excluída.") } }) { Text("Excluir") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Metas de economia", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(goalTitle, { goalTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome da meta") }, singleLine = true)
                    OutlinedTextField(goalTarget, { goalTarget = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Quanto deseja juntar?") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Button(onClick = {
                        scope.launch {
                            try {
                                dao.saveFinanceGoal(FinanceGoalEntity(title = goalTitle.trim(), targetCents = parseMoneyCents(goalTarget)))
                                goalTitle = ""; goalTarget = ""; onNotice("Meta criada.")
                            } catch (e: Exception) { onNotice(e.message ?: "Não foi possível criar a meta.") }
                        }
                    }, enabled = goalTitle.isNotBlank() && goalTarget.isNotBlank()) { Text("Criar meta") }
                }
            }
        }
        if (goals.isNotEmpty()) item { Text("Suas metas", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        items(goals, key = { "goal-${it.id}" }) { goal ->
            val progress = (goal.savedCents.toFloat() / goal.targetCents.toFloat()).coerceIn(0f, 1f)
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(goal.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${money(goal.savedCents)} de ${money(goal.targetCents)}", color = Muted, fontSize = 12.sp)
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(contribution, { contribution = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Adicionar à meta (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                try {
                                    val added = parseMoneyCents(contribution)
                                    dao.updateFinanceGoalSaved(goal.id, (goal.savedCents + added).coerceAtMost(goal.targetCents))
                                    contribution = ""; onNotice("Valor adicionado à meta.")
                                } catch (e: Exception) { onNotice(e.message ?: "Valor inválido.") }
                            }
                        }, enabled = contribution.isNotBlank()) { Text("Guardar") }
                        TextButton(onClick = { scope.launch { dao.deleteFinanceGoal(goal); onNotice("Meta excluída.") } }) { Text("Excluir") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Contas e vencimentos", color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(billTitle, { billTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome da conta") }, singleLine = true)
                    OutlinedTextField(billAmount, { billAmount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Valor (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(billDays, { billDays = it.filter(Char::isDigit).take(3) }, modifier = Modifier.fillMaxWidth(), label = { Text("Vence em quantos dias?") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(billCategory, { billCategory = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Categoria") }, singleLine = true)
                    Button(onClick = {
                        scope.launch {
                            try {
                                val days = billDays.toLongOrNull() ?: 0L
                                require(days <= 3650L) { "Informe até 3650 dias." }
                                dao.saveFinanceBill(FinanceBillEntity(title = billTitle.trim(), amountCents = parseMoneyCents(billAmount), dueAt = System.currentTimeMillis() + days * 86_400_000L, category = billCategory.trim().ifBlank { "Geral" }))
                                billTitle = ""; billAmount = ""; onNotice("Conta adicionada.")
                            } catch (e: Exception) { onNotice(e.message ?: "Não foi possível adicionar a conta.") }
                        }
                    }, enabled = billTitle.isNotBlank() && billAmount.isNotBlank()) { Text("Adicionar conta") }
                }
            }
        }
        if (bills.isNotEmpty()) item { Text("Contas cadastradas", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        items(bills, key = { "bill-${it.id}" }) { bill ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(bill.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${money(bill.amountCents)} • vence ${dateFormat.format(Date(bill.dueAt))}", color = Muted, fontSize = 11.sp)
                        Text(if (bill.paid) "Paga" else "Pendente", color = if (bill.paid) FinanceGreen else FinanceRed, fontSize = 11.sp)
                    }
                    Column {
                        TextButton(onClick = { scope.launch { dao.setFinanceBillPaid(bill.id, !bill.paid); onNotice(if (bill.paid) "Conta marcada como pendente." else "Conta marcada como paga.") } }) { Text(if (bill.paid) "Desfazer" else "Pagar") }
                        TextButton(onClick = { scope.launch { dao.deleteFinanceBill(bill); onNotice("Conta excluída.") } }) { Text("Excluir") }
                    }
                }
            }
        }
        item {
            Text("Os valores são registros manuais. O secret não acessa contas bancárias nem realiza pagamentos.", color = Muted, fontSize = 11.sp)
        }
    }
}
