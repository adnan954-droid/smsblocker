package com.example.smsblocker;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.MenuItem;


import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {


    private SmsReceiver smsReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupToolbar();
        checkAndRequestDefaultSmsApp();
        setupBottomNavigation(savedInstanceState);

        // Register the SMSReceiver dynamically for SMS blocking
        smsReceiver = new SmsReceiver();
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(smsReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the receiver to prevent memory leaks
        unregisterReceiver(smsReceiver);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("SMS Blocker");
    }

    private void checkAndRequestDefaultSmsApp() {
        if (!isDefaultSmsApp()) {
            showSetDefaultSmsAppDialog();
        }
    }

    private boolean isDefaultSmsApp() {
        return getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));
    }

    private void showSetDefaultSmsAppDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Set as Default SMS App")
                .setMessage("This app needs to be set as your default SMS app to work properly. Do you want to set it as default?")
                .setPositiveButton("Yes", (dialog, which) -> setAsDefaultSmsApp())
                .setNegativeButton("No", null)
                .show();
    }

    private void setAsDefaultSmsApp() {
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
        startActivity(intent);
    }

    private void setupBottomNavigation(Bundle savedInstanceState) {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this::handleNavigationSelection);

        if (savedInstanceState == null) {
            loadFragment(new InboxFragment());
            bottomNavigationView.setSelectedItemId(R.id.nav_inbox);
        }
    }

    private boolean handleNavigationSelection(MenuItem item) {
        Fragment selectedFragment;

        if (item.getItemId() == R.id.nav_inbox) {
            selectedFragment = new InboxFragment();
        } else if (item.getItemId() == R.id.nav_add) {
            selectedFragment = new AddFragment();
        } else if (item.getItemId() == R.id.nav_blocked) {
            selectedFragment = new BlockedFragment();
        } else {
            return false;
        }

        loadFragment(selectedFragment);
        return true;
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }


}
