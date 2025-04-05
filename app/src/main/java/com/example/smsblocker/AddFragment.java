package com.example.smsblocker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

public class AddFragment extends Fragment {

    private static final int PICK_CONTACT_REQUEST = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add, container, false);

        @SuppressLint({"MissingInflatedId", "LocalSuppress"})
        Button selectContactButton = view.findViewById(R.id.select_contact_button);
        selectContactButton.setOnClickListener(v -> pickContact());

        return view;
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_CONTACT_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data == null || data.getData() == null) {
                Log.e("AddFragment", "No contact selected");
                return;
            }

            Uri contactUri = data.getData();
            String[] projection = {
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            };

            try (Cursor cursor = requireActivity().getContentResolver().query(contactUri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);

                    if (numberIndex >= 0 && nameIndex >= 0) {
                        String phoneNumber = cursor.getString(numberIndex);
                        String contactName = cursor.getString(nameIndex);

                        Log.d("AddFragment", "Selected contact: " + contactName + " (" + phoneNumber + ")");

                        // Open ChatActivity with selected contact
                        Intent intent = new Intent(getContext(), ChatActivity.class);
                        intent.putExtra("senderPhoneNumber", phoneNumber);
                        intent.putExtra("senderName", contactName);
                        startActivity(intent);
                    } else {
                        Log.e("AddFragment", "Invalid cursor indexes");
                    }
                } else {
                    Log.e("AddFragment", "Cursor is empty or null");
                }
            } catch (Exception e) {
                Log.e("AddFragment", "Error retrieving contact", e);
            }
        }
    }
}
