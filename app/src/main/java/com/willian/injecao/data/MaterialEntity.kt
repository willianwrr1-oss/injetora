package com.willian.injecao.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.willian.injecao.engine.Material
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Um material do banco. Os campos vêm de materiais.json (assets) e mantêm os nomes do JSON via @SerialName.
 * ARQUIVO GERADO por dados/gerar_entidade.py. Não edite à mão.
 * Os dois últimos campos são do usuário e não vêm do JSON.
 * Nulo = não encontrado no datasheet (nunca preenchido com valor genérico).
 */
@Entity(tableName = "materiais")
@Serializable
data class MaterialEntity(
    @PrimaryKey @SerialName("id") val id: String,
    @SerialName("familia") val familia: String,
    @SerialName("fabricante") val fabricante: String,
    @SerialName("grade") val grade: String,
    @SerialName("carga") val carga: String? = null,
    @SerialName("densidade_g_cm3") val densidadeGCm3: Double? = null,
    @SerialName("mfr_g_10min") val mfrG10min: Double? = null,
    @SerialName("mvr_cm3_10min") val mvrCm3_10min: Double? = null,
    @SerialName("mvr_condicao") val mvrCondicao: String? = null,
    @SerialName("temp_fusao_c") val tFusaoC: Double? = null,
    @SerialName("temp_fundido_min_c") val tFundidoMinC: Double? = null,
    @SerialName("temp_fundido_max_c") val tFundidoMaxC: Double? = null,
    @SerialName("temp_fundido_rec_c") val tFundidoRecC: Double? = null,
    @SerialName("temp_molde_min_c") val tMoldeMinC: Double? = null,
    @SerialName("temp_molde_max_c") val tMoldeMaxC: Double? = null,
    @SerialName("temp_molde_rec_c") val tMoldeRecC: Double? = null,
    @SerialName("secagem_temp_min_c") val secagemTempMinC: Double? = null,
    @SerialName("secagem_temp_max_c") val secagemTempMaxC: Double? = null,
    @SerialName("secagem_tempo_min_h") val secagemTempoMinH: Double? = null,
    @SerialName("secagem_tempo_max_h") val secagemTempoMaxH: Double? = null,
    @SerialName("umidade_max_pct") val umidadeMaxPct: Double? = null,
    @SerialName("zona_traseira_min_c") val zonaTraseiraMinC: Double? = null,
    @SerialName("zona_traseira_max_c") val zonaTraseiraMaxC: Double? = null,
    @SerialName("zona_media_min_c") val zonaMediaMinC: Double? = null,
    @SerialName("zona_media_max_c") val zonaMediaMaxC: Double? = null,
    @SerialName("zona_frontal_min_c") val zonaFrontalMinC: Double? = null,
    @SerialName("zona_frontal_max_c") val zonaFrontalMaxC: Double? = null,
    @SerialName("bico_min_c") val bicoMinC: Double? = null,
    @SerialName("bico_max_c") val bicoMaxC: Double? = null,
    @SerialName("contra_pressao_max_mpa") val contraPressaoMaxMpa: Double? = null,
    @SerialName("velocidade_injecao") val velocidadeInjecao: String? = null,
    @SerialName("contracao_paralela_pct") val contracaoParalelaPct: Double? = null,
    @SerialName("contracao_normal_pct") val contracaoNormalPct: Double? = null,
    @SerialName("contracao_faixa_min_pct") val contracaoFaixaMinPct: Double? = null,
    @SerialName("contracao_faixa_max_pct") val contracaoFaixaMaxPct: Double? = null,
    @SerialName("hdt_1_8mpa_c") val hdt18MpaC: Double? = null,
    @SerialName("hdt_0_45mpa_c") val hdt045mpaC: Double? = null,
    @SerialName("densidade_fundido_g_cm3") val densidadeFundidoGCm3: Double? = null,
    @SerialName("calor_especifico_fundido_j_kg_c") val calorEspecificoFundidoJKgC: Double? = null,
    @SerialName("condutividade_fundido_w_m_k") val condutividadeFundidoWMK: Double? = null,
    @SerialName("temp_extracao_c") val tExtracaoC: Double? = null,
    @SerialName("temp_fluxo_c") val tempFluxoC: Double? = null,
    @SerialName("fonte_url") val fonteUrl: String? = null,
    @SerialName("fonte_tipo") val fonteTipo: String? = null,
    @SerialName("data_consulta") val dataConsulta: String? = null,
    @SerialName("observacoes") val observacoes: String? = null,
    @SerialName("campos_faltantes") val camposFaltantes: String? = null,
    @SerialName("cross_wlf_disponivel") val crossWlfDisponivel: Boolean = false,
    @SerialName("pvt_disponivel") val pvtDisponivel: Boolean = false,
    /** Expoente n da lei de potência ajustado pelo usuário para este material. */
    val expoentePotencia: Double? = null,
    /** Material cadastrado pelo usuário (não vem do JSON). */
    val personalizado: Boolean = false
)

fun MaterialEntity.toEngine() = Material(
    id = id,
    familia = familia,
    grade = grade,
    densidadeGCm3 = densidadeGCm3,
    densidadeFundidoGCm3 = densidadeFundidoGCm3,
    mfrG10min = mfrG10min,
    mvrCm3_10min = mvrCm3_10min,
    mvrCondicao = mvrCondicao,
    tFusaoC = tFusaoC,
    tFundidoMinC = tFundidoMinC,
    tFundidoMaxC = tFundidoMaxC,
    tFundidoRecC = tFundidoRecC,
    tMoldeMinC = tMoldeMinC,
    tMoldeMaxC = tMoldeMaxC,
    tMoldeRecC = tMoldeRecC,
    secagemTempMinC = secagemTempMinC,
    secagemTempMaxC = secagemTempMaxC,
    secagemTempoMinH = secagemTempoMinH,
    secagemTempoMaxH = secagemTempoMaxH,
    umidadeMaxPct = umidadeMaxPct,
    zonaTraseiraMinC = zonaTraseiraMinC,
    zonaTraseiraMaxC = zonaTraseiraMaxC,
    zonaMediaMinC = zonaMediaMinC,
    zonaMediaMaxC = zonaMediaMaxC,
    zonaFrontalMinC = zonaFrontalMinC,
    zonaFrontalMaxC = zonaFrontalMaxC,
    bicoMinC = bicoMinC,
    bicoMaxC = bicoMaxC,
    contraPressaoMaxMpa = contraPressaoMaxMpa,
    contracaoParalelaPct = contracaoParalelaPct,
    contracaoNormalPct = contracaoNormalPct,
    hdt18MpaC = hdt18MpaC,
    calorEspecificoFundidoJKgC = calorEspecificoFundidoJKgC,
    condutividadeFundidoWMK = condutividadeFundidoWMK,
    tExtracaoC = tExtracaoC,
    expoentePotenciaUsuario = expoentePotencia
)
