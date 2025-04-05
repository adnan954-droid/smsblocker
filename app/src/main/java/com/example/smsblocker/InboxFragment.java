package com.example.smsblocker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InboxFragment extends Fragment {

    private RecyclerView recyclerViewInbox;
    private InboxAdapter inboxAdapter;
    private List<SmsMessage> smsMessages;
    private SmsReceiver smsReceiver;
    private Set<String> blockedNumbersSet;
    private boolean isSmsFetched = false;

    private static final int PERMISSION_REQUEST_CODE = 1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inbox, container, false);

        recyclerViewInbox = view.findViewById(R.id.recyclerViewInbox);
        recyclerViewInbox.setLayoutManager(new LinearLayoutManager(getContext()));

        smsMessages = new ArrayList<>();
        inboxAdapter = new InboxAdapter(getContext(), smsMessages, smsMessage -> {
            Intent intent = new Intent(getContext(), ChatActivity.class);
            intent.putExtra("sender_name", smsMessage.getSenderName());
            intent.putExtra("sender_number", smsMessage.getSenderPhoneNumber());
            startActivity(intent);
        });
        recyclerViewInbox.setAdapter(inboxAdapter);


        loadBlockedNumbers();


        requestPermissionsIfNeeded();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isSmsFetched && hasAllPermissions()) {
            fetchSMSMessages();
            isSmsFetched = true;
        }
    }

    private void loadBlockedNumbers() {
        blockedNumbersSet = new HashSet<>();
        SharedPreferences prefs = getContext().getSharedPreferences("BlockedSMS", Context.MODE_PRIVATE);
        blockedNumbersSet.addAll(prefs.getStringSet("blocked_numbers", new HashSet<>()));

        BlockedNumbersDatabaseHelper dbHelper = new BlockedNumbersDatabaseHelper(getContext());
        Cursor cursor = dbHelper.getBlockedNumbers();

        if (cursor != null) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range")
                String blockedNumber = cursor.getString(cursor.getColumnIndex("phone_number"));
                blockedNumbersSet.add(blockedNumber);
            }
            cursor.close();
        }
        Log.d("SMSBlocker", "Blocked Numbers Loaded: " + blockedNumbersSet);
    }

    private void requestPermissionsIfNeeded() {
        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS},
                    PERMISSION_REQUEST_CODE);
        } else {
            setupSmsReceiver();
        }
    }

    private boolean hasAllPermissions() {
        Context context = getContext();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                setupSmsReceiver();
                fetchSMSMessages();
            } else {
                Toast.makeText(getContext(), "Permissions required for SMS and Contacts", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupSmsReceiver() {
        if (smsReceiver == null) {
            smsReceiver = new SmsReceiver();
            requireContext().registerReceiver(smsReceiver, new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (smsReceiver != null) {
            requireContext().unregisterReceiver(smsReceiver);
            smsReceiver = null;
        }
    }

    private class FetchSMSMessagesTask extends AsyncTask<Void, Void, List<SmsMessage>> {
        @Override
        protected List<SmsMessage> doInBackground(Void... voids) {
            if (getContext() == null) return null;

            ContentResolver contentResolver = requireContext().getContentResolver();
            Cursor cursor = contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    null,
                    null,
                    null,
                    Telephony.Sms.DEFAULT_SORT_ORDER
            );

            if (cursor != null) {
                Set<String> uniqueNumbers = new HashSet<>();
                List<SmsMessage> messagesList = new ArrayList<>();

                while (cursor.moveToNext()) {
                    String senderPhoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    if (uniqueNumbers.contains(senderPhoneNumber)) continue;
                    uniqueNumbers.add(senderPhoneNumber);

                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    int readState = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ));
                    long messageId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID));

                    String senderName = getContactName(getContext(), senderPhoneNumber);
                    boolean isBlocked = blockedNumbersSet.contains(senderPhoneNumber);

                    if (!isBlocked) {
                        messagesList.add(new SmsMessage(
                                senderName != null ? senderName : senderPhoneNumber,
                                senderPhoneNumber,
                                body,
                                timestamp,
                                readState,
                                messageId,
                                isBlocked
                        ));
                    }
                }
                cursor.close();
                return messagesList;
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<SmsMessage> result) {
            if (isAdded() && getContext() != null) {
                if (result != null) {
                    smsMessages.clear();
                    smsMessages.addAll(result);
                    Collections.sort(smsMessages, (sms1, sms2) -> Long.compare(sms2.getTimestamp(), sms1.getTimestamp()));
                    inboxAdapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(getContext(), "Failed to fetch SMS messages", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void fetchSMSMessages() {
        new FetchSMSMessagesTask().execute();
    }

    @SuppressLint("Range")
    public String getContactName(Context context, String phoneNumber) {
        if (context == null) return phoneNumber; // Return original number if context is null

        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME));
            }
        }

        return phoneNumber;
    }
}