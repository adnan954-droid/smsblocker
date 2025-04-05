package com.example.smsblocker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
public class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.InboxViewHolder> {

    private List<SmsMessage> smsMessages;
    private OnItemClickListener listener;
    private Context context;

    public interface OnItemClickListener {
        void onItemClick(SmsMessage smsMessage);
    }

    public InboxAdapter(Context context, List<SmsMessage> smsMessages, OnItemClickListener listener) {
        this.context = context;
        this.smsMessages = smsMessages;
        this.listener = listener;
    }

    @Override
    public InboxViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sms, parent, false);
        return new InboxViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(InboxViewHolder holder, int position) {
        SmsMessage smsMessage = smsMessages.get(position);

        if (smsMessage.isBlocked()) {
            return; // Skip binding if the message is blocked
        }

        holder.senderName.setText(smsMessage.getSenderName());
        holder.lastMessage.setText(smsMessage.getBody());

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
        holder.timestamp.setText(sdf.format(smsMessage.getTimestamp()));

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChatActivity.class);
            intent.putExtra("senderName", smsMessage.getSenderName());
            intent.putExtra("senderPhoneNumber", smsMessage.getSenderPhoneNumber());
            context.startActivity(intent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            showContextMenu(smsMessage, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        int nonBlockedCount = 0;
        for (SmsMessage smsMessage : smsMessages) {
            if (!smsMessage.isBlocked()) {
                nonBlockedCount++;
            }
        }
        return nonBlockedCount;
    }

    private void showContextMenu(SmsMessage smsMessage, View view) {
        PopupMenu popupMenu = new PopupMenu(context, view);
        popupMenu.getMenuInflater().inflate(R.menu.sms_context_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.block_sms) {
                blockSms(smsMessage);
                return true;
            } else if (item.getItemId() == R.id.delete_sms) {
                deleteSms(smsMessage);
                return true;
            } else if (item.getItemId() == R.id.block_all) {
                blockAll();
                return true;
            } else if (item.getItemId() == R.id.delete_all) {
                deleteAll();
                return true;
            } else {
                return false;
            }
        });

        popupMenu.show();
    }

    private void blockSms(SmsMessage smsMessage) {
        smsMessage.setBlocked(true);

        BlockedNumbersDatabaseHelper dbHelper = new BlockedNumbersDatabaseHelper(context);
        dbHelper.blockNumber(smsMessage.getSenderPhoneNumber());

        SharedPreferences prefs = context.getSharedPreferences("BlockedSMS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(smsMessage.getSenderPhoneNumber(), true);
        editor.apply();

        smsMessages.remove(smsMessage);
        notifyDataSetChanged();

        Toast.makeText(context, "Blocked SMS from " + smsMessage.getSenderName(), Toast.LENGTH_SHORT).show();
    }

    private void deleteSms(SmsMessage smsMessage) {
        smsMessages.remove(smsMessage);
        notifyDataSetChanged();

        Toast.makeText(context, "Deleted SMS from " + smsMessage.getSenderName(), Toast.LENGTH_SHORT).show();
    }

    private void blockAll() {
        // Get all SMS messages that are not yet blocked
        List<SmsMessage> allSmsMessages = getAllSmsMessages();  // You can implement this method to fetch all SMS messages

        BlockedNumbersDatabaseHelper dbHelper = new BlockedNumbersDatabaseHelper(context);
        SharedPreferences prefs = context.getSharedPreferences("BlockedSMS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        for (SmsMessage smsMessage : allSmsMessages) {
            // Block each SMS message
            smsMessage.setBlocked(true);  // Mark SMS as blocked

            // Block the sender in the database
            dbHelper.blockNumber(smsMessage.getSenderPhoneNumber());

            // Save the blocked state in SharedPreferences
            editor.putBoolean(smsMessage.getSenderPhoneNumber(), true);

            // Remove the blocked SMS from the list
            smsMessages.remove(smsMessage);  // Remove the blocked SMS from the list
        }

        // Commit the changes to SharedPreferences
        editor.apply();

        // Notify the adapter to update the list view
        notifyDataSetChanged();

        // Show a toast indicating that all SMS messages are blocked
        Toast.makeText(context, "Blocked all SMS messages", Toast.LENGTH_SHORT).show();
    }

    private List<SmsMessage> getAllSmsMessages() {
        List<SmsMessage> smsMessages = new ArrayList<>();

        // Ensure permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return smsMessages;
        }

        String[] projection = {
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
                Telephony.Sms._ID
        };

        // Fetch from multiple sources
        Uri[] uris = {
                Telephony.Sms.CONTENT_URI,   // All messages
                Telephony.Sms.Inbox.CONTENT_URI,  // Inbox
                Telephony.Sms.Sent.CONTENT_URI,   // Sent
                Telephony.MmsSms.CONTENT_URI      // MMS/RCS
        };

        for (Uri uri : uris) {
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, Telephony.Sms.DATE + " DESC");

            try {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        @SuppressLint("Range") String senderPhoneNumber = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS));
                        @SuppressLint("Range") String body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
                        @SuppressLint("Range") long timestamp = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));
                        @SuppressLint("Range") int readState = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.READ));
                        @SuppressLint("Range") long messageId = cursor.getLong(cursor.getColumnIndex(Telephony.Sms._ID));

                        if (senderPhoneNumber == null) senderPhoneNumber = "Unknown";
                        if (body == null) body = "No message content";

                        smsMessages.add(new SmsMessage(
                                null, senderPhoneNumber, body, timestamp, readState, messageId, false
                        ));
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        return smsMessages;
    }



    private void deleteAll() {
        smsMessages.clear();
        notifyDataSetChanged();

        Toast.makeText(context, "All SMS deleted", Toast.LENGTH_SHORT).show();
    }

    public class InboxViewHolder extends RecyclerView.ViewHolder {

        TextView senderName;
        TextView lastMessage;
        TextView timestamp;

        public InboxViewHolder(View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.sender_name);
            lastMessage = itemView.findViewById(R.id.last_message);
            timestamp = itemView.findViewById(R.id.timestamp);
        }
    }
}
