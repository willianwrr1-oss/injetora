#!/usr/bin/env python3
"""
Valida o banco de materiais. Sai com código 1 se achar erro (a CI falha).

Erros: ID repetido, faixa invertida, valor recomendado fora da faixa, valor fora de limites físicos
plausíveis, registro sem fonte ou sem data, JSON e CSV divergentes.
Avisos (não falham): registros sem temperatura de fundido.

Uso: python dados/validar_dados.py [--json caminho] [--csv caminho]
"""
import argparse
import csv
import json
import sys
from pathlib import Path

PARES_FAIXA = [
    ("temp_fundido_min_c", "temp_fundido_max_c"), ("temp_molde_min_c", "temp_molde_max_c"),
    ("secagem_temp_min_c", "secagem_temp_max_c"), ("secagem_tempo_min_h", "secagem_tempo_max_h"),
    ("zona_traseira_min_c", "zona_traseira_max_c"), ("zona_media_min_c", "zona_media_max_c"),
    ("zona_frontal_min_c", "zona_frontal_max_c"), ("bico_min_c", "bico_max_c"),
    ("contracao_faixa_min_pct", "contracao_faixa_max_pct"),
]
RECOMENDADOS = [
    ("temp_fundido_min_c", "temp_fundido_max_c", "temp_fundido_rec_c"),
    ("temp_molde_min_c", "temp_molde_max_c", "temp_molde_rec_c"),
]
# (campo, mínimo, máximo) plausíveis para polímeros de engenharia
LIMITES = [
    ("densidade_g_cm3", 0.8, 2.2), ("densidade_fundido_g_cm3", 0.6, 2.0),
    ("mfr_g_10min", 0.01, 500), ("mvr_cm3_10min", 0.01, 500),
    ("temp_fusao_c", 80, 400), ("temp_fundido_min_c", 100, 450), ("temp_fundido_max_c", 100, 450),
    ("temp_fundido_rec_c", 100, 450), ("temp_molde_min_c", 0, 250), ("temp_molde_max_c", 0, 250),
    ("temp_molde_rec_c", 0, 250), ("secagem_temp_min_c", 40, 200), ("secagem_temp_max_c", 40, 200),
    ("secagem_tempo_min_h", 0.5, 24), ("secagem_tempo_max_h", 0.5, 24), ("umidade_max_pct", 0.001, 1),
    ("contracao_paralela_pct", 0.0, 5), ("contracao_normal_pct", 0.0, 5),
    ("contracao_faixa_min_pct", 0.0, 5), ("contracao_faixa_max_pct", 0.0, 5),
    ("hdt_1_8mpa_c", 30, 320), ("hdt_0_45mpa_c", 30, 350),
    ("calor_especifico_fundido_j_kg_c", 1000, 4000), ("condutividade_fundido_w_m_k", 0.05, 0.6),
    ("temp_extracao_c", 40, 300), ("contra_pressao_max_mpa", 0, 50),
]


def main():
    raiz = Path(__file__).resolve().parent.parent
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", default=str(raiz / "app/src/main/assets/materiais.json"))
    ap.add_argument("--csv", default=str(raiz / "dados/saida/materiais.csv"))
    args = ap.parse_args()

    banco = json.load(open(args.json, encoding="utf-8"))
    rows = banco["materiais"]
    erros, avisos = [], []

    if banco.get("total") != len(rows):
        erros.append(f"total={banco.get('total')} mas há {len(rows)} materiais")

    ids = [r["id"] for r in rows]
    for i in sorted({x for x in ids if ids.count(x) > 1}):
        erros.append(f"ID repetido: {i}")

    for r in rows:
        rid = r["id"]
        if not str(r.get("fonte_url") or "").startswith("http"):
            erros.append(f"{rid}: sem fonte_url válida")
        if not r.get("fonte_tipo"):
            erros.append(f"{rid}: sem fonte_tipo")
        if not r.get("data_consulta"):
            erros.append(f"{rid}: sem data_consulta")
        if not r.get("observacoes"):
            erros.append(f"{rid}: sem observações")
        for a, b in PARES_FAIXA:
            if r.get(a) is not None and r.get(b) is not None and r[a] > r[b]:
                erros.append(f"{rid}: {a}={r[a]} maior que {b}={r[b]}")
        for lo, hi, rec in RECOMENDADOS:
            if None not in (r.get(lo), r.get(hi), r.get(rec)) and not (r[lo] <= r[rec] <= r[hi]):
                erros.append(f"{rid}: {rec}={r[rec]} fora da faixa {r[lo]}–{r[hi]}")
        for campo, mn, mx in LIMITES:
            v = r.get(campo)
            if v is not None and not (mn <= v <= mx):
                erros.append(f"{rid}: {campo}={v} fora do intervalo plausível {mn}–{mx}")
        if all(r.get(c) is None for c in ("temp_fundido_min_c", "temp_fundido_max_c", "temp_fundido_rec_c")):
            avisos.append(f"{rid}: sem temperatura de fundido (registro incompleto)")
        # contrapressão e MVR precisam de condição de ensaio para o motor estimar pressão
        if (r.get("mvr_cm3_10min") is not None or r.get("mfr_g_10min") is not None) and not r.get("mvr_condicao"):
            avisos.append(f"{rid}: MVR/MFR sem condição de ensaio (o app não estima pressão)")

    # CSV deve concordar com o JSON
    if Path(args.csv).exists():
        linhas = list(csv.DictReader(open(args.csv, encoding="utf-8-sig"), delimiter=";"))
        if [l["id"] for l in linhas] != ids:
            erros.append("CSV e JSON têm IDs ou ordem diferentes")
        else:
            for l, r in zip(linhas, rows):
                for k, v in r.items():
                    if isinstance(v, float) and l.get(k, "") != "" and abs(float(l[k]) - v) > 1e-9:
                        erros.append(f"{r['id']}: {k} difere entre CSV ({l[k]}) e JSON ({v})")
    else:
        avisos.append("CSV não encontrado; conferência CSV x JSON ignorada")

    print(f"{len(rows)} materiais verificados")
    for a in avisos:
        print("AVISO:", a)
    for e in erros:
        print("ERRO :", e)
    if erros:
        sys.exit(1)
    print("Validação OK")


if __name__ == "__main__":
    main()
