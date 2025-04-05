package com.example.smsblocker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class BlockedAdapter extends RecyclerView.Adapter<BlockedAdapter.ViewHolder> {
    private final Context context;
    private List<String> blockedNumbers;
    private final OnBlockedListener onBlockedListener;


    public void updateBlockedNumbers(List<String> newBlockedNumbers) {
        this.blockedNumbers.clear();
        this.blockedNumbers.addAll(newBlockedNumbers);
        notifyDataSetChanged();
    }

    public interface OnBlockedListener {
        void onUnblock(String phoneNumber);
        void onSetRetentionPeriod(String phoneNumber, long retentionMillis);
        void onChat(String phoneNumber);
    }

    public BlockedAdapter(Context context, List<String> blockedNumbers, OnBlockedListener onBlockedListener) {
        this.context = context;
        this.blockedNumbers = blockedNumbers;
        this.onBlockedListener = onBlockedListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_blocked_sms, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String phoneNumber = blockedNumbers.get(position);

        // ✅ Fetch contact name if available
        String contactName = getContactName(context, phoneNumber);

        // ✅ If a contact name is found, display it. Otherwise, show the number.
        if (contactName != null) {
            holder.senderName.setText(contactName);
        } else {
            holder.senderName.setText(phoneNumber);
        }

        // ✅ Fetch last message for the blocked number
        String[] projection = {Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE};
        String selection = Telephony.Sms.ADDRESS + " = ?";
        String[] selectionArgs = {phoneNumber};
        Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI, projection, selection, selectionArgs, Telephony.Sms.DATE + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") String lastMessageText = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
            @SuppressLint("Range") long messageDate = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));


            String formattedDate = DateFormat.format("dd-MM-yyyy hh:mm a", messageDate).toString();


            holder.lastMessage.setText(lastMessageText);
            holder.messageTime.setText(formattedDate);
            cursor.close(); // Close cursor to prevent memory leaks
        } else {
            // If no message found, display "No messages"
            holder.lastMessage.setText("No messages");
            holder.messageTime.setText("N/A");
        }


        holder.itemView.setOnClickListener(v -> onBlockedListener.onChat(phoneNumber));
        holder.itemView.setOnLongClickListener(v -> {
            showUnblockOptionsDialog(phoneNumber);
            return true;
        });
    }

    private String getContactName(Context context, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME));
            }
        }
        return null; // Return null if no contact found
    }


    @Override
    public int getItemCount() {
        return blockedNumbers.size();
    }

    private void showUnblockOptionsDialog(String phoneNumber) {
        // Create an EditText view for user input
        final EditText retentionInput = new EditText(context);
        retentionInput.setHint("Enter deletion period in days");
        retentionInput.setInputType(InputType.TYPE_CLASS_NUMBER); // Restrict to numbers only

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(retentionInput);

        new AlertDialog.Builder(context)
                .setTitle("Manage Blocked Number")
                .setMessage("Do you want to unblock or set a retention period for this number?")
                .setPositiveButton("Unblock", (dialog, which) -> {
                    onBlockedListener.onUnblock(phoneNumber);
                    removeBlockedNumber(phoneNumber);  // Remove the unblocked number from the list
                })
                .setNeutralButton("Set Deletion", (dialog, which) -> {
                    String input = retentionInput.getText().toString().trim();

                    // Validate user input
                    if (input.isEmpty()) {
                        Toast.makeText(context, "Please enter a deletion period.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        int retentionDays = Integer.parseInt(input);
                        if (retentionDays <= 0) {
                            Toast.makeText(context, "Enter a valid positive number.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Convert days to milliseconds correctly
                        long retentionMillis = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(retentionDays);

                        BlockedNumbersDatabaseHelper dbHelper = new BlockedNumbersDatabaseHelper(context);
                        boolean success = dbHelper.unblockNumber(phoneNumber, retentionMillis);

                        if (success) {
                            // ✅ FIX: Calculate remaining days correctly and round up
                            long daysRemaining = TimeUnit.MILLISECONDS.toDays(retentionMillis - System.currentTimeMillis());

                            // If there's any remaining hours/minutes, round up by adding 1 day
                            if ((retentionMillis - System.currentTimeMillis()) % TimeUnit.DAYS.toMillis(1) != 0) {
                                daysRemaining++;
                            }

                            Toast.makeText(context, "Number will be unblocked in " + daysRemaining + " days", Toast.LENGTH_SHORT).show();
                            onBlockedListener.onSetRetentionPeriod(phoneNumber, retentionMillis);
                        } else {
                            Toast.makeText(context, "Deletion period is already set.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Please enter a valid number of days.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .setView(layout)
                .show();
    }

    public void removeBlockedNumber(String phoneNumber) {
        Iterator<String> iterator = blockedNumbers.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().equals(phoneNumber)) {
                iterator.remove();
                notifyDataSetChanged(); // Refresh the UI
                break;
            }
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView senderName, lastMessage, messageTime;

        public ViewHolder(View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.sender_name);
            lastMessage = itemView.findViewById(R.id.last_message);
            messageTime = itemView.findViewById(R.id.timestamp);
        }
    }
}
