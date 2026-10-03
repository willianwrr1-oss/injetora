package com.willian.injecao.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.willian.injecao.data.MaterialEntity
import com.willian.injecao.engine.Estimativa
import com.willian.injecao.engine.Maquina
import com.willian.injecao.engine.Origem
import com.willian.injecao.engine.Peca

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Raiz() } }
    }
}

@Composable
fun Raiz(vm: MainViewModel = viewModel()) {
    var aba by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = aba == 0, onClick = { aba = 0 }, icon = {}, label = { Text("Materiais") })
                NavigationBarItem(selected = aba == 1, onClick = { aba = 1 }, icon = {}, label = { Text("Calculadora") })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (aba == 0) TelaMateriais(vm, aoEscolher = { vm.selecionar(it); aba = 1 })
            else TelaCalculadora(vm)
        }
    }
}

@Composable
fun TelaMateriais(vm: MainViewModel, aoEscolher: (MaterialEntity) -> Unit) {
    val lista by vm.materiais.collectAsState()
    val busca by vm.busca.collectAsState()
    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(
            value = busca, onValueChange = { vm.busca.value = it },
            label = { Text("Buscar (família, fabricante, grade, carga)") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("${lista.size} materiais", fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lista, key = { it.id }) { m -> CartaoMaterial(m, aoEscolher) }
        }
    }
}

