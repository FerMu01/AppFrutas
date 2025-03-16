package com.example.appfinal2

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "fruits")
data class Fruit(
    @PrimaryKey val id: Int,
    val name: String,
    val family: String,
    val order: String,
    val genus: String,
    val apiIndex: Int,
    @Embedded val nutritions: Nutrition
)

data class Nutrition(
    val carbohydrates: Double,
    val protein: Double,
    val fat: Double,
    val calories: Int,
    val sugar: Double
)
