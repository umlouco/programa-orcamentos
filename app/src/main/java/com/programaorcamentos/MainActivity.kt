package com.programaorcamentos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.programaorcamentos.data.BudgetArchiveRow
import com.programaorcamentos.data.BudgetStatus
import com.programaorcamentos.data.CompanyProfile
import com.programaorcamentos.data.EditableBudget
import com.programaorcamentos.data.EditableLine
import com.programaorcamentos.data.formatMoney
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException

private sealed interface Screen {
    data object Loading : Screen
    data object Home : Screen
    data object Settings : Screen
    data object Archive : Screen
    data object Backup : Screen
    data class Editor(val budgetId: Long? = null) : Screen
}

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as BudgetApplication
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    BudgetApp(app)
                }
            }
        }
    }
}

@Composable
private fun BudgetApp(app: BudgetApplication) {
    val company by app.repository.observeCompany().collectAsStateWithLifecycle(initialValue = null)
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
    LaunchedEffect(Unit) {
        app.repository.observeCompany().first().let { company ->
            screen = if (company == null) Screen.Settings else Screen.Home
        }
    }
    when (val current = screen) {
        Screen.Loading -> Text("A carregar...", Modifier.padding(24.dp))
        Screen.Home -> HomeScreen(onNavigate = { screen = it })
        Screen.Settings -> CompanySettingsScreen(
            app = app,
            company = company ?: CompanyProfile(),
            onDone = { screen = Screen.Home }
        )
        Screen.Archive -> ArchiveScreen(app = app, onBack = { screen = Screen.Home }, onOpen = { screen = Screen.Editor(it) })
        Screen.Backup -> BackupScreen(app = app, onBack = { screen = Screen.Home })
        is Screen.Editor -> BudgetEditorScreen(app = app, id = current.budgetId, onDone = { screen = Screen.Archive })
    }
}

@Composable
private fun HomeScreen(onNavigate: (Screen) -> Unit) {
    Scaffold(topBar = { AppBar("Programa Orçamentos") }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onNavigate(Screen.Editor()) }, modifier = Modifier.fillMaxWidth().testTag("home_create_budget")) { Text("Criar novo orçamento") }
            Button(onClick = { onNavigate(Screen.Archive) }, modifier = Modifier.fillMaxWidth().testTag("home_archive")) { Text("Arquivo de orçamentos") }
            Button(onClick = { onNavigate(Screen.Settings) }, modifier = Modifier.fillMaxWidth().testTag("home_settings")) { Text("Dados da empresa") }
            Button(onClick = { onNavigate(Screen.Backup) }, modifier = Modifier.fillMaxWidth().testTag("home_backup")) { Text("Backup e restauro") }
        }
    }
}

