package com.willian.injecao.engine

/** De onde veio cada número. O app deve mostrar isso ao usuário. */
enum class Origem {
    /** Valor lido direto do datasheet cadastrado. */
    BANCO,
    /** Calculado por fórmula a partir de dados do banco ou da entrada. */
    CALCULADO,
    /** Regra prática ou valor padrão assumido. Precisa de calibração. */
    HEURISTICA,
    /** Não há dado para calcular. Nenhum valor foi inventado. */
    FALTANTE
}

data class Estimativa(
    val valor: Double?,
    val unidade: String,
    val origem: Origem,
    val nota: String = ""
) {
    companion object {
        fun faltante(unidade: String, nota: String) = Estimativa(null, unidade, Origem.FALTANTE, nota)
    }
}

/** Dados de material que o motor usa. Nulo = não encontrado no datasheet. */
data class Material(
    val id: String,
    val familia: String,
    val grade: String,
    val densidadeGCm3: Double? = null,
    val densidadeFundidoGCm3: Double? = null,
    val mfrG10min: Double? = null,
    val mvrCm3_10min: Double? = null,
    /** Texto como no banco, ex.: "190°C/2.16 kg". */
    val mvrCondicao: String? = null,
    val tFusaoC: Double? = null,
    val tFundidoMinC: Double? = null,
    val tFundidoMaxC: Double? = null,
    val tFundidoRecC: Double? = null,
    val tMoldeMinC: Double? = null,
    val tMoldeMaxC: Double? = null,
    val tMoldeRecC: Double? = null,
    val secagemTempMinC: Double? = null,
    val secagemTempMaxC: Double? = null,
    val secagemTempoMinH: Double? = null,
    val secagemTempoMaxH: Double? = null,
    val umidadeMaxPct: Double? = null,
    val zonaTraseiraMinC: Double? = null,
    val zonaTraseiraMaxC: Double? = null,
    val zonaMediaMinC: Double? = null,
    val zonaMediaMaxC: Double? = null,
    val zonaFrontalMinC: Double? = null,
    val zonaFrontalMaxC: Double? = null,
    val bicoMinC: Double? = null,
    val bicoMaxC: Double? = null,
    val contraPressaoMaxMpa: Double? = null,
    val contracaoParalelaPct: Double? = null,
    val contracaoNormalPct: Double? = null,
    val hdt18MpaC: Double? = null,
    val calorEspecificoFundidoJKgC: Double? = null,
    val condutividadeFundidoWMK: Double? = null,
    val tExtracaoC: Double? = null,
    /** Expoente da lei de potência cadastrado pelo usuário (calibração por material). */
    val expoentePotenciaUsuario: Double? = null
)

data class Maquina(
    val diametroRoscaMm: Double,
    /** Pressão máxima no plástico (não a hidráulica), em bar. */
    val pressaoMaxPlasticaBar: Double,
    val forcaFechamentoKn: Double,
    val velocidadeMaxInjecaoMmS: Double? = null
)

data class Peca(
    val volumeCm3: Double,
    /** Área projetada de UMA peça, em cm². */
    val areaProjetadaCm2: Double,
    /** Espessura nominal de parede, em mm. */
    val espessuraMm: Double,
    /** Maior comprimento de fluxo do ponto de injeção até o fim, em mm. */
    val comprimentoFluxoMm: Double,
    val cavidades: Int = 1,
    /** Volume de canais frios por ciclo (sprue + runner). 0 em câmara quente. */
    val volumeCanaisCm3: Double = 0.0,
    val espessuraGateMm: Double? = null
)

/** Ajustes e suposições. Os valores padrão são pontos de partida, não verdades. */
data class Calibracao(
    val fatorPressao: Double = 1.0,
    val fatorTempoEnchimento: Double = 1.0,
    /** Expoente n da lei de potência quando o material não tem n cadastrado. */
    val expoentePotencia: Double = 0.4,
    /** Taxa de cisalhamento aparente alvo na parede durante o enchimento, 1/s. */
    val taxaCisalhamentoAlvo: Double = 1000.0,
    /** Multiplicador para perdas em bico, canais e ponto de injeção. */
    val fatorPerdasAlimentacao: Double = 1.3,
    /** Pressão de recalque como fração da pressão de enchimento. */
    val fracaoRecalque: Double = 0.6,
    val almofadaMm: Double = 5.0,
    val velocidadePerifericaMs: Double = 0.2,
    /** Abertura, extração e fechamento. */
    val tempoAuxiliarCicloS: Double = 6.0,
    /** Fração do volume da cavidade cheia no ponto de comutação V/P. */
    val fracaoComutacao: Double = 0.98,
    /** Difusividade térmica de reserva, m²/s, quando faltam dados. */
    val difusividadeReservaM2s: Double = 7.0e-8
)

data class Resultado(
    val temperaturaFundido: Estimativa,
    val temperaturaMolde: Estimativa,
    val zonaTraseira: Estimativa,
    val zonaMedia: Estimativa,
    val zonaFrontal: Estimativa,
    val bico: Estimativa,
    val secagemTemperatura: Estimativa,
    val secagemTempo: Estimativa,
    val umidadeMaxima: Estimativa,
    val volumeDoseCm3: Estimativa,
    val massaDoseG: Estimativa,
    val cursoDoseMm: Estimativa,
    val posicaoComutacaoMm: Estimativa,
    val tempoEnchimentoS: Estimativa,
    val velocidadeInjecaoMmS: Estimativa,
    val pressaoEnchimentoBar: Estimativa,
    val pressaoRecalqueBar: Estimativa,
    val tempoRecalqueS: Estimativa,
    val tempoResfriamentoS: Estimativa,
    val tempoCicloS: Estimativa,
    val forcaFechamentoKn: Estimativa,
    val rotacaoRoscaRpm: Estimativa,
    val contrapressaoBar: Estimativa,
    val avisos: List<String>
)
