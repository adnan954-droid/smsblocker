package com.example.smsblocker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BlockedFragment extends Fragment {

    private RecyclerView recyclerView;
    private BlockedAdapter blockedAdapter;
    private BlockedNumbersDatabaseHelper blockedNumbersDatabaseHelper;
    private static final String TAG = "BlockedFragment";

    // Register the file picker activity launcher
    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importBlockedList(uri);
                    } else {
                        Toast.makeText(getContext(), "Invalid file selected", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    public void onResume() {
        super.onResume();
        refreshBlockedList();  // Refresh the list to make sure the data is updated
    }

    private void refreshBlockedList() {
        Cursor cursor = blockedNumbersDatabaseHelper.getBlockedNumbers();

        if (cursor != null && cursor.getCount() > 0) {
            List<String> blockedNumbers = new ArrayList<>();

            while (cursor.moveToNext()) {
                @SuppressLint("Range")
                String phoneNumber = cursor.getString(cursor.getColumnIndex("phone_number"));
                blockedNumbers.add(phoneNumber);
            }

            // Sort the blocked numbers list (choose one sorting method)
            Collections.reverse(blockedNumbers);
            // Collections.reverse(blockedNumbers); // Reverse order (newest last)

            if (blockedAdapter != null) {
                blockedAdapter.updateBlockedNumbers(blockedNumbers);
                blockedAdapter.notifyDataSetChanged();
            }

        } else {
            Toast.makeText(getContext(), "Block list is empty", Toast.LENGTH_SHORT).show();
        }

        if (cursor != null) {
            cursor.close();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_blocked, container, false);

        recyclerView = view.findViewById(R.id.recyclerViewBlocked);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        Button btnExportBlockedList = view.findViewById(R.id.btnExportBlockedList);
        Button btnImportBlockedList = view.findViewById(R.id.btnImportBlockedList);
        FloatingActionButton fab =  view.findViewById(R.id.fab);
        fab.setOnClickListener(v -> showBlockNameDialog());
        blockedNumbersDatabaseHelper = new BlockedNumbersDatabaseHelper(getContext());

        blockedAdapter = new BlockedAdapter(getContext(), new ArrayList<>(), new BlockedAdapter.OnBlockedListener() {
            @Override
            public void onUnblock(String phoneNumber) {
                unblockSender(phoneNumber);  // Direct method to unblock sender
            }

            @Override
            public void onSetRetentionPeriod(String phoneNumber, long retentionMillis) {
                setRetentionPeriod(phoneNumber, retentionMillis);
            }

            @Override
            public void onChat(String phoneNumber) {
                openChatActivity(phoneNumber);
            }
        });

        recyclerView.setAdapter(blockedAdapter);

        btnImportBlockedList.setOnClickListener(v -> selectFileToImport());
        btnExportBlockedList.setOnClickListener(v -> exportBlockedList());

        return view;
    }

    private void showBlockNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_block_name, null);
        builder.setView(dialogView);

        AutoCompleteTextView etBlockName = dialogView.findViewById(R.id.etBlockName);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnBlock = dialogView.findViewById(R.id.btnBlock);

        // Fetch contacts and set up adapter
        List<String> contacts = getContacts();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, contacts);
        etBlockName.setAdapter(adapter);

        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnBlock.setOnClickListener(v -> {
            String blockNumber = etBlockName.getText().toString().trim();

            if (!blockNumber.isEmpty()) {
                blockedNumbersDatabaseHelper.blockNumber(blockNumber);
                Toast.makeText(requireContext(), "Blocked: " + blockNumber, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                refreshBlockedList();
            } else {
                etBlockName.setError("Enter Number or Name");
            }
        });
    }

    private List<String> getContacts() {
        List<String> contactList = new ArrayList<>();
        Map<String, String> contactMap = new HashMap<>(); // Stores <Number, Name>

        ContentResolver contentResolver = requireContext().getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String number = cursor.getString(1);

                if (number != null) {
                    contactMap.put(number, name != null && !name.isEmpty() ? name : number);
                }
            }
            cursor.close();
        }

        // Add only unique display values (name if available, otherwise number)
        for (Map.Entry<String, String> entry : contactMap.entrySet()) {
            contactList.add(entry.getValue()); // Only add display name (or number if no name)
        }

        return contactList;
    }


    private void unblockSender(String phoneNumber) {
        // Remove the sender from the blocked numbers in the database
        BlockedNumbersDatabaseHelper blockedNumbersDatabaseHelper = new BlockedNumbersDatabaseHelper(getContext());
        blockedNumbersDatabaseHelper.removeBlockedNumber(phoneNumber);

        // Also remove from SharedPreferences
        SharedPreferences prefs = getContext().getSharedPreferences("BlockedSMS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(phoneNumber); // Remove the block status for this phone number
        editor.apply();

        // Show a toast message
        Toast.makeText(getContext(), "Unblocked: " + phoneNumber, Toast.LENGTH_SHORT).show();

        // Refresh the UI to reflect changes
        refreshBlockedList();  // <-- ADD THIS LINE
    }




    public void setRetentionPeriod(String phoneNumber, long retentionMillis) {
        // Correctly calculate unblock time (do not add current time again)
        long unblockTime = retentionMillis;

        // Store unblock time in the database
        blockedNumbersDatabaseHelper.unblockNumber(phoneNumber, unblockTime);

        // Calculate retention period in days and display correct message

    }

    private void selectFileToImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(intent);
    }

    private void importBlockedList(Uri fileUri) {
        try (InputStream inputStream = getContext().getContentResolver().openInputStream(fileUri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }

            JSONArray jsonArray = new JSONArray(jsonContent.toString());

            // Import each phone number from the JSON without setting a retention period
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String phoneNumber = jsonObject.optString("PhoneNumber", "Unknown");

                // Directly block the number without retention period
                blockedNumbersDatabaseHelper.blockNumber(phoneNumber);
            }

            getActivity().runOnUiThread(() -> {
                refreshBlockedList();
                Toast.makeText(getContext(), "Import Successful!", Toast.LENGTH_LONG).show();
            });

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Import Failed: " + e.getMessage());
            Toast.makeText(getContext(), "Import Failed!", Toast.LENGTH_LONG).show();
        }
    }

    private Uri createFileUri(String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContext().getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            contentValues.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            return Uri.fromFile(directory);
        }
    }

    private void exportBlockedList() {
        try {
            JSONArray jsonArray = new JSONArray();
            Cursor cursor = blockedNumbersDatabaseHelper.getBlockedNumbers();

            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    @SuppressLint("Range")
                    String phoneNumber = cursor.getString(cursor.getColumnIndex("phone_number"));
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("PhoneNumber", phoneNumber);
                    jsonArray.put(jsonObject);
                }

                // Create URI and write to output stream
                Uri uri = createFileUri("blocked_sms_list.json");
                try (OutputStream outputStream = getContext().getContentResolver().openOutputStream(uri)) {
                    if (outputStream != null) {
                        outputStream.write(jsonArray.toString().getBytes());
                        Toast.makeText(getContext(), "Export Successful!", Toast.LENGTH_LONG).show();
                    } else {
                        Log.e(TAG, "Failed to open OutputStream.");
                        Toast.makeText(getContext(), "Export Failed!", Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                Toast.makeText(getContext(), "No blocked numbers to export", Toast.LENGTH_SHORT).show();
            }

            // Don't forget to close the cursor after use
            if (cursor != null) {
                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Export Failed: " + e.getMessage());
            Toast.makeText(getContext(), "Export Failed!", Toast.LENGTH_LONG).show();
        }
    }

    private void openChatActivity(String phoneNumber) {
        // Check if the number is blocked
        boolean isBlocked = blockedNumbersDatabaseHelper.isBlocked(phoneNumber);

        // Open ChatActivity when a blocked number is clicked
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra("senderPhoneNumber", phoneNumber);  // Pass the phone number to the ChatActivity
        intent.putExtra("isBlocked", isBlocked);  // Pass the isBlocked flag
        startActivity(intent);
    }
}
