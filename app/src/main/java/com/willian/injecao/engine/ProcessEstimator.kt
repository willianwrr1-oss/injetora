package com.willian.injecao.engine

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Estimador de ponto de partida para o processo de injeção.
 *
 * Modelos simplificados (1D, sem malha), pensados para dar uma primeira configuração.
 * Não substitui simulação (Moldflow, Moldex3D) nem validação na máquina.
 *
 * Regras do motor:
 *  - Nada é inventado: sem dado, o campo sai como Origem.FALTANTE.
 *  - Cada valor diz de onde veio (BANCO, CALCULADO, HEURISTICA, FALTANTE).
 *  - A viscosidade vem de uma estimativa de lei de potência a partir do MVR/MFR do
 *    datasheet. Isso é uma ordem de grandeza (um ponto em baixa taxa de cisalhamento
 *    extrapolado com n assumido), não um ajuste Cross-WLF.
 */
object ProcessEstimator {

    // Geometria do ensaio de índice de fluidez (ISO 1133): matriz Ø 2,095 x 8 mm, cilindro Ø 9,55 mm
    private const val RAIO_MATRIZ_MM = 1.0475
    private const val COMPRIMENTO_MATRIZ_MM = 8.0
    private const val RAIO_CILINDRO_MM = 4.775
    private const val G = 9.80665

    internal data class CondicaoMvr(val temperaturaC: Double, val cargaKg: Double)

    internal data class Reologia(
        val kPaSn: Double,
        val n: Double,
        val etaRefPaS: Double,
        val gammaRefPorS: Double
    )

    /** Lê textos como "190°C/2.16 kg" ou "337°C/6.6 kgf (ASTM D1238)". Devolve null se faltar a carga. */
    internal fun lerCondicaoMvr(texto: String?): CondicaoMvr? {
        if (texto == null) return null
        val regex = Regex("""(\d+(?:[.,]\d+)?)\s*°C\s*/\s*(\d+(?:[.,]\d+)?)\s*kg""")
        val m = regex.find(texto) ?: return null
        val t = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val c = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
        return CondicaoMvr(t, c)
    }

    /**
     * Viscosidade de referência a partir do MVR (ou MFR convertido) e da carga do ensaio.
     * Taxa aparente na matriz: 4Q/(pi R^3). Tensão: P R / (2 L), com P = carga / área do pistão.
     */
    internal fun reologiaPorMvr(m: Material, n: Double, avisos: MutableList<String>): Reologia? {
        val cond = lerCondicaoMvr(m.mvrCondicao) ?: return null

        val mvr: Double = when {
            m.mvrCm3_10min != null -> m.mvrCm3_10min
            m.mfrG10min != null -> {
                val rho = m.densidadeFundidoGCm3 ?: m.densidadeGCm3 ?: return null
                if (m.densidadeFundidoGCm3 == null) {
                    avisos += "MFR convertido em MVR com a densidade sólida (o fundido é ~10-20% menos denso): viscosidade menos confiável."
                }
                m.mfrG10min / rho
            }
            else -> return null
        }
        if (mvr <= 0.0) return null

        val vazaoMm3PorS = mvr * 1000.0 / 600.0
        val gamma = 4.0 * vazaoMm3PorS / (PI * RAIO_MATRIZ_MM.pow(3))
        val areaPistaoMm2 = PI * RAIO_CILINDRO_MM * RAIO_CILINDRO_MM
        val pressaoMpa = cond.cargaKg * G / areaPistaoMm2
        val tauMpa = pressaoMpa * RAIO_MATRIZ_MM / (2.0 * COMPRIMENTO_MATRIZ_MM)
        val eta = tauMpa * 1.0e6 / gamma
        val k = eta * gamma.pow(1.0 - n)
        return Reologia(k, n, eta, gamma)
    }

