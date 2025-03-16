package com.example.appfinal2

import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FruitAdapter(
    private var fruits: List<Fruit>,
    private val activity: MainActivity
) : RecyclerView.Adapter<FruitAdapter.FruitViewHolder>() {

    inner class FruitViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnCreateContextMenuListener {

        val textViewName: TextView = itemView.findViewById(R.id.textViewName)
        val textViewCalories: TextView = itemView.findViewById(R.id.textViewCalories)
        val textViewFat: TextView = itemView.findViewById(R.id.textViewFat)
        val textViewSugar: TextView = itemView.findViewById(R.id.textViewSugar)
        val textViewCarbohydrates: TextView = itemView.findViewById(R.id.textViewCarbohydrates)

        init {
            itemView.setOnCreateContextMenuListener(this)
        }

        override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
            activity.menuInflater.inflate(R.menu.context_menu, menu)
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                activity.selectedFruit = fruits[position]
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FruitViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fruit, parent, false)
        return FruitViewHolder(view)
    }

    override fun onBindViewHolder(holder: FruitViewHolder, position: Int) {
        val fruit = fruits[position]
        holder.textViewName.text = fruit.name
        holder.textViewCalories.text = "Calorías: ${fruit.nutritions.calories}"
        holder.textViewFat.text = "Grasas: ${fruit.nutritions.fat}"
        holder.textViewSugar.text = "Azúcar: ${fruit.nutritions.sugar}"
        holder.textViewCarbohydrates.text = "Carbohidratos: ${fruit.nutritions.carbohydrates}"

        // Animación de desvanecimiento
        setFadeAnimation(holder.itemView)
    }

    override fun getItemCount(): Int = fruits.size

    fun updateData(newFruits: List<Fruit>) {
        fruits = newFruits
        notifyDataSetChanged()
    }
    fun getPositionOfFruit(fruit: Fruit): Int {
        return fruits.indexOfFirst { it.id == fruit.id }
    }

    // Efecto fade in para cada ítem
    private fun setFadeAnimation(view: View) {
        val anim = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 700 // Duración en ms
        view.startAnimation(anim)
    }
}
