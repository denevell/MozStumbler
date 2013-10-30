package org.mozilla.mozstumbler;

import org.mozilla.mozstumbler.preferences.PreferencesScreen;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String LOGTAG = MainActivity.class.getName();
    private static final String LEADERBOARD_URL = "https://location.services.mozilla.com/leaders";
    private static final String ABOUT_PAGE_URL = "https://wiki.mozilla.org/Services/Location/About";

    private ScannerServiceInterface  mConnectionRemote;
    private ServiceConnection        mConnection;
    private ServiceBroadcastReceiver mReceiver;
    private int                      mGpsFixes;
    private StatsView mStatsView;

    private class ServiceBroadcastReceiver extends BroadcastReceiver {
        private boolean mReceiverIsRegistered;

        public void register() {
            if (!mReceiverIsRegistered) {
                registerReceiver(this, new IntentFilter(ScannerService.MESSAGE_TOPIC));
                mReceiverIsRegistered = true;
            }
        }

        public void unregister() {
            if (mReceiverIsRegistered) {
                unregisterReceiver(this);
                mReceiverIsRegistered = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (!action.equals(ScannerService.MESSAGE_TOPIC)) {
                Log.e(LOGTAG, "Received an unknown intent");
                return;
            }

            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

            if (subject.equals("Notification")) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                Toast.makeText(getApplicationContext(), (CharSequence) text, Toast.LENGTH_SHORT).show();
                Log.d(LOGTAG, "Received a notification intent and showing: " + text);
                return;
            } else if (subject.equals("Reporter")) {
                updateUI();
                Log.d(LOGTAG, "Received a reporter intent...");
                return;
            } else if (subject.equals("Scanner")) {
                mGpsFixes = intent.getIntExtra("fixes", 0);
                updateUI();
                Log.d(LOGTAG, "Received a scanner intent...");
                return;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableStrictMode();
        setContentView(R.layout.activity_main);
        mStatsView = (StatsView) findViewById(R.id.view_stats);
        Updater.checkForUpdates(this);

        // Temporarily disable map button on Gingerbread and older Honeycomb devices.
        if (VERSION.SDK_INT < 12) {
            Button mapButton = (Button) findViewById(R.id.view_map);
            mapButton.setEnabled(false);
        }

        Log.d(LOGTAG, "onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();

        mReceiver = new ServiceBroadcastReceiver();
        mReceiver.register();

        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
                mConnectionRemote = ScannerServiceInterface.Stub.asInterface(binder);
                Log.d(LOGTAG, "Service connected");
                updateUI();
            }

            public void onServiceDisconnected(ComponentName className) {
                mConnectionRemote = null;
                Log.d(LOGTAG, "Service disconnected", new Exception());
            }
        };

        Intent intent = new Intent(this, ScannerService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mConnection);
        mConnection = null;
        mConnectionRemote = null;
        mReceiver.unregister();
        mReceiver = null;
        Log.d(LOGTAG, "onStop");
    }

    protected void updateUI() {
        // TODO time this to make sure we're not blocking too long on mConnectionRemote
        // if we care, we can bundle this into one call -- or use android to remember
        // the state before the rotation.

        if (mConnectionRemote == null) {
            return;
        }

        Log.d(LOGTAG, "Updating UI");
        boolean scanning = false;
        try {
            scanning = mConnectionRemote.isScanning();
        } catch (RemoteException e) {
            Log.e(LOGTAG, "", e);
        }

        Button scanningBtn = (Button) findViewById(R.id.toggle_scanning);
        if (scanning) {
            scanningBtn.setText(R.string.stop_scanning);
        } else {
            scanningBtn.setText(R.string.start_scanning);
        }

        try {
            int locationsScanned = mConnectionRemote.getLocationCount();
            int APs = mConnectionRemote.getAPCount();
            long lastUploadTime = mConnectionRemote.getLastUploadTime();
            long reportsSent = mConnectionRemote.getReportsSent();
            mStatsView.updateStats(locationsScanned, APs, lastUploadTime, reportsSent, mGpsFixes);
        } catch (RemoteException e) {
            Log.e(LOGTAG, "", e);
        }
    }

    public void onClick_ToggleScanning(View v) throws RemoteException {
        if (mConnectionRemote == null) {
            return;
        }

        boolean scanning = mConnectionRemote.isScanning();
        Log.d(LOGTAG, "Connection remote return: isScanning() = " + scanning);

        Button b = (Button) v;
        if (scanning) {
            mConnectionRemote.stopScanning();
            b.setText(R.string.start_scanning);
        } else {
            mConnectionRemote.startScanning();
            b.setText(R.string.stop_scanning);
        }
    }

    public void onClick_ViewLeaderboard(View v) {
        Intent openLeaderboard = new Intent(Intent.ACTION_VIEW, Uri.parse(LEADERBOARD_URL));
        startActivity(openLeaderboard);
    }

    public void onClick_ViewMap(View v) throws RemoteException {
        // We are starting Wi-Fi scanning because we want the the APs for our
        // geolocation request whose results we want to display on the map.
        if (mConnectionRemote != null) {
            mConnectionRemote.startScanning();
        }

        Log.d(LOGTAG, "onClick_ViewMap");
        Intent intent = new Intent(this, MapActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            Intent openAboutPage = new Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_PAGE_URL));
            startActivity(openAboutPage);
            return true;
        } else if (item.getItemId() == R.id.action_preferences) {
            Intent open= new Intent();
            open.setClass(getApplication(), PreferencesScreen.class);
            startActivity(open);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @TargetApi(9)
    private void enableStrictMode() {
        if (VERSION.SDK_INT < 9) {
            return;
        }

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                                              .detectAll()
                                                              .permitDiskReads()
                                                              .permitDiskWrites()
                                                              .penaltyLog().build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                                      .detectAll()
                                                      .penaltyLog().build());
    }

}
