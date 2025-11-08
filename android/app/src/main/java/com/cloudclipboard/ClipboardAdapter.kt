package com.cloudclipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class ClipboardAdapter(
    private val context: Context,
    private var items: List<ClipboardItem>,
    private val onDelete: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.clipboardContent)
        val timestamp: TextView = view.findViewById(R.id.clipboardTimestamp)
        val copyButton: MaterialButton = view.findViewById(R.id.copyButton)
        val deleteButton: MaterialButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clipboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.content.text = item.content
        holder.timestamp.text = item.getTimeAgo()

        holder.copyButton.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("clipboard", item.content)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        holder.deleteButton.setOnClickListener {
            onDelete(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ClipboardItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
