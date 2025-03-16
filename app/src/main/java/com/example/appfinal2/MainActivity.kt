package com.example.appfinal2

import android.os.AsyncTask
import android.os.Bundle
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.animation.Easing
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fruitAdapter: FruitAdapter

    // Para menú contextual
    var selectedFruit: Fruit? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        fruitAdapter = FruitAdapter(emptyList(), this)
        recyclerView.adapter = fruitAdapter

        // Carga inicial
        LoadFruitsFromDatabaseTask().execute()

        // Botón para ver el gráfico
        val btnShowChart = findViewById<Button>(R.id.btnShowChart)
        btnShowChart.setOnClickListener {
            GetTop3FruitsTask().execute()
        }
    }

    // -------------------------------------------------------------------------
    // Carga de datos (AsyncTasks)
    // -------------------------------------------------------------------------

    inner class LoadFruitsFromDatabaseTask : AsyncTask<Void, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Void?): List<Fruit> {
            val db = AppDatabase.getDatabase(applicationContext)
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            if (result.isEmpty()) {
                FetchFruitsTask().execute()
            } else {
                fruitAdapter.updateData(result)
            }
        }
    }

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

    inner class InsertFruitsTask : AsyncTask<List<Fruit>, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: List<Fruit>): List<Fruit> {
            val db = AppDatabase.getDatabase(applicationContext)
            db.fruitDao().insertFruits(params[0])
            return db.fruitDao().getAllFruits()
        }

        override fun onPostExecute(result: List<Fruit>) {
            fruitAdapter.updateData(result)
            Toast.makeText(this@MainActivity, "Frutas cargadas correctamente", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------------------
    // Top 3 frutas con más calorías (AsyncTask + BottomSheet)
    // -------------------------------------------------------------------------

    inner class GetTop3FruitsTask : AsyncTask<Void, Void, List<Fruit>>() {
        override fun doInBackground(vararg params: Void?): List<Fruit> {
            val db = AppDatabase.getDatabase(applicationContext)
            return db.fruitDao().getTop3ByCalories() // ORDER BY calories DESC LIMIT 3
        }

        override fun onPostExecute(result: List<Fruit>) {
            if (result.isEmpty()) {
                Toast.makeText(this@MainActivity, "No hay frutas en la base de datos", Toast.LENGTH_SHORT).show()
            } else {
                showTop3Chart(result)
            }
        }
    }

    /**
     * Muestra un BottomSheetDialog con un BarChart de las 3 frutas con más calorías.
     * Incluye animación en el chart.
     */
    private fun showTop3Chart(fruits: List<Fruit>) {
        // Creamos el BottomSheet
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_chart, null)
        bottomSheetDialog.setContentView(view)

        val barChart = view.findViewById<BarChart>(R.id.barChart)

        // Lista de BarEntry (x, y) y lista de labels
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        for ((index, fruit) in fruits.withIndex()) {
            entries.add(BarEntry(index.toFloat(), fruit.nutritions.calories.toFloat()))
            labels.add(fruit.name)
        }

        val dataSet = BarDataSet(entries, "Calorías")
        // Colores ejemplo (ajusta a tus recursos)
        dataSet.setColors(
            resources.getColor(R.color.purple_200),
            resources.getColor(R.color.purple_500),
            resources.getColor(R.color.purple_700)
        )

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

        // ANIMACIÓN del chart
        barChart.animateY(1500, Easing.EaseInOutQuad)

        // Refrescar
        barChart.invalidate()

        // Mostrar el BottomSheet
        bottomSheetDialog.show()
    }

    // -------------------------------------------------------------------------
    // MENÚ CONTEXTUAL (opcional)
    // -------------------------------------------------------------------------
    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            // Ejemplo
            else -> super.onContextItemSelected(item)
        }
    }
}
