package com.example.mental_ai

import org.json.JSONArray
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ChatbotActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private val client = OkHttpClient()

    // API configuration
    private val API_URL = "https://api-inference.huggingface.co/models/facebook/blenderbot-400M-distill" // Example model
    private val API_KEY = "" // Replace with your API key
    private val TAG = "ChatbotActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        // Set up toolbar with back button
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Mental Health Assistant"

        // Initialize views
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        // Set up RecyclerView
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(chatMessages)
        chatRecyclerView.adapter = chatAdapter

        // Add welcome message
        addBotMessage("Hello! I'm your mental wellness assistant. How are you feeling today?")

        // Set up send button
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                addUserMessage(message)
                messageInput.text.clear()

                // Show typing indicator
                addBotTypingIndicator()

                // Get AI response
                sendMessageToAPI(message)
            }
        }
    }

    private fun addUserMessage(message: String) {
        chatMessages.add(ChatMessage(message, true))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun addBotMessage(message: String) {
        chatMessages.add(ChatMessage(message, false))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun addBotTypingIndicator() {
        chatMessages.add(ChatMessage("Typing...", false, true))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun removeBotTypingIndicator() {
        val position = chatMessages.indexOfLast { it.isTypingIndicator }
        if (position != -1) {
            chatMessages.removeAt(position)
            chatAdapter.notifyItemRemoved(position)
        }
    }

    private fun sendMessageToAPI(message: String) {
        // Using Hugging Face API as an example
        // For a real mental health assistant, you might want to use a more specialized model

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create request JSON
                val jsonBody = JSONObject()

                // Ensure that `text` is a string, not a nested object
                jsonBody.put("inputs", message)  // Directly send the message as a string

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(API_URL)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("ChatbotActivity", "API call failed: ${e.localizedMessage}", e)
                        runOnUiThread {
                            removeBotTypingIndicator()
                            addBotMessage("Sorry, I'm having trouble connecting to my brain right now. Please try again later.")
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        Log.d(TAG, "API response: $responseBody")

                        runOnUiThread {
                            removeBotTypingIndicator()

                            if (response.isSuccessful && responseBody != null) {
                                try {
                                    // Assuming the response is now a JSON array as before
                                    val jsonResponse = JSONArray(responseBody)
                                    val botReply = jsonResponse.getJSONObject(0).optString("generated_text",
                                        "I'm here to help with your mental health concerns. Could you tell me more?")

                                    // Add mental health context to generic responses
                                    val enhancedReply = enhanceWithMentalHealthContext(botReply)
                                    addBotMessage(enhancedReply)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing API response", e)
                                    addBotMessage("I understand you're reaching out. As your mental health assistant, I'm here to listen. What's on your mind today?")
                                }
                            } else {
                                addBotMessage("I'm here to support your mental wellbeing. How can I help you today?")
                            }
                        }
                    }
                })

            } catch (e: Exception) {
                Log.e(TAG, "Error sending message to API", e)
                withContext(Dispatchers.Main) {
                    removeBotTypingIndicator()
                    addBotMessage("I'm having trouble processing that right now. How are you feeling today?")
                }
            }
        }
    }


    // Function to enhance generic responses with mental health context
    private fun enhanceWithMentalHealthContext(response: String): String {
        // If the response is very generic, add mental health context
        val genericResponses = listOf(
            "I don't know", "I'm not sure", "Can you tell me more",
            "That's interesting", "I see", "Tell me more"
        )

        for (generic in genericResponses) {
            if (response.contains(generic, ignoreCase = true)) {
                return "As your mental health assistant, I'm here to support you. $response How are your emotions right now?"
            }
        }

        return response
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Data class for chat messages
    data class ChatMessage(
        val content: String,
        val isUser: Boolean,
        val isTypingIndicator: Boolean = false
    )

    // Adapter for the chat RecyclerView
    inner class ChatAdapter(private val messages: List<ChatMessage>) :
        RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            val message = messages[position]
            return when {
                message.isTypingIndicator -> 2
                message.isUser -> 1
                else -> 0
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val layout = when (viewType) {
                1 -> R.layout.item_user_message
                2 -> R.layout.item_typing_indicator
                else -> R.layout.item_bot_message
            }

            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = messages[position]
            if (!message.isTypingIndicator) {
                holder.messageText?.text = message.content
            }
        }

        override fun getItemCount() = messages.size

        inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val messageText: TextView? = itemView.findViewById(R.id.messageText)
        }
    }
}