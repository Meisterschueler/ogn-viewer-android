package com.meisterschueler.ognviewer.activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.meisterschueler.ognviewer.activity.base.BaseActivity;
import com.meisterschueler.ognviewer.common.AppConstants;
import com.meisterschueler.ognviewer.ui.PrefsFragment;

import timber.log.Timber;

public class PrefsActivity extends BaseActivity {

    PrefsFragment prefsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefsFragment = new PrefsFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, prefsFragment)
                .commit();

        Intent intent = new Intent();
        intent.putExtra("MESSAGE","Prefs Activity finished");
        setResult(AppConstants.ACTIVITY_REQUEST_CODE_SETTINGS, intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case AppConstants.REQUEST_CODE_LOCATION_TCP_UPDATES: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    prefsFragment.setTCPServerActiveState(true);
                } else {
                    Timber.d("Location permission for TCP Server denied");
                    prefsFragment.setTCPServerActiveState(false);
                }
                break;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }

    }

}
