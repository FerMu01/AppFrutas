package com.example.appfinal2

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fruitAdapter: FruitAdapter
    // Variable para el menú contextual (opcional)
    var selectedFruit: Fruit? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Iniciando la aplicación")
        setContentView(R.layout.activity_main)
        window.statusBarColor = Color.TRANSPARENT

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configuración del RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        fruitAdapter = FruitAdapter(emptyList(), this)
        recyclerView.adapter = fruitAdapter

        // Carga inicial de frutas desde la BD
        Log.d(TAG, "onCreate: Iniciando carga de frutas desde la base de datos")
        LoadFruitsFromDatabaseTask().execute()

        // Botón para ver el gráfico
        val btnShowChart = findViewById<Button>(R.id.btnShowChart)
        btnShowChart.setOnClickListener {
            Log.d(TAG, "onCreate: Botón de gráfico presionado")
            GetTop3FruitsTask().execute()
        }
    }

    // -------------------------------------------------------------------------
    // 1) Carga de datos (AsyncTasks)
    // -------------------------------------------------------------------------

    inner class LoadFruitsFromDatabaseTask : AsyncTask<Void, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Void?): List<Fruit> {
            Log.d(TAG, "LoadFruitsFromDatabaseTask: Obteniendo frutas de la BD")
            val db = AppDatabase.getDatabase(applicationContext)
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            Log.d(TAG, "LoadFruitsFromDatabaseTask: Frutas obtenidas: ${result.size}")
            if (result.isEmpty()) {
                Log.d(TAG, "LoadFruitsFromDatabaseTask: Base de datos vacía, se llama a FetchFruitsTask")
                FetchFruitsTask().execute()
            } else {
                fruitAdapter.updateData(result)
            }
        }
    }

    inner class FetchFruitsTask : AsyncTask<Void, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Void?): List<Fruit> {
            Log.d(TAG, "FetchFruitsTask: Obteniendo frutas desde la API")
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
                    Log.d(TAG, "FetchFruitsTask: Se obtuvieron ${fruits.size} frutas de la API")
                } else {
                    Log.e(TAG, "FetchFruitsTask: Error en la conexión, código ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "FetchFruitsTask: Excepción al obtener frutas", e)
                e.printStackTrace()
            }
            return fruits
        }

        override fun onPostExecute(result: List<Fruit>) {
            Log.d(TAG, "FetchFruitsTask: Iniciando inserción de frutas en la BD")
            InsertFruitsTask().execute(result)
        }
    }

    inner class InsertFruitsTask : AsyncTask<List<Fruit>, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: List<Fruit>): List<Fruit> {
            val db = AppDatabase.getDatabase(applicationContext)
            Log.d(TAG, "InsertFruitsTask: Insertando ${params[0].size} frutas en la BD")
            db.fruitDao().insertFruits(params[0])
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            fruitAdapter.updateData(result)
            Toast.makeText(this@MainActivity, "Frutas cargadas correctamente", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "InsertFruitsTask: Frutas cargadas correctamente, total: ${result.size}")
        }
    }

    // -------------------------------------------------------------------------
    // 2) Top 3 frutas con más calorías (AsyncTask + BottomSheet)
    // -------------------------------------------------------------------------

    inner class GetTop3FruitsTask : AsyncTask<Void, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Void?): List<Fruit> {
            Log.d(TAG, "GetTop3FruitsTask: Obteniendo top 3 frutas por calorías")
            val db = AppDatabase.getDatabase(applicationContext)
            return db.fruitDao().getTop3ByCalories() // ORDER BY calories DESC LIMIT 3
        }

        override fun onPostExecute(result: List<Fruit>) {
            if (result.isEmpty()) {
                Toast.makeText(this@MainActivity, "No hay frutas en la base de datos", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "GetTop3FruitsTask: No se encontraron frutas para mostrar en el gráfico")
            } else {
                Log.d(TAG, "GetTop3FruitsTask: Top 3 frutas obtenidas, mostrando gráfico")
                showTop3Chart(result)
            }
        }
    }

    /**
     * Muestra un BottomSheetDialog con un BarChart de las 3 frutas con más calorías.
     * Incluye animación en el chart + animación de aparición (slide_up).
     */
    private fun showTop3Chart(fruits: List<Fruit>) {
        Log.d(TAG, "showTop3Chart: Preparando gráfico con ${fruits.size} frutas")
        // Creamos el BottomSheet con su layout
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_chart, null)
        bottomSheetDialog.setContentView(view)

        // Animación de aparición
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        view.startAnimation(slideUp)

        val barChart = view.findViewById<BarChart>(R.id.barChart)

        // Construimos la lista de BarEntry y los labels
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        for ((index, fruit) in fruits.withIndex()) {
            entries.add(BarEntry(index.toFloat(), fruit.nutritions.calories.toFloat()))
            labels.add(fruit.name)
        }

        val dataSet = BarDataSet(entries, "Calorías")
        dataSet.setColors(
            resources.getColor(R.color.purple_200),
            resources.getColor(R.color.purple_500),
            resources.getColor(R.color.purple_700)
        )
        dataSet.valueTextColor = Color.WHITE // Números encima de cada barra en blanco
        dataSet.valueTextSize = 13f
        val barData = BarData(dataSet)
        barChart.data = barData
        barChart.description.isEnabled = false
        barChart.setFitBars(true)

        // Eje X con nombres
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.labelCount = labels.size
        xAxis.isGranularityEnabled = true
        xAxis.textColor = Color.WHITE // Textos en blanco en el eje X
        xAxis.textSize = 12f

        // Configuración de los ejes Y
        barChart.axisLeft.textColor = Color.WHITE
        barChart.axisRight.textColor = Color.WHITE

        // Configuración de la leyenda
        barChart.legend.textColor = Color.WHITE

        // Animación vertical de las barras
        barChart.animateY(1500, Easing.EaseInOutQuad)
        barChart.invalidate()

        // Mostrar el BottomSheet
        bottomSheetDialog.show()
        Log.d(TAG, "showTop3Chart: Gráfico mostrado")
    }

    // -------------------------------------------------------------------------
    // 3) Eliminar fruta con animación (fade out)
    // -------------------------------------------------------------------------

    /**
     * Llama a esta función para eliminar una fruta con animación de desvanecimiento.
     * @param fruit La fruta a eliminar.
     * @param position Índice en el adapter.
     */
    fun deleteFruitWithAnimation(fruit: Fruit, position: Int) {
        Log.d(TAG, "deleteFruitWithAnimation: Eliminando fruta ${fruit.name} en posición $position")
        // Obtenemos el ViewHolder para ese position
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
        val itemView = viewHolder?.itemView ?: return

        // Cargamos la animación fade_out
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                Log.d(TAG, "deleteFruitWithAnimation: Inicia animación fade out")
            }
            override fun onAnimationRepeat(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                Log.d(TAG, "deleteFruitWithAnimation: Finaliza animación fade out, procediendo a eliminar fruta")
                // Cuando termina la animación, eliminamos la fruta de la BD y refrescamos la lista
                RemoveFruitTask().execute(fruit)
            }
        })
        // Iniciamos la animación
        itemView.startAnimation(fadeOut)
    }

    /**
     * AsyncTask para eliminar la fruta de la base de datos y actualizar el adapter.
     */
    inner class RemoveFruitTask : AsyncTask<Fruit, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Fruit): List<Fruit> {
            Log.d(TAG, "RemoveFruitTask: Eliminando fruta ${params[0].name} de la BD")
            val db = AppDatabase.getDatabase(applicationContext)
            db.fruitDao().deleteFruit(params[0])
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            fruitAdapter.updateData(result)
            Toast.makeText(this@MainActivity, "Fruta eliminada", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "RemoveFruitTask: Fruta eliminada, total frutas restantes: ${result.size}")
        }
    }

    // -------------------------------------------------------------------------
    // MENÚ CONTEXTUAL (opcional)
    // -------------------------------------------------------------------------
    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.option_delete -> {
                Log.d(TAG, "onContextItemSelected: Opción eliminar seleccionada")
                selectedFruit?.let { fruit ->
                    val position = fruitAdapter.getPositionOfFruit(fruit)
                    if (position >= 0) {
                        deleteFruitWithAnimation(fruit, position)
                    }
                }
                true
            }
            R.id.option_add -> {
                Log.d(TAG, "onContextItemSelected: Opción agregar seleccionada")
                showAddFruitDialog()
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    /**
     * Muestra un cuadro de diálogo para agregar una nueva fruta.
     */
    private fun showAddFruitDialog() {
        Log.d(TAG, "showAddFruitDialog: Mostrando diálogo para agregar fruta")
        // Infla el layout del diálogo (asegúrate de que el archivo se llame dialog_add_fruit.xml)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_fruit, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
            .setTitle("Agregar Fruta")
            .setPositiveButton("Agregar") { dialog, which ->
                val editTextName = dialogView.findViewById<EditText>(R.id.editTextName)
                val editTextCalories = dialogView.findViewById<EditText>(R.id.editTextCalories)
                val editTextFat = dialogView.findViewById<EditText>(R.id.editTextFat)
                val editTextSugar = dialogView.findViewById<EditText>(R.id.editTextSugar)
                val editTextCarbohydrates = dialogView.findViewById<EditText>(R.id.editTextCarbohydrates)

                val name = editTextName.text.toString()
                val calories = editTextCalories.text.toString().toIntOrNull() ?: 0
                val fat = editTextFat.text.toString().toDoubleOrNull() ?: 0.0
                val sugar = editTextSugar.text.toString().toDoubleOrNull() ?: 0.0
                val carbohydrates = editTextCarbohydrates.text.toString().toDoubleOrNull() ?: 0.0

                Log.d(TAG, "showAddFruitDialog: Datos ingresados - Nombre: $name, Calorías: $calories, Grasas: $fat, Azúcar: $sugar, Carbohidratos: $carbohydrates")

                // Se crea la fruta; se usan valores por defecto para campos no ingresados
                val newFruit = Fruit(
                    id = 0, // Se asume que la BD asigna el ID automáticamente
                    name = name,
                    family = "",
                    order = "",
                    genus = "",
                    apiIndex = 0,
                    nutritions = Nutrition(
                        carbohydrates = carbohydrates,
                        protein = 0.0,
                        fat = fat,
                        calories = calories,
                        sugar = sugar
                    )
                )
                InsertNewFruitTask().execute(newFruit)
            }
            .setNegativeButton("Cancelar") { dialog, which ->
                Log.d(TAG, "showAddFruitDialog: Se canceló la operación de agregar fruta")
            }
        val alertDialog = builder.create()
        alertDialog.show()
        // Establecer el color de fondo del cuadro de diálogo
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#10454F")))
        // Establecer el color de los botones "Agregar" y "Cancelar"
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#BDE038"))
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#BDE038"))
    }

    /**
     * AsyncTask para insertar una nueva fruta en la base de datos y actualizar el adapter.
     */
    inner class InsertNewFruitTask : AsyncTask<Fruit, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Fruit): List<Fruit> {
            Log.d(TAG, "InsertNewFruitTask: Insertando nueva fruta ${params[0].name} en la BD")
            val db = AppDatabase.getDatabase(applicationContext)
            db.fruitDao().insertFruit(params[0])
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            fruitAdapter.updateData(result)
            Toast.makeText(this@MainActivity, "Fruta agregada", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "InsertNewFruitTask: Nueva fruta agregada, total frutas: ${result.size}")
        }
    }
}