@Composable
fun CartaoMaterial(m: MaterialEntity, aoEscolher: (MaterialEntity) -> Unit) {
    var aberto by remember(m.id) { mutableStateOf(false) }
    val semFundido = m.tFundidoRecC == null && m.tFundidoMinC == null && m.tFundidoMaxC == null
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${m.grade}${m.carga?.let { " ($it)" } ?: ""}", style = MaterialTheme.typography.titleMedium)
            Text("${m.familia} · ${m.fabricante}", fontSize = 13.sp)
            if (semFundido) Text("Dados incompletos: sem temperatura de fundido", color = Color(0xFFB71C1C), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = { aoEscolher(m) }) { Text("Usar") }
                Button(onClick = { aberto = !aberto }) { Text(if (aberto) "Ocultar fonte" else "Ver fonte") }
            }
            if (aberto) {
                Text("Fonte: ${m.fonteTipo ?: "-"}", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Text(m.fonteUrl ?: "", fontSize = 11.sp)
                Text("Consulta: ${m.dataConsulta ?: "-"}", fontSize = 12.sp)
                Text(m.observacoes ?: "", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Faltantes: ${m.camposFaltantes ?: "-"}", fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun TelaCalculadora(vm: MainViewModel) {
    val material = vm.selecionado
    if (material == null) {
        Text("Escolha um material na aba Materiais.", Modifier.padding(16.dp))
        return
    }
    var volume by remember { mutableStateOf("10") }
    var area by remember { mutableStateOf("20") }
    var espessura by remember { mutableStateOf("2") }
    var fluxo by remember { mutableStateOf("100") }
    var cavidades by remember { mutableStateOf("1") }
    var canais by remember { mutableStateOf("0") }
    var rosca by remember { mutableStateOf("30") }
    var pmax by remember { mutableStateOf("1500") }
    var forca by remember { mutableStateOf("500") }
    var medida by remember { mutableStateOf("") }

    fun peca(): Peca? {
        return Peca(
            volumeCm3 = volume.toDoubleOrNull() ?: return null,
            areaProjetadaCm2 = area.toDoubleOrNull() ?: return null,
            espessuraMm = espessura.toDoubleOrNull() ?: return null,
            comprimentoFluxoMm = fluxo.toDoubleOrNull() ?: return null,
            cavidades = cavidades.toIntOrNull() ?: return null,
            volumeCanaisCm3 = canais.toDoubleOrNull() ?: return null
        )
    }
    fun maquina(): Maquina? {
        return Maquina(
            diametroRoscaMm = rosca.toDoubleOrNull() ?: return null,
            pressaoMaxPlasticaBar = pmax.toDoubleOrNull() ?: return null,
            forcaFechamentoKn = forca.toDoubleOrNull() ?: return null
        )
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${material.grade}${material.carga?.let { " ($it)" } ?: ""} · ${material.familia}", style = MaterialTheme.typography.titleMedium)

        Campo("Volume da peça (cm³)", volume) { volume = it }
        Campo("Área projetada de uma peça (cm²)", area) { area = it }
        Campo("Espessura de parede (mm)", espessura) { espessura = it }
        Campo("Comprimento de fluxo (mm)", fluxo) { fluxo = it }
        Campo("Cavidades", cavidades) { cavidades = it }
        Campo("Volume de canais frios (cm³, 0 se câmara quente)", canais) { canais = it }
        Campo("Diâmetro da rosca (mm)", rosca) { rosca = it }
        Campo("Pressão máx. no plástico (bar)", pmax) { pmax = it }
        Campo("Força de fechamento (kN)", forca) { forca = it }

        Button(onClick = {
            val p = peca(); val m = maquina()
            if (p != null && m != null) vm.calcular(p, m)
        }, modifier = Modifier.fillMaxWidth()) { Text("Calcular ponto de partida") }

        vm.erro?.let { Text(it, color = Color(0xFFB71C1C)) }

        vm.resultado?.let { r ->
            Text("Cada valor mostra a origem: BANCO (datasheet), CALCULADO, HEURÍSTICA (regra prática, calibrar) ou FALTANTE.", fontSize = 12.sp)
            Linha("Temperatura do fundido", r.temperaturaFundido)
            Linha("Temperatura do molde", r.temperaturaMolde)
            Linha("Zona traseira", r.zonaTraseira)
            Linha("Zona média", r.zonaMedia)
            Linha("Zona frontal", r.zonaFrontal)
            Linha("Bico", r.bico)
            Linha("Secagem: temperatura", r.secagemTemperatura)
            Linha("Secagem: tempo", r.secagemTempo)
            Linha("Umidade máxima", r.umidadeMaxima)
            Linha("Volume da dose", r.volumeDoseCm3)
            Linha("Massa da dose", r.massaDoseG)
            Linha("Posição de dosagem", r.cursoDoseMm)
            Linha("Posição de comutação V/P", r.posicaoComutacaoMm)
            Linha("Tempo de enchimento", r.tempoEnchimentoS)
            Linha("Velocidade de injeção", r.velocidadeInjecaoMmS)
            Linha("Pressão de enchimento", r.pressaoEnchimentoBar)
            Linha("Pressão de recalque", r.pressaoRecalqueBar)
            Linha("Tempo de recalque (selagem)", r.tempoRecalqueS)
            Linha("Tempo de resfriamento", r.tempoResfriamentoS)
            Linha("Tempo de ciclo", r.tempoCicloS)
            Linha("Força de fechamento", r.forcaFechamentoKn)
            Linha("Rotação da rosca", r.rotacaoRoscaRpm)
            Linha("Contrapressão", r.contrapressaoBar)

            if (r.avisos.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Avisos", style = MaterialTheme.typography.titleSmall)
                        r.avisos.forEach { Text("• $it", fontSize = 12.sp) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Calibração com a máquina", style = MaterialTheme.typography.titleSmall)
                Text("Fator de pressão atual: %.2f".format(vm.calibracao.fatorPressao), fontSize = 12.sp)
                Campo("Pico de pressão de enchimento medido (bar)", medida) { medida = it }
                Button(onClick = {
                    val p = peca(); val m = maquina(); val med = medida.toDoubleOrNull()
                    if (p != null && m != null && med != null && med > 0) vm.calibrarPelaPressaoMedida(p, m, med)
                }) { Text("Calibrar pela pressão medida") }
            }
        }
    }
}

@Composable
private fun Campo(rotulo: String, valor: String, aoMudar: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = aoMudar, label = { Text(rotulo) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Linha(rotulo: String, e: Estimativa) {
    val (texto, cor) = when (e.origem) {
        Origem.BANCO -> "BANCO" to Color(0xFF2E7D32)
        Origem.CALCULADO -> "CALCULADO" to Color(0xFF1565C0)
        Origem.HEURISTICA -> "HEURÍSTICA" to Color(0xFFEF6C00)
        Origem.FALTANTE -> "FALTANTE" to Color(0xFFB71C1C)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rotulo)
            Text(e.valor?.let { "%.1f %s".format(it, e.unidade) } ?: "—")
        }
        Text("$texto · ${e.nota}", color = cor, fontSize = 11.sp)
    }
}
