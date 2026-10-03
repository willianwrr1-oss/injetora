package com.willian.injecao.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materiais ORDER BY familia, fabricante, grade")
    fun observarTodos(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materiais WHERE id = :id")
    suspend fun buscar(id: String): MaterialEntity?

    @Query("SELECT COUNT(*) FROM materiais")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(materiais: List<MaterialEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirUm(material: MaterialEntity)

    /** Guarda o expoente n calibrado pelo usuário para um material. */
    @Query("UPDATE materiais SET expoentePotencia = :n WHERE id = :id")
    suspend fun ajustarExpoente(id: String, n: Double?)
}
