package com.example.smsblocker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private EditText messageInput;
    private ImageButton sendButton;
    private String phoneNumber;
    private String senderName;
    private boolean isBlocked;
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int SEND_SMS_PERMISSION_REQUEST_CODE = 102;
    private TextView senderNameTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatRecyclerView = findViewById(R.id.chat_recycler_view);
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        senderNameTextView = findViewById(R.id.sender_name);
        ImageView backButton = findViewById(R.id.back_button);

        // Get sender details from intent
        phoneNumber = getIntent().getStringExtra("senderPhoneNumber");
        senderName = getIntent().getStringExtra("senderName");
        isBlocked = getIntent().getBooleanExtra("isBlocked", false);

        if (phoneNumber == null) {
            Log.e("ChatActivity", "Error: Received phoneNumber is null!");
            Toast.makeText(this, "Error: No phone number provided!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // **Fetch name from contacts if not provided**
        if (senderName == null || senderName.isEmpty()) {
            senderName = getContactName(phoneNumber);
        }

        // **Set sender name or phone number in UI**
        senderNameTextView.setText(senderName != null ? senderName : phoneNumber);

        // Initialize RecyclerView
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(new ArrayList<>());
        chatRecyclerView.setAdapter(chatAdapter);

        // **Disable sending if the sender is blocked**
        if (isBlocked) {
            sendButton.setEnabled(false);
            messageInput.setEnabled(false);
            Toast.makeText(this, "Messaging is disabled for blocked contacts", Toast.LENGTH_LONG).show();
        }

        // Check SMS permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_SMS}, PERMISSION_REQUEST_CODE);
        } else {
            loadChatMessages();
        }

        // Back button logic
        backButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("message_read", true);
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Send message logic
        sendButton.setOnClickListener(v -> sendMessage());
    }

    // **Fetch the contact name from contacts**
    private String getContactName(String phoneNumber) {
        ContentResolver resolver = getContentResolver();
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};

        Cursor cursor = resolver.query(uri, projection,
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                new String[]{phoneNumber}, null);

        if (cursor != null && cursor.moveToFirst()) {
            String contactName = cursor.getString(0);
            cursor.close();
            return contactName;
        }
        return null; // Return null if no name found
    }

    // Load chat messages from the SMS content provider
    private void loadChatMessages() {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e("ChatActivity", "Phone number is null or empty.");
            return;
        }

        List<Message> messages = new ArrayList<>();
        ContentResolver contentResolver = getContentResolver();

        Cursor cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                Telephony.Sms.ADDRESS + " LIKE ?",
                new String[]{"%" + phoneNumber + "%"},
                Telephony.Sms.DATE + " ASC" // Sort messages from oldest to newest
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                @SuppressLint("Range")
                String body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
                @SuppressLint("Range")
                long date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));
                String timestamp = DateFormat.format("dd-MM-yyyy hh:mm a", date).toString();
                @SuppressLint("Range")
                boolean isReceived = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE)) == Telephony.Sms.MESSAGE_TYPE_INBOX;

                messages.add(new Message(body, timestamp, isReceived));
            } while (cursor.moveToNext());

            cursor.close();
        }

        // Update adapter with new messages
        chatAdapter.updateMessages(messages);

        //  Scroll to the last message after updating the adapter
        chatRecyclerView.post(() -> chatRecyclerView.scrollToPosition(messages.size() - 1));
    }

    // Send message logic
    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();

        if (messageText.isEmpty()) {
            Toast.makeText(this, "Cannot send an empty message", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SEND_SMS_PERMISSION_REQUEST_CODE);
        } else {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, messageText, null, null);

            String timestamp = DateFormat.format("dd-MM-yyyy hh:mm a", System.currentTimeMillis()).toString();
            Message sentMessage = new Message(messageText, timestamp, false);

            chatAdapter.addMessage(sentMessage);
            chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
            messageInput.setText("");

            Toast.makeText(this, "Message sent!", Toast.LENGTH_LONG).show();
        }
    }

    // Handle permission request results
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadChatMessages();
            } else {
                Toast.makeText(this, "Permission denied to read SMS", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == SEND_SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendMessage();
            } else {
                Toast.makeText(this, "Permission denied to send SMS", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
