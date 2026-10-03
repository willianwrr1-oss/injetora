#!/usr/bin/env python3
"""
Gera MaterialEntity.kt (entidade Room + modelo do JSON + mapeamento para o motor) a partir do
esquema de app/src/main/assets/materiais.json. Rode depois de acrescentar ou renomear colunas em
montar_banco.py, para o app e o JSON nunca ficarem fora de sincronia.

Uso: python dados/gerar_entidade.py
"""
import json
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
JSON = RAIZ / "app/src/main/assets/materiais.json"
SAIDA = RAIZ / "app/src/main/java/com/willian/injecao/data/MaterialEntity.kt"

# Campos que o motor de cálculo usa (nome no JSON -> nome em Kotlin).
MOTOR = {
    "densidade_g_cm3": "densidadeGCm3", "densidade_fundido_g_cm3": "densidadeFundidoGCm3",
    "mfr_g_10min": "mfrG10min", "mvr_cm3_10min": "mvrCm3_10min", "mvr_condicao": "mvrCondicao",
    "temp_fusao_c": "tFusaoC",
    "temp_fundido_min_c": "tFundidoMinC", "temp_fundido_max_c": "tFundidoMaxC", "temp_fundido_rec_c": "tFundidoRecC",
    "temp_molde_min_c": "tMoldeMinC", "temp_molde_max_c": "tMoldeMaxC", "temp_molde_rec_c": "tMoldeRecC",
    "secagem_temp_min_c": "secagemTempMinC", "secagem_temp_max_c": "secagemTempMaxC",
    "secagem_tempo_min_h": "secagemTempoMinH", "secagem_tempo_max_h": "secagemTempoMaxH",
    "umidade_max_pct": "umidadeMaxPct",
    "zona_traseira_min_c": "zonaTraseiraMinC", "zona_traseira_max_c": "zonaTraseiraMaxC",
    "zona_media_min_c": "zonaMediaMinC", "zona_media_max_c": "zonaMediaMaxC",
    "zona_frontal_min_c": "zonaFrontalMinC", "zona_frontal_max_c": "zonaFrontalMaxC",
    "bico_min_c": "bicoMinC", "bico_max_c": "bicoMaxC",
    "contra_pressao_max_mpa": "contraPressaoMaxMpa",
    "contracao_paralela_pct": "contracaoParalelaPct", "contracao_normal_pct": "contracaoNormalPct",
    "hdt_1_8mpa_c": "hdt18MpaC", "calor_especifico_fundido_j_kg_c": "calorEspecificoFundidoJKgC",
    "condutividade_fundido_w_m_k": "condutividadeFundidoWMK", "temp_extracao_c": "tExtracaoC",
}


def camel(k):
    if k in MOTOR:
        return MOTOR[k]
    partes = k.split("_")
    return partes[0] + "".join(p.capitalize() for p in partes[1:])


def main():
    rows = json.load(open(JSON, encoding="utf-8"))["materiais"]
    chaves = list(rows[0].keys())

    def tipo(k):
        vals = [r.get(k) for r in rows if r.get(k) is not None]
        if k in ("id", "familia", "fabricante", "grade"):
            return "String", False
        if vals and all(isinstance(v, bool) for v in vals):
            return "Boolean", False
        if vals and all(isinstance(v, (int, float)) and not isinstance(v, bool) for v in vals):
            return "Double", True
        return "String", True

    linhas = [
        "package com.willian.injecao.data", "",
        "import androidx.room.Entity", "import androidx.room.PrimaryKey",
        "import com.willian.injecao.engine.Material",
        "import kotlinx.serialization.SerialName", "import kotlinx.serialization.Serializable", "",
        "/**",
        " * Um material do banco. Os campos vêm de materiais.json (assets) e mantêm os nomes do JSON via @SerialName.",
        " * ARQUIVO GERADO por dados/gerar_entidade.py. Não edite à mão.",
        " * Os dois últimos campos são do usuário e não vêm do JSON.",
        " * Nulo = não encontrado no datasheet (nunca preenchido com valor genérico).",
        " */",
        '@Entity(tableName = "materiais")', "@Serializable", "data class MaterialEntity(",
    ]
    campos = []
    for k in chaves:
        t, nul = tipo(k)
        pk = "    @PrimaryKey " if k == "id" else "    "
        default = " = null" if nul else (" = false" if t == "Boolean" else "")
        campos.append(f'{pk}@SerialName("{k}") val {camel(k)}: {t}{"?" if nul else ""}{default}')
    campos.append("    /** Expoente n da lei de potência ajustado pelo usuário para este material. */")
    campos.append("    val expoentePotencia: Double? = null")
    campos.append("    /** Material cadastrado pelo usuário (não vem do JSON). */")
    campos.append("    val personalizado: Boolean = false")

    idx = [i for i, l in enumerate(campos) if not l.strip().startswith("/**")]
    ultimo = idx[-1]
    for i, l in enumerate(campos):
        linhas.append(l if l.strip().startswith("/**") else l + ("," if i != ultimo else ""))
    linhas += [")", "", "fun MaterialEntity.toEngine() = Material(",
               "    id = id,", "    familia = familia,", "    grade = grade,"]
    for k, v in MOTOR.items():
        if k in chaves:
            linhas.append(f"    {v} = {camel(k)},")
    linhas.append("    expoentePotenciaUsuario = expoentePotencia")
    linhas.append(")")

    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text("\n".join(linhas) + "\n", encoding="utf-8")
    print(f"{len(chaves)} campos do JSON -> {SAIDA.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
