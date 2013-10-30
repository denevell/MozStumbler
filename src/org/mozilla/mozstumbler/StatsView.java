package org.mozilla.mozstumbler;

import android.content.Context;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;

public class StatsView extends FrameLayout {

    private static final String LOGTAG = MainActivity.class.getName();
    public StatsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.stats_layout, this);
        updateStats(0, 0, 0, 0, 0);
    }

    public StatsView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs); // We're not using styles atm
    }

    public void updateStats(int locationsScanned, int APs, long lastUploadTime, long reportsSent, int gpsFixes) {
        String lastUploadTimeString = (lastUploadTime > 0)
                                      ? DateTimeUtils.formatTimeForLocale(lastUploadTime)
                                      : "-";

        formatTextView(R.id.gps_satellites, R.string.gps_satellites, gpsFixes);
        formatTextView(R.id.wifi_access_points, R.string.wifi_access_points, APs);
        formatTextView(R.id.locations_scanned, R.string.locations_scanned, locationsScanned);
        formatTextView(R.id.last_upload_time, R.string.last_upload_time, lastUploadTimeString);
        formatTextView(R.id.reports_sent, R.string.reports_sent, reportsSent);
    }

    private void formatTextView(int textViewId, int stringId, Object... args) {
        TextView textView = (TextView) findViewById(textViewId);
        String str = getResources().getString(stringId);
        str = String.format(str, args);
        textView.setText(str);
    }

}
