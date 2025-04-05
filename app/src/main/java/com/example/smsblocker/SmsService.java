package com.example.smsblocker;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Telephony;

public class SmsService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            // Handle incoming SMS (if needed)
        }
        return START_STICKY;
    }
}