@Composable
private fun CompanySettingsScreen(app: BudgetApplication, company: CompanyProfile, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var draft by remember { mutableStateOf(company) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            draft = draft.copy(logoUri = uri.toString())
        }
    }
    Scaffold(topBar = { AppBar("Dados da empresa", onDone) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = { logoPicker.launch(arrayOf("image/*")) }) { Text(if (draft.logoUri == null) "Escolher logotipo" else "Alterar logotipo") }
            Field("Nome da empresa", draft.name, tag = "company_name") { draft = draft.copy(name = it) }
            Field("Morada", draft.address) { draft = draft.copy(address = it) }
            Field("NIF", draft.vatNumber) { draft = draft.copy(vatNumber = it) }
            Field("Telefone", draft.phone) { draft = draft.copy(phone = it) }
            Field("Email", draft.email) { draft = draft.copy(email = it) }
            Field("Contactos adicionais", draft.additionalContacts) { draft = draft.copy(additionalContacts = it) }
            Field("IVA predefinido (%)", draft.defaultVatRate.toString()) { draft = draft.copy(defaultVatRate = it.replace(",", ".").toDoubleOrNull() ?: draft.defaultVatRate) }
            Field("Moeda", draft.currency) { draft = draft.copy(currency = it.uppercase()) }
            Field("Validade predefinida (dias)", draft.defaultValidityDays.toString()) { draft = draft.copy(defaultValidityDays = it.toIntOrNull() ?: draft.defaultValidityDays) }
            Field("Detalhes de pagamento", draft.paymentDetails) { draft = draft.copy(paymentDetails = it) }
            Field("Rodapé", draft.footerText) { draft = draft.copy(footerText = it) }
            Button(
                onClick = { scope.launch { app.repository.saveCompany(draft); onDone() } },
                modifier = Modifier.fillMaxWidth().testTag("company_save")
            ) {
                Icon(Icons.Default.Save, null)
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun BudgetEditorScreen(app: BudgetApplication, id: Long?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var draft by remember { mutableStateOf<EditableBudget?>(null) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(id) {
        draft = if (id == null) app.repository.newBudgetDraft() else app.repository.getEditableBudget(id)
    }
    val current = draft
    Scaffold(topBar = { AppBar(if (id == null) "Novo orçamento" else "Editar orçamento", onDone) }) { padding ->
        if (current == null) {
            Text("A carregar...", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Número", current.budgetNumber) { draft = current.copy(budgetNumber = it) }
            Field("Data (AAAA-MM-DD)", current.issueDate.toString()) { draft = current.copy(issueDate = parseDate(it, current.issueDate)) }
            Field("Validade (AAAA-MM-DD)", current.expiryDate.toString()) { draft = current.copy(expiryDate = parseDate(it, current.expiryDate)) }
            Field("Título do projeto", current.projectTitle) { draft = current.copy(projectTitle = it) }
            Field("Local do projeto", current.projectLocation) { draft = current.copy(projectLocation = it) }
            StatusPicker(current.status) { draft = current.copy(status = it ?: current.status) }
            Row {
                Checkbox(current.pricesEnteredWithVat, onCheckedChange = { draft = current.copy(pricesEnteredWithVat = it) })
                Text("Preços introduzidos com IVA", Modifier.padding(top = 12.dp))
            }
            Row {
                Checkbox(current.exemptFromVat, onCheckedChange = { draft = current.copy(exemptFromVat = it) })
                Text("Orçamento sem IVA", Modifier.padding(top = 12.dp))
            }
            Text("Cliente", fontWeight = FontWeight.Bold)
            Field("Nome", current.client.name) { draft = current.copy(client = current.client.copy(name = it)) }
            Field("NIF", current.client.vatNumber) { draft = current.copy(client = current.client.copy(vatNumber = it)) }
            Field("Morada", current.client.address) { draft = current.copy(client = current.client.copy(address = it)) }
            Field("Telefone", current.client.phone) { draft = current.copy(client = current.client.copy(phone = it)) }
            Field("Email", current.client.email) { draft = current.copy(client = current.client.copy(email = it)) }
            Text("Linhas", fontWeight = FontWeight.Bold)
            current.lines.forEachIndexed { index, line ->
                LineEditor(
                    line = line,
                    exemptFromVat = current.exemptFromVat,
                    onChange = { changed -> draft = current.copy(lines = current.lines.toMutableList().also { it[index] = changed }) },
                    onDelete = { draft = current.copy(lines = current.lines.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(EditableLine()) }) }
                )
                HorizontalDivider()
            }
            TextButton(onClick = { draft = current.copy(lines = current.lines + EditableLine()) }) {
                Icon(Icons.Default.Add, null)
                Text("Adicionar linha")
            }
            Field("Notas", current.notes) { draft = current.copy(notes = it) }
            if (message.isNotBlank()) Text(message)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val savedId = app.repository.saveBudgetTransactional(current)
                        draft = app.repository.getEditableBudget(savedId)
                        message = "Guardado"
                    }
                }, modifier = Modifier.testTag("budget_save")) { Text("Guardar") }
                Button(onClick = {
                    scope.launch {
                        val savedId = app.repository.saveBudgetTransactional(current)
                        val full = app.repository.getFullBudget(savedId) ?: return@launch
                        val file = app.pdfGenerator.generate(app.repository.getCompany(), full)
                        val uri = app.pdfGenerator.uriFor(file)
                        context.startActivity(Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                }, modifier = Modifier.testTag("budget_pdf_share")) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Text("Partilhar PDF")
                }
                TextButton(onClick = {
                    scope.launch {
                        val savedId = app.repository.saveBudgetTransactional(current)
                        val full = app.repository.getFullBudget(savedId) ?: return@launch
                        val file = app.pdfGenerator.generate(app.repository.getCompany(), full)
                        val uri = app.pdfGenerator.uriFor(file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                }) { Text("Ver PDF") }
            }
        }
    }
}

@Composable
private fun ArchiveScreen(app: BudgetApplication, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<BudgetStatus?>(null) }
    val rows by app.repository.observeArchive(query, status).collectAsState(initial = emptyList())
    Scaffold(topBar = { AppBar("Arquivo", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Pesquisar cliente ou número", query, tag = "archive_search") { query = it }
            StatusPicker(status, includeAll = true) { status = it }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows) { row -> ArchiveRow(row, app, scope, onOpen) }
            }
        }
    }
}

