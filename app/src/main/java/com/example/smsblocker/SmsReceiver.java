package com.example.smsblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Check if the action is for receiving SMS
        if (intent.getAction() == null || !intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            return;
        }

        // Get the SMS messages from the intent
        Object[] pdus = (Object[]) intent.getExtras().get("pdus");
        if (pdus == null) {
            return;
        }

        // Loop through all the received messages
        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);

            // Get sender phone number
            String senderPhoneNumber = smsMessage.getOriginatingAddress();

            // Check if the sender is blocked using the database
            if (isBlocked(context, senderPhoneNumber)) {
                // Block the SMS by aborting the broadcast
                abortBroadcast();
                return; // Stop further processing if blocked
            }
        }
    }

    private boolean isBlocked(Context context, String senderPhoneNumber) {
        // Check if the sender is in the blocked list using the database
        BlockedNumbersDatabaseHelper dbHelper = new BlockedNumbersDatabaseHelper(context);
        return dbHelper.isBlocked(senderPhoneNumber);
    }
}
