package com.willian.injecao.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valores esperados conferidos contra uma segunda implementação independente (Python) das mesmas
 * fórmulas. Os testes validam a implementação, não a física: a precisão do modelo precisa ser
 * medida contra casos reais da máquina.
 */
class ProcessEstimatorTest {

    // Celcon M90 (Celanese), dados do datasheet UL Prospector
    private val celcon = Material(
        id = "POM-CELANESE-CELCON-M90", familia = "POM", grade = "Celcon M90",
        densidadeGCm3 = 1.41, mvrCm3_10min = 8.0, mvrCondicao = "190°C/2.16 kg",
        tFundidoMinC = 180.0, tFundidoMaxC = 190.0, tMoldeMinC = 80.0, tMoldeMaxC = 120.0,
        zonaTraseiraMinC = 170.0, zonaTraseiraMaxC = 180.0,
        densidadeFundidoGCm3 = 1.20, calorEspecificoFundidoJKgC = 2210.0, condutividadeFundidoWMK = 0.16,
        tExtracaoC = 140.0, contraPressaoMaxMpa = 4.0
    )

    private val peca = Peca(volumeCm3 = 10.0, areaProjetadaCm2 = 20.0, espessuraMm = 2.0, comprimentoFluxoMm = 100.0)
    private val maquina = Maquina(diametroRoscaMm = 30.0, pressaoMaxPlasticaBar = 1500.0, forcaFechamentoKn = 500.0)

    @Test
    fun celcon_valoresDeReferencia() {
        val r = ProcessEstimator.estimar(celcon, peca, maquina)
        assertEquals(185.0, r.temperaturaFundido.valor!!, 1e-9)
        assertEquals(100.0, r.temperaturaMolde.valor!!, 1e-9)
        assertEquals(0.3, r.tempoEnchimentoS.valor!!, 1e-9)
        assertEquals(47.157020, r.velocidadeInjecaoMmS.valor!!, 1e-4)
        assertEquals(159.785366, r.pressaoEnchimentoBar.valor!!, 1e-3)
        assertEquals(6.686265, r.tempoResfriamentoS.valor!!, 1e-4)
        assertEquals(12.986265, r.tempoCicloS.valor!!, 1e-4)
        assertEquals(21.091668, r.forcaFechamentoKn.valor!!, 1e-3)
        assertEquals(19.147106, r.cursoDoseMm.valor!!, 1e-4)
        assertEquals(5.282942, r.posicaoComutacaoMm.valor!!, 1e-4)
        assertEquals(127.323954, r.rotacaoRoscaRpm.valor!!, 1e-4)
        assertEquals(40.0, r.contrapressaoBar.valor!!, 1e-9)
    }

    @Test
    fun origemDosValores_ficaExplicita() {
        val r = ProcessEstimator.estimar(celcon, peca, maquina)
        assertEquals(Origem.CALCULADO, r.temperaturaFundido.origem) // ponto médio da faixa
        assertEquals(Origem.BANCO, r.contrapressaoBar.origem)
        assertEquals(Origem.HEURISTICA, r.pressaoRecalqueBar.origem)
        assertEquals(Origem.HEURISTICA, r.zonaMedia.origem) // sem zona média no datasheet
        assertEquals(Origem.CALCULADO, r.zonaTraseira.origem) // ponto médio de 170-180
        assertEquals(175.0, r.zonaTraseira.valor!!, 1e-9)
    }

    @Test
    fun semMvr_pressaoFicaFaltante_eNaoInventaValor() {
        val m = celcon.copy(mvrCm3_10min = null, mfrG10min = null)
        val r = ProcessEstimator.estimar(m, peca, maquina)
        assertNull(r.pressaoEnchimentoBar.valor)
        assertEquals(Origem.FALTANTE, r.pressaoEnchimentoBar.origem)
        assertNull(r.forcaFechamentoKn.valor)
        assertNull(r.pressaoRecalqueBar.valor)
    }

    @Test
    fun semCondicaoDoMvr_pressaoFicaFaltante() {
        val m = celcon.copy(mvrCondicao = "230°C (carga não exibida na fonte)")
        assertNull(ProcessEstimator.estimar(m, peca, maquina).pressaoEnchimentoBar.valor)
    }

