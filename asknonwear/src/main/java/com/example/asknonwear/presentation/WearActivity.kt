package com.example.asknonwear.presentation

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.asknonwear.databinding.ActivityWearBinding
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

class WearActivity : Activity(), MessageClient.OnMessageReceivedListener {

    private lateinit var binding: ActivityWearBinding
    private lateinit var pendingQuestionsTextView: TextView
    private lateinit var approveAllButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWearBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Assuming you have TextView with id 'pendingQuestionsTextView' and Button with id 'approveAllButton' in activity_wear.xml
        pendingQuestionsTextView = binding.pendingQuestionsTextView
        approveAllButton = binding.approveAllButton

        approveAllButton.setOnClickListener {
            sendApproveAllMessageToMobile()
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(messageEvent: com.google.android.gms.wearable.MessageEvent) {
        if (messageEvent.path == "/pending_questions_count") {
            val count = String(messageEvent.data).toIntOrNull()
            count?.let {
                runOnUiThread {
                    pendingQuestionsTextView.text = "Pendientes: $it"
                }
            }
        }
    }

    private fun sendApproveAllMessageToMobile() {
        // TODO: Get the connected mobile node ID and send the message
        val nodeId = "" // Placeholder for mobile node ID
        if (nodeId.isNotEmpty()) {
            Wearable.getMessageClient(this).sendMessage(
                nodeId,
                "/approve_all_questions",
                ByteArray(0)
            )
        }
    }
}