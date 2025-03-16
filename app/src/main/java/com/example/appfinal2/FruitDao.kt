package com.example.appfinal2

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FruitDao {
    // Inserta una lista de frutas (reemplaza en caso de conflicto)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFruits(fruits: List<Fruit>)

    // Elimina una fruta
    @Delete
    fun deleteFruit(fruit: Fruit)

    // Consulta todas las frutas almacenadas, ordenadas por apiIndex
    @Query("SELECT * FROM fruits ORDER BY apiIndex ASC")
    fun getAllFruits(): List<Fruit>
}
