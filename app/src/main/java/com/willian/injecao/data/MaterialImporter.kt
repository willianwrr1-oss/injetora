package com.willian.injecao.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BancoJson(
    val versao: String,
    @SerialName("data_consulta") val dataConsulta: String,
    val total: Int,
    val materiais: List<MaterialEntity>
)

/**
 * Carrega assets/materiais.json no Room. Só reimporta quando a versão do arquivo muda e
 * preserva o que o usuário ajustou (expoente n e materiais personalizados).
 */
class MaterialImporter(private val context: Context, private val dao: MaterialDao) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importarSeNecessario() = withContext(Dispatchers.IO) {
        val texto = context.assets.open("materiais.json").bufferedReader().use { it.readText() }
        val banco = json.decodeFromString<BancoJson>(texto)

        val prefs = context.getSharedPreferences("banco_materiais", Context.MODE_PRIVATE)
        if (prefs.getString("versao", null) == banco.versao && dao.contar() > 0) return@withContext

        val preservados = banco.materiais.map { novo ->
            val anterior = dao.buscar(novo.id)
            novo.copy(
                expoentePotencia = anterior?.expoentePotencia,
                personalizado = anterior?.personalizado ?: false
            )
        }
        dao.inserir(preservados)
        prefs.edit().putString("versao", banco.versao).apply()
    }
}