@Composable
private fun ArchiveRow(row: BudgetArchiveRow, app: BudgetApplication, scope: kotlinx.coroutines.CoroutineScope, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar orçamento?") },
            text = { Text("Esta ação remove o orçamento ${row.budgetNumber}.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { app.repository.deleteBudget(row.id) } }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("${row.budgetNumber} - ${row.clientName}", fontWeight = FontWeight.Bold)
        Text("${LocalDate.ofEpochDay(row.issueDateEpochDay)}  ${formatMoney(row.totalIncludingVatCents)}  ${row.status.displayName}")
        Row {
            TextButton(onClick = { onOpen(row.id) }) { Text("Abrir") }
            TextButton(onClick = { scope.launch { onOpen(app.repository.duplicateBudget(row.id)) } }) { Text("Duplicar") }
            TextButton(onClick = {
                scope.launch {
                    val full = app.repository.getFullBudget(row.id) ?: return@launch
                    val file = app.pdfGenerator.generate(app.repository.getCompany(), full)
                    val uri = app.pdfGenerator.uriFor(file)
                    context.startActivity(Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            }) { Text("Partilhar") }
            TextButton(onClick = {
                scope.launch {
                    val full = app.repository.getFullBudget(row.id) ?: return@launch
                    val file = app.pdfGenerator.generate(app.repository.getCompany(), full)
                    val uri = app.pdfGenerator.uriFor(file)
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            }) { Text("Ver") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Apagar") }
        }
    }
}

@Composable
private fun BackupScreen(app: BudgetApplication, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    app.repository.importFromZip(stream)
                } ?: throw IllegalStateException("Não foi possível abrir o ficheiro")
            }.onSuccess { result ->
                val parts = mutableListOf<String>()
                if (result.imported > 0) parts.add("${result.imported} importados")
                if (result.skipped > 0) parts.add("${result.skipped} ignorados (já existiam)")
                message = "Backup restaurado: ${parts.joinToString(", ")} de ${result.total} orçamentos"
            }.onFailure {
                message = "Ficheiro de backup inválido"
            }
        }
    }
    Scaffold(topBar = { AppBar("Backup e restauro", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                scope.launch {
                    val file = app.repository.createBackupZip()
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Partilhar backup"))
                }
            }, modifier = Modifier.fillMaxWidth().testTag("backup_export")) { Text("Exportar backup") }
            Button(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth().testTag("backup_import")) { Text("Importar backup") }
            Text("Ao importar, apenas orçamentos novos são adicionados. Os existentes não são alterados.")
            if (message.isNotBlank()) Text(message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPicker(value: BudgetStatus?, includeAll: Boolean = false, onChange: (BudgetStatus?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value?.displayName ?: "Todos",
            onValueChange = {},
            readOnly = true,
            label = { Text("Estado") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (includeAll) DropdownMenuItem(text = { Text("Todos") }, onClick = { onChange(null); expanded = false })
            BudgetStatus.entries.forEach { status ->
                DropdownMenuItem(text = { Text(status.displayName) }, onClick = { onChange(status); expanded = false })
            }
        }
    }
}

@Composable
private fun LineEditor(line: EditableLine, exemptFromVat: Boolean, onChange: (EditableLine) -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Field("Descrição", line.description, Modifier.weight(1f)) { onChange(line.copy(description = it)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Apagar linha") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field("Qtd", line.quantity, Modifier.weight(1f)) { onChange(line.copy(quantity = it)) }
            Field("Un.", line.unit, Modifier.weight(1f)) { onChange(line.copy(unit = it)) }
            Field("Preço", line.unitPrice, Modifier.weight(1f)) { onChange(line.copy(unitPrice = it)) }
            if (!exemptFromVat) Field("IVA", line.vatRate, Modifier.weight(1f)) { onChange(line.copy(vatRate = it)) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), tag: String? = null, onChange: (String) -> Unit) {
    val taggedModifier = if (tag == null) modifier else modifier.testTag(tag)
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = taggedModifier.fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) TextButton(onClick = onBack) { Text("Voltar") }
        }
    )
}

private fun parseDate(value: String, fallback: LocalDate): LocalDate =
    try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        fallback
    }
