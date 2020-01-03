package com.meisterschueler.ognviewer.activity;

import android.content.pm.PackageManager;
import android.os.Bundle;

import com.meisterschueler.ognviewer.activity.base.BaseActivity;
import com.meisterschueler.ognviewer.common.AppConstants;
import com.meisterschueler.ognviewer.ui.ManageIDsFragment;

public class ManageIDsActivity extends BaseActivity {

    private ManageIDsFragment manageIDsActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        manageIDsActivity = new ManageIDsFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, manageIDsActivity)
                .commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case AppConstants.REQUEST_CODE_STORAGE_IMPORT: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    manageIDsActivity.importItems();
                }
                break;
            }
            case AppConstants.REQUEST_CODE_STORAGE_EXPORT: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    manageIDsActivity.exportItems();
                }
                break;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

}
