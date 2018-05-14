package com.lego.mydrawerapplication

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.recycler_grid_card.view.*

class RvAdapter(private val context: Context, var data: List<OloloModel>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return DataViewHolder(layoutInflater.inflate(R.layout.recycler_grid_card, parent, false))
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is DataViewHolder) {
            val item = data[position]
            item.let {
                holder.bindData(context, it)
            }
        }
    }

    class DataViewHolder(containerView: View) : RecyclerView.ViewHolder(containerView) {
        private var picture = containerView.gridProfileImage
        private var profileName = containerView.gridProfileName

        internal fun bindData(context: Context, data: OloloModel) {
            profileName.text = data.name
            picture.setImageResource(data.photo)
        }
    }
}