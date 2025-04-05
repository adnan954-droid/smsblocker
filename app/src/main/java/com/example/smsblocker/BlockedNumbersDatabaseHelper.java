package com.example.smsblocker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public class BlockedNumbersDatabaseHelper {

    private static final String DATABASE_NAME = "sms_blocker.db";
    private static final int DATABASE_VERSION = 3;  // Incremented version for schema changes
    private static final String TABLE_BLOCKED_NUMBERS = "blocked_numbers";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_PHONE_NUMBER = "phone_number";
    private static final String COLUMN_UNBLOCK_TIME = "unblock_time";  // Column for retention period

    private SQLiteDatabase database;
    private BlockedNumbersSQLiteOpenHelper dbHelper;

    public BlockedNumbersDatabaseHelper(Context context) {
        dbHelper = new BlockedNumbersSQLiteOpenHelper(context);
        this.database = dbHelper.getWritableDatabase();  // Open the database in writable mode
    }

    // Method to block a phone number (without retention period)
    public void blockNumber(String phoneNumber) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_PHONE_NUMBER, phoneNumber);  // Store phone number
        values.put(COLUMN_UNBLOCK_TIME, 0); // Default unblock time as 0

        long rowId = database.insert(TABLE_BLOCKED_NUMBERS, null, values);

        // Log the insert operation result
        if (rowId != -1) {
            Log.d("Database", "Inserted blocked number: " + phoneNumber);
        } else {
            Log.e("Database", "Failed to insert blocked number: " + phoneNumber);
        }
    }

    // Method to unblock a number (set unblock time)
    public boolean unblockNumber(String phoneNumber, long unblockTime) {
        long currentTime = System.currentTimeMillis();

        // Check if a retention period is already set
        Cursor cursor = database.rawQuery(
                "SELECT is_retention_set, " + COLUMN_UNBLOCK_TIME + " FROM " + TABLE_BLOCKED_NUMBERS +
                        " WHERE phone_number = ?",
                new String[]{phoneNumber}
        );

        boolean canUpdate = true;

        if (cursor != null && cursor.moveToFirst()) {
            int isSet = cursor.getInt(0);
            long existingUnblockTime = cursor.getLong(1);

            // If retention period has expired, allow updating
            if (isSet == 1 && existingUnblockTime > currentTime) {
                canUpdate = false; // Do not update if time is still active
            }
            cursor.close();
        }

        if (!canUpdate) {
            Log.d("Database", "Retention period is already active, cannot update.");
            return false;
        }

        // Update the retention period in the database
        ContentValues values = new ContentValues();
        values.put(COLUMN_UNBLOCK_TIME, unblockTime);
        values.put("is_retention_set", 1);  // Mark as set

        int rowsAffected = database.update(TABLE_BLOCKED_NUMBERS, values,
                COLUMN_PHONE_NUMBER + "=?", new String[]{phoneNumber});

        return rowsAffected > 0;
    }


    // Check if a phone number is blocked
    public boolean isBlocked(String phoneNumber) {
        Cursor cursor = null;
        try {
            cursor = database.query(TABLE_BLOCKED_NUMBERS,
                    new String[]{COLUMN_PHONE_NUMBER},
                    COLUMN_PHONE_NUMBER + " = ? AND (" + COLUMN_UNBLOCK_TIME + " = 0 OR " + COLUMN_UNBLOCK_TIME + " > ?)",
                    new String[]{phoneNumber, String.valueOf(System.currentTimeMillis())},  // Only return numbers still blocked
                    null, null, null);

            boolean isBlocked = cursor.getCount() > 0;
            Log.d("Database", "Phone number " + phoneNumber + " isBlocked: " + isBlocked);
            return isBlocked;
        } finally {
            if (cursor != null) {
                cursor.close();  // Ensure the cursor is closed after use
            }
        }
    }

    // Get all currently blocked numbers
// Get all currently blocked numbers (excluding expired ones)
    public Cursor getBlockedNumbers() {
        long currentTime = System.currentTimeMillis();
        database.delete(TABLE_BLOCKED_NUMBERS, COLUMN_UNBLOCK_TIME + " > 0 AND " + COLUMN_UNBLOCK_TIME + " <= ?",
                new String[]{String.valueOf(currentTime)});  // Delete expired numbers

        String selection = COLUMN_UNBLOCK_TIME + " = 0 OR " + COLUMN_UNBLOCK_TIME + " > ?";
        String[] selectionArgs = new String[]{String.valueOf(currentTime)};

        return database.query(TABLE_BLOCKED_NUMBERS, null, selection, selectionArgs, null, null, null);
    }

    // Remove a blocked number (unblock it)
    public void removeBlockedNumber(String phoneNumber) {
        int rowsDeleted = database.delete(TABLE_BLOCKED_NUMBERS, COLUMN_PHONE_NUMBER + " = ?", new String[]{phoneNumber});

        // Log the result of the delete operation
        if (rowsDeleted > 0) {
            Log.d("Database", "Removed blocked number: " + phoneNumber);
        } else {
            Log.e("Database", "Failed to remove blocked number: " + phoneNumber);
        }
    }

    // Close the database connection
    public void close() {
        if (database != null && database.isOpen()) {
            database.close();
            Log.d("Database", "Database connection closed");
        }
    }



    // Database helper class for creating and upgrading the database schema
    private static class BlockedNumbersSQLiteOpenHelper extends android.database.sqlite.SQLiteOpenHelper {

        public BlockedNumbersSQLiteOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // SQL to create the blocked_numbers table
            String createTableSQL = "CREATE TABLE " + TABLE_BLOCKED_NUMBERS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_PHONE_NUMBER + " TEXT NOT NULL, " +
                    COLUMN_UNBLOCK_TIME + " INTEGER NOT NULL DEFAULT 0);";
            db.execSQL(createTableSQL);

            Log.d("Database", "Table created: " + TABLE_BLOCKED_NUMBERS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                String alterTableSQL = "ALTER TABLE " + TABLE_BLOCKED_NUMBERS + " ADD COLUMN " + COLUMN_UNBLOCK_TIME + " INTEGER NOT NULL DEFAULT 0;";
                db.execSQL(alterTableSQL);
            }
            if (oldVersion < 3) { // Increment version when adding a new column
                String alterTableSQL = "ALTER TABLE " + TABLE_BLOCKED_NUMBERS + " ADD COLUMN is_retention_set INTEGER NOT NULL DEFAULT 0;";
                db.execSQL(alterTableSQL);
            }
        }
    }
}
