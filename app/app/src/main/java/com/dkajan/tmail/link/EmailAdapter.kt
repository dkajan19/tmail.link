package com.dkajan.tmail.link

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmailAdapter(
    private val emails: List<Email>,
    private var userEmail: String,
    private val onEmailClick: (String) -> Unit
) : RecyclerView.Adapter<EmailAdapter.EmailViewHolder>() {

    inner class EmailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.emailTitle)
        val content: TextView = itemView.findViewById(R.id.emailContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmailViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.email_item, parent, false)
        return EmailViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmailViewHolder, position: Int) {
        val email = emails[position]
        holder.title.text = email.subject
        holder.content.text = email.sender

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, EmailDetailActivity::class.java)
            intent.putExtra(EmailDetailActivity.EXTRA_EMAIL_URL, email.href)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = emails.size
}
