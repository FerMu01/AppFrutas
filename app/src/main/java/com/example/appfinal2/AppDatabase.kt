package com.example.appfinal2

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Fruit::class],
    version = 2, // Aumenta la versión si cambiaste la estructura
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fruitDao(): FruitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Obtiene la instancia única (singleton) de la base de datos
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fruit_database"
                )
                    // Borra y recrea la base de datos si cambia la versión
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
