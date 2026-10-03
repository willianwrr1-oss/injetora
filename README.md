# Injeção: ponto de partida

App Android que sugere uma configuração inicial para a injetora (temperaturas, secagem, dose, velocidade,
pressões, tempos, força de fechamento) a partir de um banco de 50 materiais montado de datasheets públicos.
O GitHub Actions monta e valida os dados, roda os testes do motor e gera o APK.

> Ponto de partida, não simulação. Não substitui Moldflow/Moldex3D nem a validação na máquina. Veja "Limitações".

## Como usar no GitHub

1. Crie um repositório vazio no GitHub (por exemplo `injecao-ponto-de-partida`) e envie esta pasta:

   ```bash
   git init -b main
   git add -A
   git commit -m "primeira versão"
   git remote add origin https://github.com/SEU_USUARIO/injecao-ponto-de-partida.git
   git push -u origin main
   ```

2. Abra a aba **Actions**. O workflow **Montar dados, testar e gerar APK** roda sozinho (leva alguns minutos).
3. Ao terminar, abra a execução e baixe, em **Artifacts**:
   - `apk-debug`: o APK para instalar no celular (permita "instalar apps de fontes desconhecidas").
   - `banco-de-materiais`: `materiais.json` e `materiais.csv` atualizados.
   - `relatorio-de-testes`: relatório dos testes (também sobe se algum falhar).
4. Para publicar uma versão com o APK em **Releases**, crie uma tag:

   ```bash
   git tag v0.1 && git push origin v0.1
   ```

O APK é de **debug** (assinado com a chave de debug do Android). Serve para uso pessoal e testes. Para distribuir
fora do seu círculo, será preciso configurar uma chave de assinatura de release.

## O que o GitHub faz sozinho

| Workflow | Quando roda | O que faz |
|---|---|---|
| `apk.yml` | push em `main`, pull request, tag `v*`, manual | Gera o JSON e o CSV, valida, gera a entidade Room, roda os testes do motor, monta o APK e publica os arquivos. Em tag `v*`, anexa à Release. |
| `dados.yml` | mudança em `dados/montar_banco.py` ou `dados/gerar_entidade.py` | Regenera JSON, CSV e entidade, valida e commita o resultado no repositório. |

Se a validação dos dados falhar, o build para antes de gerar o APK e o log diz qual material e qual campo.

## Acrescentar ou corrigir um material

1. Abra `dados/montar_banco.py`, copie um bloco `add(...)` e preencha com os valores do datasheet
   (id único, fabricante, grade, temperaturas, fonte_url, fonte_tipo e observações). Campo que o datasheet não traz fica de fora:
   o app mostra como **FALTANTE** em vez de usar valor genérico.
2. Faça commit e push. O GitHub regenera os arquivos, valida e gera um novo APK. Como o conteúdo do banco muda, o
   app reimporta sozinho na próxima abertura, preservando o expoente n que você calibrou.

Para rodar localmente (Python 3): `python dados/montar_banco.py && python dados/validar_dados.py && python dados/gerar_entidade.py`.

## Estrutura

- `dados/montar_banco.py` — fonte dos 50 materiais; gera `materiais.json` (app) e `materiais.csv` (conferência).
- `dados/validar_dados.py` — checa IDs, faixas, valores fisicamente plausíveis, fonte e data de cada registro.
- `dados/gerar_entidade.py` — gera `MaterialEntity.kt` do esquema do JSON.
- `app/src/main/java/.../engine` — motor de cálculo em Kotlin puro. `data/` (Room) e `ui/` (Compose) completam o app.
- `app/src/test` — 14 testes JUnit do motor.
- `tools/` — verificador que roda o motor nos 50 materiais.

## Como o motor estima (resumo)

- Viscosidade: lei de potência com K estimado do MVR/MFR do datasheet e n assumido (0,4). É ordem de grandeza, não Cross-WLF.
- Pressão: queda de pressão em canal retangular fino (Rabinowitsch), com fator 1,3 para bico, canais e ponto de injeção.
- Resfriamento: condução em placa plana até a temperatura de extração (ou HDT 1,8 MPa como substituta, com aviso).
- Força de fechamento: área projetada x pressão média na cavidade x 1,1.
- Cada valor mostra a origem: `BANCO`, `CALCULADO`, `HEURISTICA` ou `FALTANTE`.

## Limitações

1. A pressão depende do n assumido. Em materiais quase newtonianos medidos com carga baixa o erro é grande
   (o PC Makrolon 2407 sai com ~63 bar numa placa de 2 x 100 mm, bem abaixo do esperado). **Calibre com a pressão medida
   na máquina** (tela Calculadora, bloco Calibração).
2. Só 25 dos 50 materiais permitem estimar a pressão e só 16 o resfriamento (faltam MVR com carga ou temperatura de extração).
3. Sem Cross-WLF nem PVT: contração, compactação e empenamento não são previstos, só informados do datasheet.
4. Recalque a 60% do enchimento, taxa de cisalhamento de 1000 1/s, velocidade periférica de 0,2 m/s e 6 s de tempo auxiliar
   são regras práticas ajustáveis em `Calibracao`.
5. Alguns valores vêm de cópias de datasheets hospedadas por terceiros ou de revisões antigas (indicado nas observações de
   cada registro). Valide no datasheet vigente antes de usar em produção ou auditoria.

## O que foi e o que não foi testado

Testado fora do GitHub: o motor compila (Kotlin 2.0.21), os 14 testes passam (contra um stub de JUnit), os resultados batem
com uma segunda implementação independente em Python nos 50 materiais, os scripts de dados são idempotentes e o YAML dos
workflows é válido.

**Não testado:** o build Gradle completo, o Room (KSP), o importador de JSON e as telas Compose nunca foram compilados nem
executados. Os workflows ainda não rodaram. A primeira execução no GitHub pode falhar por detalhe de versão ou de código do app;
nesse caso o log do passo `Testes do motor e APK` mostra o erro exato e ele costuma ser pequeno de corrigir.