    private fun faixa(
        rec: Double?, min: Double?, max: Double?, unidade: String, nome: String
    ): Estimativa = when {
        rec != null -> Estimativa(rec, unidade, Origem.BANCO, "$nome recomendado no datasheet")
        min != null && max != null ->
            Estimativa((min + max) / 2.0, unidade, Origem.CALCULADO, "ponto médio da faixa $min–$max do datasheet")
        min != null -> Estimativa(min, unidade, Origem.BANCO, "só o limite inferior consta no datasheet")
        max != null -> Estimativa(max, unidade, Origem.BANCO, "só o limite superior consta no datasheet")
        else -> Estimativa.faltante(unidade, "$nome não consta no datasheet cadastrado")
    }

    private fun faixaSimples(min: Double?, max: Double?, unidade: String, nome: String): Estimativa = when {
        min != null && max != null && min != max ->
            Estimativa((min + max) / 2.0, unidade, Origem.CALCULADO, "ponto médio da faixa $min–$max do datasheet")
        min != null -> Estimativa(min, unidade, Origem.BANCO, "valor do datasheet")
        max != null -> Estimativa(max, unidade, Origem.BANCO, "valor do datasheet")
        else -> Estimativa.faltante(unidade, "$nome não consta no datasheet cadastrado")
    }

    /** Tempo para o centro de uma placa de espessura h chegar à temperatura de extração. */
    internal fun tempoResfriamentoS(
        espessuraMm: Double, tFundidoC: Double, tMoldeC: Double, tExtracaoC: Double, difusividadeM2s: Double
    ): Double? {
        if (tFundidoC <= tMoldeC || tExtracaoC <= tMoldeC || tExtracaoC >= tFundidoC) return null
        val arg = (4.0 / PI) * (tFundidoC - tMoldeC) / (tExtracaoC - tMoldeC)
        if (arg <= 1.0) return null
        val h = espessuraMm / 1000.0
        return h * h / (PI * PI * difusividadeM2s) * ln(arg)
    }

    /**
     * Calibração por medição: dado o pico de pressão de enchimento lido na máquina com este
     * material, peça e ajuste, devolve o fatorPressao que faz o motor reproduzir a medição.
     * Devolve null se o material não tem dados para estimar a pressão.
     */
    fun fatorPressaoPorMedicao(
        m: Material, p: Peca, maq: Maquina, cal: Calibracao, pressaoMedidaBar: Double
    ): Double? {
        require(pressaoMedidaBar > 0) { "A pressão medida deve ser positiva" }
        val base = estimar(m, p, maq, cal.copy(fatorPressao = 1.0)).pressaoEnchimentoBar.valor ?: return null
        return pressaoMedidaBar / base
    }