    @Test
    fun semTemperaturaDeExtracaoNemHdt_resfriamentoFicaFaltante() {
        val m = celcon.copy(tExtracaoC = null, hdt18MpaC = null)
        val r = ProcessEstimator.estimar(m, peca, maquina)
        assertNull(r.tempoResfriamentoS.valor)
        assertNull(r.tempoCicloS.valor)
        // o resto do cálculo não depende disso
        assertNotNull(r.pressaoEnchimentoBar.valor)
    }

    @Test
    fun lerCondicaoMvr_formatosDoBanco() {
        val a = ProcessEstimator.lerCondicaoMvr("190°C/2.16 kg")!!
        assertEquals(190.0, a.temperaturaC, 1e-9); assertEquals(2.16, a.cargaKg, 1e-9)
        val b = ProcessEstimator.lerCondicaoMvr("337°C/6.6 kgf (ASTM D1238)")!!
        assertEquals(6.6, b.cargaKg, 1e-9)
        val c = ProcessEstimator.lerCondicaoMvr("280°C/2.16 kg (MFR)")!!
        assertEquals(280.0, c.temperaturaC, 1e-9)
        assertNull(ProcessEstimator.lerCondicaoMvr("230°C (carga não exibida na fonte)"))
        assertNull(ProcessEstimator.lerCondicaoMvr(null))
    }

    @Test
    fun fatorPressao_escalaLinearmenteAPressao() {
        val base = ProcessEstimator.estimar(celcon, peca, maquina).pressaoEnchimentoBar.valor!!
        val dobro = ProcessEstimator.estimar(celcon, peca, maquina, Calibracao(fatorPressao = 2.0)).pressaoEnchimentoBar.valor!!
        assertEquals(2.0 * base, dobro, 1e-6)
    }

    @Test
    fun calibracaoPorMedicao_reproduzAMedicao() {
        val medido = 220.0
        val fator = ProcessEstimator.fatorPressaoPorMedicao(celcon, peca, maquina, Calibracao(), medido)!!
        val recalculado = ProcessEstimator.estimar(celcon, peca, maquina, Calibracao(fatorPressao = fator))
            .pressaoEnchimentoBar.valor!!
        assertEquals(medido, recalculado, 1e-6)
    }

    @Test
    fun fatorTempoEnchimento_maiorDeixaOEnchimentoMaisLentoEBaixaAPressao() {
        val normal = ProcessEstimator.estimar(celcon, peca, maquina)
        val lento = ProcessEstimator.estimar(celcon, peca, maquina, Calibracao(fatorTempoEnchimento = 2.0))
        assertEquals(2.0 * normal.tempoEnchimentoS.valor!!, lento.tempoEnchimentoS.valor!!, 1e-9)
        assertTrue(lento.pressaoEnchimentoBar.valor!! < normal.pressaoEnchimentoBar.valor!!)
    }

    @Test
    fun avisos_pressaoAcimaDoLimiteDaMaquina() {
        val fraca = maquina.copy(pressaoMaxPlasticaBar = 100.0)
        val r = ProcessEstimator.estimar(celcon, peca, fraca)
        assertTrue(r.avisos.any { it.contains("acima do limite da máquina") })
    }

    @Test
    fun avisos_forcaAcimaDaMaquina() {
        val pequena = maquina.copy(forcaFechamentoKn = 10.0)
        val r = ProcessEstimator.estimar(celcon, peca, pequena)
        assertTrue(r.avisos.any { it.contains("Força de fechamento") })
    }

    @Test
    fun entradasInvalidas_lancamExcecao() {
        var lancou = false
        try {
            ProcessEstimator.estimar(celcon, peca.copy(espessuraMm = 0.0), maquina)
        } catch (e: IllegalArgumentException) {
            lancou = true
        }
        assertTrue(lancou)
    }

    @Test
    fun mfrSemDensidadeDoFundido_usaSolidaEAvisa() {
        val m = celcon.copy(mvrCm3_10min = null, mfrG10min = 9.0, densidadeFundidoGCm3 = null)
        val r = ProcessEstimator.estimar(m, peca, maquina)
        assertNotNull(r.pressaoEnchimentoBar.valor)
        assertTrue(r.avisos.any { it.contains("densidade sólida") })
    }

    @Test
    fun resfriamento_temperaturaDeExtracaoForaDaFaixa_naoCalcula() {
        val m = celcon.copy(tExtracaoC = 300.0) // acima do fundido
        val r = ProcessEstimator.estimar(m, peca, maquina)
        assertNull(r.tempoResfriamentoS.valor)
        assertTrue(r.avisos.any { it.contains("Resfriamento não calculado") })
    }
}
