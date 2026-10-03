package tools

import com.willian.injecao.engine.*

fun f(e: Estimativa): String = e.valor?.let { "%.6f".format(java.util.Locale.ROOT, it) } ?: "NA"

fun main() {
    val peca = Peca(volumeCm3 = 10.0, areaProjetadaCm2 = 20.0, espessuraMm = 2.0, comprimentoFluxoMm = 100.0)
    val maq = Maquina(diametroRoscaMm = 30.0, pressaoMaxPlasticaBar = 1500.0, forcaFechamentoKn = 500.0)
    println("id;tFundido;tMolde;pEnch;tEnch;vInj;tResf;forca;ciclo;curso;posCom;rpm;avisos")
    for (m in MATERIAIS_TESTE) {
        val r = ProcessEstimator.estimar(m, peca, maq)
        println(listOf(m.id, f(r.temperaturaFundido), f(r.temperaturaMolde), f(r.pressaoEnchimentoBar),
            f(r.tempoEnchimentoS), f(r.velocidadeInjecaoMmS), f(r.tempoResfriamentoS), f(r.forcaFechamentoKn),
            f(r.tempoCicloS), f(r.cursoDoseMm), f(r.posicaoComutacaoMm), f(r.rotacaoRoscaRpm), r.avisos.size.toString()).joinToString(";"))
    }
}
