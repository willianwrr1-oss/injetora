package com.willian.injecao.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.willian.injecao.data.AppDatabase
import com.willian.injecao.data.MaterialEntity
import com.willian.injecao.data.MaterialImporter
import com.willian.injecao.data.toEngine
import com.willian.injecao.engine.Calibracao
import com.willian.injecao.engine.Maquina
import com.willian.injecao.engine.Peca
import com.willian.injecao.engine.ProcessEstimator
import com.willian.injecao.engine.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.obter(app).materialDao()

    val busca = MutableStateFlow("")

    val materiais: StateFlow<List<MaterialEntity>> =
        combine(dao.observarTodos(), busca) { lista, q ->
            if (q.isBlank()) lista
            else lista.filter { m ->
                listOf(m.familia, m.fabricante, m.grade, m.carga ?: "").any { it.contains(q, ignoreCase = true) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selecionado by mutableStateOf<MaterialEntity?>(null)
        private set
    var resultado by mutableStateOf<Resultado?>(null)
        private set
    var erro by mutableStateOf<String?>(null)
        private set
    /** Calibração ativa; a pressão medida na máquina ajusta fatorPressao. */
    var calibracao by mutableStateOf(Calibracao())
        private set

    init {
        viewModelScope.launch { MaterialImporter(app, dao).importarSeNecessario() }
    }

    fun selecionar(m: MaterialEntity) {
        selecionado = m
        resultado = null
        erro = null
    }

    fun calcular(peca: Peca, maquina: Maquina) {
        val m = selecionado ?: run { erro = "Escolha um material na aba Materiais."; return }
        erro = null
        resultado = try {
            ProcessEstimator.estimar(m.toEngine(), peca, maquina, calibracao)
        } catch (e: IllegalArgumentException) {
            erro = e.message
            null
        }
    }

    /** Ajusta o fator de pressão para reproduzir a pressão de enchimento lida na máquina. */
    fun calibrarPelaPressaoMedida(peca: Peca, maquina: Maquina, medidaBar: Double) {
        val m = selecionado ?: return
        val fator = try {
            ProcessEstimator.fatorPressaoPorMedicao(m.toEngine(), peca, maquina, calibracao, medidaBar)
        } catch (e: IllegalArgumentException) {
            erro = e.message
            null
        }
        if (fator == null) {
            erro = erro ?: "Este material não tem dados para estimar a pressão."
            return
        }
        calibracao = calibracao.copy(fatorPressao = fator)
        calcular(peca, maquina)
    }

    fun definirExpoente(n: Double?) {
        val m = selecionado ?: return
        viewModelScope.launch {
            dao.ajustarExpoente(m.id, n)
            selecionado = dao.buscar(m.id)
        }
    }
}