    fun estimar(
        m: Material,
        p: Peca,
        maq: Maquina,
        cal: Calibracao = Calibracao()
    ): Resultado {
        require(p.volumeCm3 > 0 && p.areaProjetadaCm2 > 0) { "Volume e área projetada devem ser positivos" }
        require(p.espessuraMm > 0 && p.comprimentoFluxoMm > 0) { "Espessura e comprimento de fluxo devem ser positivos" }
        require(p.cavidades >= 1) { "Número de cavidades deve ser >= 1" }
        require(maq.diametroRoscaMm > 0) { "Diâmetro da rosca deve ser positivo" }

        val avisos = mutableListOf<String>()

        // ---------- temperaturas e secagem (dados do banco) ----------
        val tFundido = faixa(m.tFundidoRecC, m.tFundidoMinC, m.tFundidoMaxC, "°C", "Temperatura do fundido")
        val tMolde = faixa(m.tMoldeRecC, m.tMoldeMinC, m.tMoldeMaxC, "°C", "Temperatura do molde")

        fun zona(min: Double?, max: Double?, nome: String, desvio: Double): Estimativa {
            if (min != null || max != null) return faixaSimples(min, max, "°C", nome)
            val f = tFundido.valor ?: return Estimativa.faltante("°C", "$nome: sem zonas no datasheet e sem temperatura de fundido")
            return Estimativa(f + desvio, "°C", Origem.HEURISTICA, "fundido $desvio°C; perfil crescente assumido, ajustar na máquina")
        }
        val zTras = zona(m.zonaTraseiraMinC, m.zonaTraseiraMaxC, "Zona traseira", -20.0)
        val zMed = zona(m.zonaMediaMinC, m.zonaMediaMaxC, "Zona média", -10.0)
        val zFro = zona(m.zonaFrontalMinC, m.zonaFrontalMaxC, "Zona frontal", 0.0)
        val bico = zona(m.bicoMinC, m.bicoMaxC, "Bico", 0.0)

        val secT = faixaSimples(m.secagemTempMinC, m.secagemTempMaxC, "°C", "Temperatura de secagem")
        val secH = faixaSimples(m.secagemTempoMinH, m.secagemTempoMaxH, "h", "Tempo de secagem")
        val umid = if (m.umidadeMaxPct != null)
            Estimativa(m.umidadeMaxPct, "%", Origem.BANCO, "umidade máxima no processamento")
        else Estimativa.faltante("%", "umidade máxima não consta no datasheet")

        // ---------- dose, curso e posições (geometria) ----------
        val vShot = p.cavidades * p.volumeCm3 + p.volumeCanaisCm3
        val areaRoscaMm2 = PI * maq.diametroRoscaMm * maq.diametroRoscaMm / 4.0
        val cursoMm = vShot * 1000.0 / areaRoscaMm2
        val posDose = cursoMm + cal.almofadaMm
        val posCom = cal.almofadaMm + (1.0 - cal.fracaoComutacao) * cursoMm

        val massa = if (m.densidadeGCm3 != null)
            Estimativa(vShot * m.densidadeGCm3, "g", Origem.CALCULADO, "volume x densidade sólida (peças + canais)")
        else Estimativa.faltante("g", "densidade não consta no datasheet")

        if (cursoMm > 3.0 * maq.diametroRoscaMm)
            avisos += "Curso de dosagem de %.0f mm passa de 3xD (%.0f mm): confira a capacidade da injetora.".format(cursoMm, 3.0 * maq.diametroRoscaMm)
        if (cursoMm < 1.0 * maq.diametroRoscaMm)
            avisos += "Curso de dosagem de %.0f mm é menor que 1xD: dose pequena para esta rosca, risco de variação de peso e de degradação por tempo de residência.".format(cursoMm)

        // ---------- enchimento: velocidade, pressão (lei de potência) ----------
        val n = m.expoentePotenciaUsuario ?: cal.expoentePotencia
        val gammaEfetiva = cal.taxaCisalhamentoAlvo / cal.fatorTempoEnchimento
        val vFrenteMmS = gammaEfetiva * p.espessuraMm / 6.0
        val tEnch = p.comprimentoFluxoMm / vFrenteMmS
        val tempoEnchimento = Estimativa(
            tEnch, "s", Origem.HEURISTICA,
            "taxa de cisalhamento aparente alvo de %.0f 1/s na parede; ajuste pelo estudo de viscosidade".format(gammaEfetiva)
        )
        val vazaoMm3S = vShot * 1000.0 / tEnch
        val vInj = vazaoMm3S / areaRoscaMm2
        val velocidadeInjecao = Estimativa(vInj, "mm/s", Origem.CALCULADO, "vazão = dose / tempo de enchimento, dividida pela área da rosca")

        if (maq.velocidadeMaxInjecaoMmS != null && vInj > maq.velocidadeMaxInjecaoMmS)
            avisos += "Velocidade de injeção calculada (%.0f mm/s) acima do máximo da máquina (%.0f mm/s): o enchimento será mais lento que o alvo.".format(vInj, maq.velocidadeMaxInjecaoMmS)

        val reo = reologiaPorMvr(m, n, avisos)
        var pFillBar: Double? = null
        val pressaoEnchimento: Estimativa
        if (reo != null) {
            val gammaVerd = (2.0 * n + 1.0) / (3.0 * n) * gammaEfetiva
            val tau = reo.kPaSn * gammaVerd.pow(n)
            val dpPa = 2.0 * tau * p.comprimentoFluxoMm / p.espessuraMm
            pFillBar = dpPa / 1.0e5 * cal.fatorPerdasAlimentacao * cal.fatorPressao
            pressaoEnchimento = Estimativa(
                pFillBar, "bar", Origem.CALCULADO,
                "lei de potência com n=%.2f assumido, K a partir do MVR; sem correção de temperatura. Ordem de grandeza, erro de ±30%% ou mais.".format(n)
            )
            avisos += "Pressão estimada com n=%.2f (suposição, não é medido). Calibre com o estudo de viscosidade na máquina.".format(n)
            if (pFillBar > maq.pressaoMaxPlasticaBar)
                avisos += "Pressão de enchimento estimada (%.0f bar) acima do limite da máquina (%.0f bar).".format(pFillBar, maq.pressaoMaxPlasticaBar)
            else if (pFillBar > 0.8 * maq.pressaoMaxPlasticaBar)
                avisos += "Pressão de enchimento estimada (%.0f bar) passa de 80%% do limite da máquina (%.0f bar): pouca margem.".format(pFillBar, maq.pressaoMaxPlasticaBar)
        } else {
            pressaoEnchimento = Estimativa.faltante(
                "bar", "sem MVR/MFR com carga e temperatura de ensaio no datasheet: não dá para estimar a viscosidade"
            )
        }

        // ---------- recalque e força de fechamento ----------
        val pHoldBar = pFillBar?.let { it * cal.fracaoRecalque }
        val pressaoRecalque = if (pHoldBar != null)
            Estimativa(pHoldBar, "bar", Origem.HEURISTICA, "%.0f%% da pressão de enchimento; semi-cristalinos costumam pedir mais".format(cal.fracaoRecalque * 100))
        else Estimativa.faltante("bar", "depende da pressão de enchimento")

        val forca = if (pFillBar != null && pHoldBar != null) {
            val pCav = max(pHoldBar, 0.5 * pFillBar)
            val kn = p.areaProjetadaCm2 * p.cavidades * pCav / 100.0 * 1.1
            if (kn > maq.forcaFechamentoKn)
                avisos += "Força de fechamento estimada (%.0f kN) acima da máquina (%.0f kN).".format(kn, maq.forcaFechamentoKn)
            Estimativa(kn, "kN", Origem.HEURISTICA, "área projetada x pressão média na cavidade x 1,1; pressão média = maior entre recalque e 50% do enchimento")
        } else Estimativa.faltante("kN", "depende da pressão de enchimento")

        // ---------- resfriamento, selagem do ponto de injeção, ciclo ----------
        val alfa: Double
        val notaAlfa: String
        if (m.condutividadeFundidoWMK != null && m.densidadeFundidoGCm3 != null && m.calorEspecificoFundidoJKgC != null) {
            alfa = m.condutividadeFundidoWMK / (m.densidadeFundidoGCm3 * 1000.0 * m.calorEspecificoFundidoJKgC)
            notaAlfa = "difusividade k/(rho cp) com dados do datasheet"
        } else {
            alfa = cal.difusividadeReservaM2s
            notaAlfa = "difusividade genérica de %.1e m²/s (faltam k, rho ou cp do fundido)".format(cal.difusividadeReservaM2s)
        }
        val tExt = m.tExtracaoC ?: m.hdt18MpaC
        val notaExt = if (m.tExtracaoC != null) "temperatura de extração do datasheet"
        else "HDT a 1,8 MPa usada como substituta da temperatura de extração: conferir"

        var tCool: Double? = null
        var tSeal: Double? = null
        val tf = tFundido.valor
        val tm = tMolde.valor
        if (tf != null && tm != null && tExt != null) {
            tCool = tempoResfriamentoS(p.espessuraMm, tf, tm, tExt, alfa)
            val hGate = p.espessuraGateMm ?: (0.6 * p.espessuraMm)
            tSeal = tempoResfriamentoS(hGate, tf, tm, tExt, alfa)
            if (tCool == null) avisos += "Resfriamento não calculado: a temperatura de extração (%.0f °C) precisa estar entre a do molde (%.0f °C) e a do fundido (%.0f °C).".format(tExt, tm, tf)
        }
        val origemCool = if (m.condutividadeFundidoWMK != null && m.tExtracaoC != null) Origem.CALCULADO else Origem.HEURISTICA

        val resfriamento = if (tCool != null)
            Estimativa(tCool, "s", origemCool, "placa plana, centro até a extração; $notaAlfa; $notaExt")
        else Estimativa.faltante("s", "faltam temperatura de fundido, de molde ou de extração/HDT no datasheet")

        val recalqueTempo = if (tSeal != null)
            Estimativa(tSeal, "s", Origem.HEURISTICA,
                "tempo de congelamento do ponto de injeção (%s); confirmar pelo estudo de selagem do gate (peso x tempo de recalque)".format(
                    if (p.espessuraGateMm != null) "espessura informada" else "espessura assumida de 60% da parede"))
        else Estimativa.faltante("s", "faltam dados de resfriamento")

        val ciclo = if (tCool != null)
            Estimativa(tEnch + max(tSeal ?: 0.0, tCool) + cal.tempoAuxiliarCicloS, "s", Origem.HEURISTICA,
                "enchimento + resfriamento + %.0f s de abertura/extração/fechamento".format(cal.tempoAuxiliarCicloS))
        else Estimativa.faltante("s", "depende do tempo de resfriamento")

        // ---------- dosagem ----------
        val rpm = cal.velocidadePerifericaMs * 60.0 / (PI * maq.diametroRoscaMm / 1000.0)
        val rotacao = Estimativa(rpm, "rpm", Origem.HEURISTICA, "velocidade periférica de %.2f m/s; reduzir para materiais sensíveis ao cisalhamento".format(cal.velocidadePerifericaMs))

        val contra = if (m.contraPressaoMaxMpa != null)
            Estimativa(m.contraPressaoMaxMpa * 10.0, "bar", Origem.BANCO, "máximo recomendado no datasheet; começar abaixo disso")
        else Estimativa.faltante("bar", "contrapressão não consta no datasheet")

        return Resultado(
            temperaturaFundido = tFundido,
            temperaturaMolde = tMolde,
            zonaTraseira = zTras, zonaMedia = zMed, zonaFrontal = zFro, bico = bico,
            secagemTemperatura = secT, secagemTempo = secH, umidadeMaxima = umid,
            volumeDoseCm3 = Estimativa(vShot, "cm³", Origem.CALCULADO, "cavidades x volume da peça + canais frios"),
            massaDoseG = massa,
            cursoDoseMm = Estimativa(posDose, "mm", Origem.CALCULADO, "posição de dosagem = curso da dose + almofada de %.0f mm".format(cal.almofadaMm)),
            posicaoComutacaoMm = Estimativa(posCom, "mm", Origem.HEURISTICA, "comutação V/P com %.0f%% da dose injetada".format(cal.fracaoComutacao * 100)),
            tempoEnchimentoS = tempoEnchimento,
            velocidadeInjecaoMmS = velocidadeInjecao,
            pressaoEnchimentoBar = pressaoEnchimento,
            pressaoRecalqueBar = pressaoRecalque,
            tempoRecalqueS = recalqueTempo,
            tempoResfriamentoS = resfriamento,
            tempoCicloS = ciclo,
            forcaFechamentoKn = forca,
            rotacaoRoscaRpm = rotacao,
            contrapressaoBar = contra,
            avisos = avisos
        )
    }
}
