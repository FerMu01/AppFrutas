package com.example.appfinal2

import android.app.AlertDialog
import android.os.AsyncTask
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fruitAdapter: FruitAdapter

    // Variable para saber qué fruta se ha pulsado (long-click) en el RecyclerView
    var selectedFruit: Fruit? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configuración del layout y edge-to-edge
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configuración del RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Pasamos 'this' al adapter para manejar el menú contextual
        fruitAdapter = FruitAdapter(emptyList(), this)
        recyclerView.adapter = fruitAdapter

        // Carga inicial de datos desde la base de datos
        LoadFruitsFromDatabaseTask().execute()
    }

    // ---------------------------------------------------------------------------------------------
    // CARGA DE DATOS (AsyncTasks)
    // ---------------------------------------------------------------------------------------------

    /**
     * Carga las frutas de la base de datos.
     * Si no hay datos, se llama a la API.
     */
    inner class LoadFruitsFromDatabaseTask : AsyncTask<Void, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Void?): List<Fruit> {
            val db = AppDatabase.getDatabase(applicationContext)
            return db.fruitDao().getAllFruits()  // Consulta con ORDER BY apiIndex ASC
        }

        override fun onPostExecute(result: List<Fruit>) {
            if (result.isEmpty()) {
                // Si no hay datos, obtenemos de la API
                FetchFruitsTask().execute()
            } else {
                fruitAdapter.updateData(result)
            }
        }
    }

    /**
     * Obtiene la lista de frutas desde la API Fruityvice.
     */
    inner class FetchFruitsTask : AsyncTask<Void, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Void?): List<Fruit> {
            val fruits = mutableListOf<Fruit>()
            try {
                val url = URL("https://www.fruityvice.com/api/fruit/all")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)

                        val id = jsonObject.getInt("id")
                        val name = jsonObject.getString("name")
                        val family = jsonObject.getString("family")
                        val order = jsonObject.getString("order")
                        val genus = jsonObject.getString("genus")

                        val nutritionObj = jsonObject.getJSONObject("nutritions")
                        val nutrition = Nutrition(
                            carbohydrates = nutritionObj.getDouble("carbohydrates"),
                            protein = nutritionObj.getDouble("protein"),
                            fat = nutritionObj.getDouble("fat"),
                            calories = nutritionObj.getInt("calories"),
                            sugar = nutritionObj.getDouble("sugar")
                        )

                        val fruit = Fruit(
                            id = id,
                            name = name,
                            family = family,
                            order = order,
                            genus = genus,
                            apiIndex = i,
                            nutritions = nutrition
                        )
                        fruits.add(fruit)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return fruits
        }

        override fun onPostExecute(result: List<Fruit>) {
            InsertFruitsTask().execute(result)
        }
    }

    /**
     * Inserta la lista de frutas en Room y luego obtiene todos los registros.
     */
    inner class InsertFruitsTask : AsyncTask<List<Fruit>, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: List<Fruit>): List<Fruit> {
            val fruits = params[0]
            val db = AppDatabase.getDatabase(applicationContext)
            db.fruitDao().insertFruits(fruits)
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            fruitAdapter.updateData(result)
            Toast.makeText(this@MainActivity, "Frutas cargadas correctamente", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // MENÚ CONTEXTUAL
    // ---------------------------------------------------------------------------------------------

    /**
     * Maneja las acciones del menú contextual inflado en el adapter.
     */
    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.option_add -> {
                // Agrega una nueva fruta manualmente
                showAddFruitDialog()
                true
            }
            R.id.option_delete -> {
                // Elimina la fruta seleccionada (guardada en selectedFruit)
                selectedFruit?.let {
                    DeleteFruitTask().execute(it)
                }
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    /**
     * Muestra un diálogo para agregar una fruta personalizada
     * (nombre, calorías, grasas, azúcar y carbohidratos).
     */
    private fun showAddFruitDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Agregar Nueva Fruta")

        val view = layoutInflater.inflate(R.layout.dialog_add_fruit, null)
        builder.setView(view)

        // Referencias a los EditText
        val editTextName = view.findViewById<EditText>(R.id.editTextName)
        val editTextCalories = view.findViewById<EditText>(R.id.editTextCalories)
        val editTextFat = view.findViewById<EditText>(R.id.editTextFat)
        val editTextSugar = view.findViewById<EditText>(R.id.editTextSugar)
        val editTextCarbohydrates = view.findViewById<EditText>(R.id.editTextCarbohydrates)

        builder.setPositiveButton("Agregar") { _, _ ->
            // Convertir los valores de los EditText
            val name = editTextName.text.toString().trim()
            val calories = editTextCalories.text.toString().toIntOrNull() ?: 0
            val fat = editTextFat.text.toString().toDoubleOrNull() ?: 0.0
            val sugar = editTextSugar.text.toString().toDoubleOrNull() ?: 0.0
            val carbs = editTextCarbohydrates.text.toString().toDoubleOrNull() ?: 0.0

            if (name.isNotEmpty()) {
                // Creamos la nueva fruta con apiIndex = -1
                val newFruit = Fruit(
                    id = 0, // Un ID ficticio
                    name = name,
                    family = "Desconocida",
                    order = "Desconocida",
                    genus = "Desconocido",
                    apiIndex = -1, // <--- Menor que 0 para que salga arriba
                    nutritions = Nutrition(
                        carbohydrates = carbs,
                        protein = 0.0, // Podrías usar otro EditText si quieres
                        fat = fat,
                        calories = calories,
                        sugar = sugar
                    )
                )
                InsertSingleFruitTask().execute(newFruit)
            } else {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    /**
     * AsyncTask para insertar UNA fruta (la que el usuario agrega manualmente).
     */
    inner class InsertSingleFruitTask : AsyncTask<Fruit, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Fruit): List<Fruit> {
            val db = AppDatabase.getDatabase(applicationContext)
            db.fruitDao().insertFruits(listOf(params[0]))
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            fruitAdapter.updateData(result)
            Toast.makeText(this@MainActivity, "Fruta agregada", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * AsyncTask para eliminar la fruta seleccionada.
     */
    inner class DeleteFruitTask : AsyncTask<Fruit, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Fruit): List<Fruit> {
            val db = AppDatabase.getDatabase(applicationContext)
            db.fruitDao().deleteFruit(params[0])
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            fruitAdapter.updateData(result)
            Toast.makeText(this@MainActivity, "Fruta eliminada", Toast.LENGTH_SHORT).show()
        }
    }
}
