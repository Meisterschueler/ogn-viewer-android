package com.meisterschueler.ognviewer.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.meisterschueler.ognviewer.R;
import com.meisterschueler.ognviewer.common.AppConstants;
import com.meisterschueler.ognviewer.service.OgnService;

import timber.log.Timber;

public class PrefsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();

        updateFragmentValues(sharedPreferences, getString(R.string.key_movingfilter_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_movingfilter_range_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_aprsfilter_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_aprsserver_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_showaircrafts_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_showreceivers_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_map_type_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_screen_orientation_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_keepscreenon_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_shownonmoving_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_showregistration_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_rotate_aircraft_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_aircraft_flightpath_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_aircraft_colorisation_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_aircraft_timeout_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_shownotactive_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_receiver_colorisation_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_altitude_unit_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_groundspeed_unit_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_verticalspeed_unit_preference));
        updateFragmentValues(sharedPreferences, getString(R.string.key_tcp_server_active_preference));
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateFragmentValues(sharedPreferences, key);
        if (key.equals(getString(R.string.key_aprsfilter_preference))
                | key.equals(getString(R.string.key_aprsserver_preference))
                | key.equals(getString(R.string.key_movingfilter_preference))
                | key.equals(getString(R.string.key_movingfilter_range_preference))) {
            getActivity().startService(new Intent(getActivity(), OgnService.class));
        } else if (key.equals(getString(R.string.key_tcp_server_active_preference))) {
            Boolean tcpServerActive = sharedPreferences.getBoolean(getString(R.string.key_tcp_server_active_preference), false);
            Boolean movingFilterActive = sharedPreferences.getBoolean(getString(R.string.key_movingfilter_preference), true);
            if (tcpServerActive | movingFilterActive) {
                requestLocationPermission();
            } else {
                // TCP updates will be stopped when switched back to MapsActivity
            }
        }
    }

    private void updateFragmentValues(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);

        if (key.equals(getString(R.string.key_movingfilter_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_movingfilter_preference), true);
            if (value) {
                pref.setSummary("on");
                findPreference(getString(R.string.key_movingfilter_range_preference)).setEnabled(true);
                findPreference(getString(R.string.key_aprsfilter_preference)).setEnabled(false);
            } else {
                pref.setSummary("off");
                findPreference(getString(R.string.key_movingfilter_range_preference)).setEnabled(false);
                findPreference(getString(R.string.key_aprsfilter_preference)).setEnabled(true);
            }
        } else if (key.equals(getString(R.string.key_movingfilter_range_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_movingfilter_range_preference), getString(R.string.distance_10km));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_aprsfilter_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_aprsfilter_preference), "");
            if (value.isEmpty()) {
                pref.setSummary(getString(R.string.empty_aprsfilter_preference));
            } else {
                pref.setSummary(value);
            }
        } else if (key.equals(getString(R.string.key_aprsserver_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_aprsserver_preference), getString(R.string.default_aprsserver));
            if (value.isEmpty()) {
                pref.setSummary(getString(R.string.default_aprsserver));
            } else {
                pref.setSummary(value);
            }

        } else if (key.equals(getString(R.string.key_showaircrafts_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_showaircrafts_preference), true);
            if (value) {
                pref.setSummary("on");
            } else {
                pref.setSummary("off");
            }
        } else if (key.equals(getString(R.string.key_showreceivers_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_showreceivers_preference), false);
            if (value) {
                pref.setSummary("on");
            } else {
                pref.setSummary("off");
            }

        } else if (key.equals(getString(R.string.key_map_type_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_map_type_preference), getString(R.string.terrain));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_screen_orientation_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_screen_orientation_preference), getString(R.string.orientation_automatic));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_keepscreenon_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_keepscreenon_preference), false);
            if (value) {
                pref.setSummary("on");
            } else {
                pref.setSummary("off");
            }
        } else if (key.equals(getString(R.string.key_shownonmoving_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_shownonmoving_preference), true);
            if (value) {
                pref.setSummary("on");
            } else {
                pref.setSummary("off");
            }
        } else if (key.equals(getString(R.string.key_showregistration_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_showregistration_preference), true);
            if (value) {
                pref.setSummary("on");
            } else {
                pref.setSummary("off");
            }
        } else if (key.equals(getString(R.string.key_rotate_aircraft_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_rotate_aircraft_preference), false);
            if (value) {
                pref.setSummary("on");
            } else {
                pref.setSummary("off");
            }
        } else if (key.equals(getString(R.string.key_aircraft_flightpath_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_aircraft_flightpath_preference), getString(R.string.flightpath_standard));
            if (value.equals(getString(R.string.flightpath_multicolor))){
                pref.setSummary(R.string.flightpath_multicolor_descr);
            } else {
                pref.setSummary(value);
            }
        } else if (key.equals(getString(R.string.key_shownotactive_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_shownotactive_preference), true);
            if (value) {
                pref.setSummary("on");
            } else {
                pref.setSummary("off");
            }
        } else if (key.equals(getString(R.string.key_aircraft_colorisation_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_aircraft_colorisation_preference), getString(R.string.altitude));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_aircraft_timeout_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_aircraft_timeout_preference), getString(R.string.time_5m));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_receiver_colorisation_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_receiver_colorisation_preference), getString(R.string.aircraft_count));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_altitude_unit_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_altitude_unit_preference), getString(R.string.unit_meters));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_groundspeed_unit_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_groundspeed_unit_preference), getString(R.string.unit_kmh));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_verticalspeed_unit_preference))) {
            String value = sharedPreferences.getString(getString(R.string.key_verticalspeed_unit_preference), getString(R.string.unit_ms));
            pref.setSummary(value);
        } else if (key.equals(getString(R.string.key_tcp_server_active_preference))) {
            Boolean value = sharedPreferences.getBoolean(getString(R.string.key_tcp_server_active_preference), false);
            if (value) {
                pref.setSummary("on");
            } else {
                pref.setSummary("off");
            }
        }
    }

    private void requestLocationPermission() {
        final String fineLocationPermissionString = Manifest.permission.ACCESS_FINE_LOCATION;

        if (ContextCompat.checkSelfPermission(getActivity(), fineLocationPermissionString) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{fineLocationPermissionString}, AppConstants.REQUEST_CODE_LOCATION_TCP_UPDATES);
        } else {
            // Permission has already been granted
            Timber.d("Location permission granted");
        }
    }

    public void setTCPServerActiveState(Boolean active) {
        SwitchPreference tcpPref = findPreference(getString(R.string.key_tcp_server_active_preference));

        if (active) {
            tcpPref.setSummary("on");
            tcpPref.setChecked(true);
        } else {
            tcpPref.setSummary("off");
            tcpPref.setChecked(false);
        }
    }
}
