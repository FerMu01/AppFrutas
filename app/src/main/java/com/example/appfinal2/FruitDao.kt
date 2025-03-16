package com.example.appfinal2

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FruitDao {
    @Query("SELECT * FROM fruits ORDER BY calories DESC LIMIT 3")
    fun getTop3ByCalories(): List<Fruit>

    // Inserta una lista de frutas (reemplaza en caso de conflicto)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFruits(fruits: List<Fruit>)

    // Inserta una fruta individual
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFruit(fruit: Fruit)

    // Elimina una fruta
    @Delete
    fun deleteFruit(fruit: Fruit)

    // Consulta todas las frutas almacenadas, ordenadas por apiIndex
    @Query("SELECT * FROM fruits ORDER BY apiIndex ASC")
    fun getAllFruits(): List<Fruit>
}
