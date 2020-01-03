package com.meisterschueler.ognviewer.service;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.meisterschueler.ognviewer.CustomAircraftDescriptor;
import com.meisterschueler.ognviewer.R;
import com.meisterschueler.ognviewer.activity.ClosingActivity;
import com.meisterschueler.ognviewer.activity.MapsActivity;
import com.meisterschueler.ognviewer.common.AircraftDescriptorProviderHelper;
import com.meisterschueler.ognviewer.common.AppConstants;
import com.meisterschueler.ognviewer.common.AprsFilterManager;
import com.meisterschueler.ognviewer.common.CustomAircraftDescriptorProvider;
import com.meisterschueler.ognviewer.common.FlarmMessage;
import com.meisterschueler.ognviewer.common.ReceiverBeaconImplReplacement;
import com.meisterschueler.ognviewer.common.ReceiverBundle;
import com.meisterschueler.ognviewer.common.entity.Aircraft;
import com.meisterschueler.ognviewer.common.entity.AircraftBundle;

import org.ogn.client.AircraftBeaconListener;
import org.ogn.client.OgnClient;
import org.ogn.client.ReceiverBeaconListener;
import org.ogn.commons.beacon.AircraftBeacon;
import org.ogn.commons.beacon.AircraftDescriptor;
import org.ogn.commons.beacon.AircraftType;
import org.ogn.commons.beacon.ReceiverBeacon;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import co.uk.rushorm.android.AndroidInitializeConfig;
import co.uk.rushorm.core.Rush;
import co.uk.rushorm.core.RushCore;
import timber.log.Timber;

public class OgnService extends Service implements AircraftBeaconListener, ReceiverBeaconListener {

    private TcpServer tcpServer;

    private OgnClient ognClient; //initialized in onStartCommand
    private boolean ognConnected = false; //is true, when service was started (onStartCommand)
    private LocalBroadcastManager localBroadcastManager;
    private IBinder binder = new LocalBinder();
    //Map<String, AircraftBundle> aircraftMap = new ConcurrentHashMap<>(); // for WIP 2017-11-02
    private Map<String, Aircraft> aircraftMap = new HashMap<>();

    private int maxAircraftCounter = 0;
    private int maxBeaconCounter = 0;
    private Map<String, ReceiverBundle> receiverBundleMap = new ConcurrentHashMap<>();
    private Map<String, AircraftBundle> aircraftBundleMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduledTaskExecutor;
    private boolean refreshingActive = false; // if true, markers on map should be updated
    private boolean mapCurrentlyUpdating = false; // if true, the map is currently updating (new updates should wait)
    private boolean timerCurrentlyRunning = false;
    private LatLngBounds latLngBounds = new LatLngBounds(new LatLng(0, 0), new LatLng(0, 0));
    private int aircraftTimeoutInSec = AppConstants.DEFAULT_AIRCRAFT_TIMEOUT_IN_SEC;
    private boolean locationUpdatesAlreadyRequested = false;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;

    private CustomAircraftDescriptorProvider customAircraftDescriptorProvider;
    private Location currentLocation = null;
    private Location movingFilterLocation = null;

    public Map<String, ReceiverBundle> getReceiverBundleMap() {
        return receiverBundleMap;
    }

    public Map<String, AircraftBundle> getAircraftBundleMap() {
        return aircraftBundleMap;
    }

    public void setMapUpdatingStatus(boolean updating) {
        mapCurrentlyUpdating = updating;
    }

    public void setAircraftTimeout(int timoutInSec) {
        aircraftTimeoutInSec = timoutInSec;
    }

    public interface UpdateListener { // for WIP 2017-11-02
        public void updateAircraftBundle(AircraftBundle bundle);
    }

    public OgnService() {
        tcpServer = new TcpServer();
        tcpServer.startServer();
    }

    // Trigger new location updates at interval
    public void startLocationUpdates(Activity activity) {
        if (locationUpdatesAlreadyRequested) {
            return;
        }
        Context context = getApplicationContext();
        long UPDATE_INTERVAL = 5 * 1000;  /* 5 secs */
        long FASTEST_INTERVAL = 1000; /* 1 sec */

        LocationRequest locationRequest;
        // Create the location request to start receiving updates
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        // Create LocationSettingsRequest object using location request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();

        // Check whether location settings are satisfied
        // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
        SettingsClient settingsClient = LocationServices.getSettingsClient(context);
        settingsClient.checkLocationSettings(locationSettingsRequest);

        final String fineLocationPermissionString = Manifest.permission.ACCESS_FINE_LOCATION;
        final String coarseLocationPermissionString = Manifest.permission.ACCESS_COARSE_LOCATION;
        // maybe fine should be enough? 2018-03-12
        if (ContextCompat.checkSelfPermission(context, fineLocationPermissionString) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, coarseLocationPermissionString) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, fineLocationPermissionString)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                Timber.d("Location permission already denied");
                // ask again? in activity? 2018-03-23
                // onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
                // https://developer.android.com/training/permissions/requesting.html#java
            } else {
                // User was never asked to allow location updates. Ask now!
                ActivityCompat.requestPermissions(activity, new String[]{fineLocationPermissionString, coarseLocationPermissionString},
                        AppConstants.REQUEST_CODE_LOCATION_TCP_UPDATES_FROM_SERVICE);
            }
            return;
        } else {
            // Permission has already been granted
            Timber.d("Location permission granted");
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
        if (locationCallback == null) {
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    currentLocation = locationResult.getLastLocation();
                    tcpServer.updatePosition(currentLocation);
                    sendLocationToMap(currentLocation);
                    if (movingFilterLocation == null || movingFilterLocation.distanceTo(currentLocation) > 5000) {
                        movingFilterLocation = currentLocation;
                        restartAprsClient();
                    }
                }
            };
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        locationUpdatesAlreadyRequested = true;
    }

    public void stopLocationUpdates() {
        // Does not really stop the TCP server but stop location updates!
        // TCP server does not send packets without location updates.
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
        locationUpdatesAlreadyRequested = false;
    }


    @Override
    public void onUpdate(AircraftBeacon aircraftBeacon, AircraftDescriptor aircraftDescriptor) {
        String address = aircraftBeacon.getAddress();
        if (aircraftBundleMap.containsKey(address)) {
            long lastTimestamp = aircraftBundleMap.get(address).aircraftBeacon.getTimestamp();
            long diffTimeInMS = aircraftBeacon.getTimestamp() - lastTimestamp;
            if (diffTimeInMS <= AppConstants.MINIMAL_AIRCRAFT_DIFF_TIME_IN_MS) {
                Timber.v("skipped position for %s", address);
                return; // skip deprecated positions
            }
        }

        AircraftBundle bundle = new AircraftBundle(aircraftBeacon, aircraftDescriptor);
        aircraftBundleMap.put(aircraftBeacon.getAddress(), bundle);

        ReceiverBundle receiverBundle = receiverBundleMap.get(aircraftBeacon.getReceiverName());
        if (receiverBundle != null) {
            if (!receiverBundle.aircrafts.contains(aircraftBeacon.getId())) {
                receiverBundle.aircrafts.add(aircraftBeacon.getId());
            }
            receiverBundle.beaconCount++;

            maxAircraftCounter = Math.max(maxAircraftCounter, receiverBundle.aircrafts.size());
            maxBeaconCounter = Math.max(maxBeaconCounter, receiverBundle.beaconCount);
        }
        if (refreshingActive) {
            if (!sendAircraftToMap(bundle)) {
                Timber.d("Lost beacon for aircraft: " + aircraftBeacon.getAddress() + " " + new Date().getTime());
            }
        }

        tcpServer.addFlarmMessage(new FlarmMessage(aircraftBeacon));

        //for debugging
        Calendar c = Calendar.getInstance();
        int hours = c.get(Calendar.HOUR_OF_DAY);
        int seconds = c.get(Calendar.SECOND);
        int minutes = c.get(Calendar.MINUTE);
        Timber.v(aircraftBundleMap.size() + " AircraftBeacons " + hours + ":" + minutes + ":" + seconds);
        Timber.v("Last aircraft: %s", aircraftBeacon.getAddress());
    }

    private boolean sendAircraftToMap(AircraftBundle aircraftBundle) {
        AircraftBeacon aircraftBeacon = aircraftBundle.aircraftBeacon;
        AircraftDescriptor aircraftDescriptor = aircraftBundle.aircraftDescriptor;
        Intent intent = new Intent(AppConstants.INTENT_AIRCRAFT_BEACON);

        // AircraftBeacon
        intent.putExtra("receiverName", aircraftBeacon.getReceiverName());
        intent.putExtra("addressType", aircraftBeacon.getAddressType().getCode());
        intent.putExtra("address", aircraftBeacon.getAddress());
        intent.putExtra("aircraftType", aircraftBeacon.getAircraftType().getCode());
        intent.putExtra("stealth", aircraftBeacon.isStealth());
        intent.putExtra("climbRate", aircraftBeacon.getClimbRate());
        intent.putExtra("turnRate", aircraftBeacon.getTurnRate());
        intent.putExtra("signalStrength", aircraftBeacon.getSignalStrength());
        intent.putExtra("frequencyOffset", aircraftBeacon.getFrequencyOffset());
        intent.putExtra("gpsStatus", aircraftBeacon.getGpsStatus());
        intent.putExtra("errorCount", aircraftBeacon.getErrorCount());
        //String[] getHeardAircraftIds();

        // OgnBeacon
        intent.putExtra("id", aircraftBeacon.getId());
        intent.putExtra("timestamp", aircraftBeacon.getTimestamp());
        intent.putExtra("lat", aircraftBeacon.getLat());
        intent.putExtra("lon", aircraftBeacon.getLon());
        intent.putExtra("alt", aircraftBeacon.getAlt());
        intent.putExtra("track", aircraftBeacon.getTrack());
        intent.putExtra("groundSpeed", aircraftBeacon.getGroundSpeed());
        intent.putExtra("rawPacket", aircraftBeacon.getRawPacket());

        // AircraftDescriptor
        if (aircraftDescriptor != null) {
            intent.putExtra("known", aircraftDescriptor.isKnown());
            intent.putExtra("regNumber", aircraftDescriptor.getRegNumber());
            intent.putExtra("CN", aircraftDescriptor.getCN());
            intent.putExtra("owner", aircraftDescriptor.getOwner());
            intent.putExtra("homeBase", aircraftDescriptor.getHomeBase());
            intent.putExtra("model", aircraftDescriptor.getModel());
            intent.putExtra("freq", aircraftDescriptor.getFreq());
            intent.putExtra("tracked", aircraftDescriptor.isTracked());
            intent.putExtra("identified", aircraftDescriptor.isIdentified());
        }

        if (!mapCurrentlyUpdating) { //check if something is updating the map currently
            localBroadcastManager.sendBroadcast(intent);
            return true;
        } else {
            return false;
        }
    }

    private boolean sendLocationToMap(Location location) {
        Intent intent = new Intent(AppConstants.INTENT_LOCATION);

        intent.putExtra("lat", location.getLatitude());
        intent.putExtra("lon", location.getLongitude());

        if (!mapCurrentlyUpdating) { //check if something is updating the map currently
            localBroadcastManager.sendBroadcast(intent);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onUpdate(ReceiverBeacon receiverBeacon) {
        //CAUTION: onUpdate(ReceiverBeacon) is called two times
        //the first time e.g. cpuLoad, ram, platform are 0 or empty
        //the second time lat, long and alt are 0
        ReceiverBundle existingBundle = receiverBundleMap.get(receiverBeacon.getId());
        ReceiverBundle bundle = new ReceiverBundle(receiverBeacon);
        if (existingBundle != null) {
            bundle.aircrafts = existingBundle.aircrafts;
            bundle.beaconCount = existingBundle.beaconCount;
            ReceiverBeaconImplReplacement beacon = new ReceiverBeaconImplReplacement(existingBundle.receiverBeacon);
            bundle.receiverBeacon = beacon.update(receiverBeacon);
        }

        receiverBundleMap.put(receiverBeacon.getId(), bundle);

        Intent intent = new Intent(AppConstants.INTENT_RECEIVER_BEACON);

        // ReceiverBeacon
        //intent.putExtra("cpuLoad", receiverBeacon.getCpuLoad());
        //intent.putExtra("cpuTemp", receiverBeacon.getCpuTemp());
        //intent.putExtra("freeRam", receiverBeacon.getFreeRam());
        //intent.putExtra("totalRam", receiverBeacon.getTotalRam());
        //intent.putExtra("ntpError", receiverBeacon.getNtpError());
        //intent.putExtra("rtCrystalCorrection", receiverBeacon.getRtCrystalCorrection());
        //intent.putExtra("recCrystalCorrection", receiverBeacon.getRecCrystalCorrection());
        //intent.putExtra("recCrystalCorrectionFine", receiverBeacon.getRecCrystalCorrectionFine());
        //intent.putExtra("recAbsCorrection", receiverBeacon.getRecAbsCorrection());
        intent.putExtra("recInputNoise", receiverBeacon.getRecInputNoise());
        //intent.putExtra("serverName", receiverBeacon.getServerName());
        intent.putExtra("version", receiverBeacon.getVersion());
        intent.putExtra("platform", receiverBeacon.getPlatform());
        intent.putExtra("numericVersion", receiverBeacon.getNumericVersion());

        // OgnBeacon
        intent.putExtra("id", receiverBeacon.getId());
        intent.putExtra("timestamp", receiverBeacon.getTimestamp()); //e.g. 1517918400000L
        intent.putExtra("lat", receiverBeacon.getLat());
        intent.putExtra("lon", receiverBeacon.getLon());
        intent.putExtra("alt", receiverBeacon.getAlt());
        //intent.putExtra("track", receiverBeacon.getTrack());
        //intent.putExtra("groundSpeed", receiverBeacon.getGroundSpeed());
        //intent.putExtra("rawPacket", receiverBeacon.getRawPacket());

        // Computed Values
        // does this two lines make any sense here? 2018-02-06
        // It's already set in onUpdate(Aircraft...)
        maxAircraftCounter = Math.max(maxAircraftCounter, bundle.aircrafts.size());
        maxBeaconCounter = Math.max(maxBeaconCounter, bundle.beaconCount);

        //maybe move to onUpdate(Aircraft...)? 2018-02-11
        ReceiverBundle.maxAircraftCounter = maxAircraftCounter;
        ReceiverBundle.maxBeaconCounter = maxBeaconCounter;

        intent.putExtra("aircraftCounter", bundle.aircrafts.size());
        intent.putExtra("maxAircraftCounter", maxAircraftCounter);
        intent.putExtra("beaconCounter", bundle.beaconCount);
        intent.putExtra("maxBeaconCounter", maxBeaconCounter);

        if (refreshingActive) {
            localBroadcastManager.sendBroadcast(intent);
        }

        //for debugging
        Calendar c = Calendar.getInstance();
        int hours = c.get(Calendar.HOUR_OF_DAY);
        int seconds = c.get(Calendar.SECOND);
        int minutes = c.get(Calendar.MINUTE);
        Timber.v(receiverBundleMap.size() + " ReceiverBeacons " + hours + ":" + minutes + ":" + seconds);
        Timber.v("Last receiver: %s", receiverBeacon.getId());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        List<Class<? extends Rush>> classes = new ArrayList<>();
        classes.add(CustomAircraftDescriptor.class);
        AndroidInitializeConfig config = new AndroidInitializeConfig(getApplicationContext());
        config.setClasses(classes);
        RushCore.initialize(config);

        localBroadcastManager = LocalBroadcastManager.getInstance(this);

        String versionName = "?";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MapsActivity.class), 0);

        Intent exitIntent = new Intent(this, ClosingActivity.class);
        exitIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        exitIntent.putExtra("EXIT", true);
        PendingIntent pendingExitIntent = PendingIntent.getActivity(this, 1, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String CHANNEL_ID = "com.meisterschueler.ognviewer.background";
        String CHANNEL_NAME = "OGN in background";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            createNotificationChannel(CHANNEL_ID, CHANNEL_NAME);
        }

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("OGN Viewer")
                .setContentText("Version " + versionName)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stat, "Stop", pendingExitIntent)
                .build();

        startForeground(R.string.notification_id, notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel(String channelId, String channelName) {
        NotificationChannel notificationChannel = null;
        notificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(notificationChannel);
        }
    }

    public float getMovingFilterRange() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String moving_filter_range = sharedPreferences.getString(getString(R.string.key_movingfilter_range_preference), getString(R.string.distance_10km));

        float radius = 10000;
        if (moving_filter_range.equals(getString(R.string.distance_10km))) {
            radius = 10000;
        } else if (moving_filter_range.equals(getString(R.string.distance_20km))) {
            radius = 20000;
        } else if (moving_filter_range.equals(getString(R.string.distance_30km))) {
            radius = 30000;
        } else if (moving_filter_range.equals(getString(R.string.distance_40km))) {
            radius = 40000;
        } else if (moving_filter_range.equals(getString(R.string.distance_50km))) {
            radius = 50000;
        } else if (moving_filter_range.equals(getString(R.string.distance_60km))) {
            radius = 60000;
        } else if (moving_filter_range.equals(getString(R.string.distance_70km))) {
            radius = 70000;
        } else if (moving_filter_range.equals(getString(R.string.distance_80km))) {
            radius = 80000;
        } else if (moving_filter_range.equals(getString(R.string.distance_90km))) {
            radius = 90000;
        } else if (moving_filter_range.equals(getString(R.string.distance_100km))) {
            radius = 100000;
        }
        return radius;
    }

    private void restartAprsClient() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String aprs_server = sharedPreferences.getString(getString(R.string.key_aprsserver_preference), "");
        String manual_filter = sharedPreferences.getString(getString(R.string.key_aprsfilter_preference), "");
        Boolean moving_filter = sharedPreferences.getBoolean(getString(R.string.key_movingfilter_preference), true);
        String moving_filter_range = sharedPreferences.getString(getString(R.string.key_movingfilter_range_preference), getString(R.string.distance_10km));

        float radius = getMovingFilterRange();

        if (aprs_server.isEmpty()) {
            aprs_server = getString(R.string.default_aprsserver);
        }

        if (ognConnected) {
            ognClient.disconnect();
        } else {
            ognClient = AircraftDescriptorProviderHelper.getOgnClient(aprs_server);
            ognClient.subscribeToAircraftBeacons(this);
            ognClient.subscribeToReceiverBeacons(this);
            customAircraftDescriptorProvider = (CustomAircraftDescriptorProvider)AircraftDescriptorProviderHelper.getCustomDbAircraftDescriptorProvider();
        }

        String aprs_filter;
        if (moving_filter) {
            if (currentLocation != null) {
                String range_filter = AprsFilterManager.latLngToAprsFilter(currentLocation.getLatitude(), currentLocation.getLongitude(), radius);
                String buddy_filter = customAircraftDescriptorProvider.getAprsBudlistFilter();
                aprs_filter = range_filter + " " + buddy_filter;
            } else {
                aprs_filter = manual_filter;
            }

        } else {
            aprs_filter = manual_filter;
        }

        ognClient = AircraftDescriptorProviderHelper.getOgnClient(aprs_server);
        ognClient.subscribeToAircraftBeacons(this);
        ognClient.subscribeToReceiverBeacons(this);

        if (aprs_filter.isEmpty()) {
            ognClient.connect();
            ognConnected = true;
            Toast.makeText(this, "Connected to " + aprs_server + " without filter", Toast.LENGTH_LONG).show();
        } else {
            ognClient.connect(aprs_filter);
            ognConnected = true;

            String filter;
            if (aprs_filter.length() > 30) {
                filter = aprs_filter.substring(0, 30) + "...";
            } else {
                filter = aprs_filter;
            }
            Toast.makeText(this, "Connected to OGN. Filter: " + filter, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        restartAprsClient();
        if (refreshingActive) {
            //this happens only when applyModifiedFilter in MapsActivity is called
            resumeUpdatingMap(this.latLngBounds);
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (ognConnected) {
            ognClient.disconnect();
            ognConnected = false;
        }

        Toast.makeText(this, "Disconnected from OGN", Toast.LENGTH_LONG).show();

        stopLocationUpdates();
        tcpServer.stopServer();
        pauseUpdatingMap();
    }

    //do not delete! WIP from Dominik 2017-11-05
    private void updateAircraftBeaconMarkerInDB(String address, AircraftType aircraftType, float climbRate,
                                                double lat, double lon, float alt, float groundSpeed,
                                                String regNumber, String cn, String model, boolean isOgnPrivate,
                                                String receiverName, int track) {
        if (!aircraftMap.containsKey(address)) {
            Aircraft aircraft = new Aircraft(address, aircraftType, climbRate, lat, lon, alt, groundSpeed, regNumber,
                    cn, model, isOgnPrivate, receiverName, track);
            aircraftMap.put(address, aircraft);
        } else {
            Aircraft aircraft = aircraftMap.get(address);
            aircraft.setAlt(alt);
            aircraft.setClimbRate(climbRate);
            aircraft.setCN(cn);
            aircraft.setGroundSpeed(groundSpeed);
            aircraft.setLastSeen(new Date());
            aircraft.setLat(lat);
            aircraft.setLon(lon);
            aircraft.setReceiverName(receiverName);
            aircraft.setTrack(track);
        }

    }

    public void resumeUpdatingMap(LatLngBounds latLngBounds) {
        this.latLngBounds = latLngBounds;
        refreshingActive = true;
        if(!ognConnected) {
            //This happens when no filter is set and onStartCommand was not called.
            return;
        }

        if (scheduledTaskExecutor != null) {
            Timber.d("Timer was already resumed!");
            return; //This happens when filter is already set and modified in MapsActivity.
        }

        final long initialDelayInSeconds = 2L;
        final long delayInSeconds = 2L;
        scheduledTaskExecutor = new ScheduledThreadPoolExecutor(1);
        scheduledTaskExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                //TODO: add try catches (for WIP 2017-11-05)
                if (timerCurrentlyRunning) {
                    Timber.wtf("Two or more timers active!");
                    return; //Should never happen! If this happens, two timers are active.
                } else {
                    timerCurrentlyRunning = true;
                    Timber.d("Update map by timer at %s", new Date());
                }
                aircraftMap = convertAircraftMap(aircraftBundleMap);
                Iterator<String> it = aircraftMap.keySet().iterator();
                while (it.hasNext()) {
                    String address = it.next();
                    Aircraft aircraft = aircraftMap.get(address);
                    Date now = new Date();
                    long diffSeconds = (now.getTime() - aircraftMap.get(address).getLastSeen().getTime()) / 1000;
                    // remove markers that are older than specified time e.g. 60 seconds to clean map
                    if (diffSeconds > aircraftTimeoutInSec) {
                        Intent intent = new Intent(AppConstants.INTENT_AIRCRAFT_ACTION);
                        //intent.setAction("REMOVE_AIRCRAFT");
                        intent.putExtra("AIRCRAFT_ACTION", "REMOVE_AIRCRAFT");
                        intent.putExtra("address", address);
                        localBroadcastManager.sendBroadcast(intent);

                        it.remove();
                        aircraftBundleMap.remove(address);
                        Timber.d("Removed " + address + ", diff: " + diffSeconds + "s");
                        continue;
                    }
                    //do not delete WIP! 2017-11-05
                    /*if (latLngBounds.contains(new LatLng(aircraft.getLat(), aircraft.getLon()))) {
                        while (mapCurrentlyUpdating) {
                            //wait for finishing updateMaker
                            System.out.println("Waiting for end of updateMarker: " + address);
                        }
                        sendAircraftToMap(aircraftBundleMap.get(address)); //refresh only visible markers
                    }*/
                    //maybe the following code in mapsActivity
                    /*LatLngBounds latLngBounds = mMap.getProjection().getVisibleRegion().latLngBounds;
                    if (latLngBounds.contains(new LatLng(aircraft.getLat(), aircraft.getLon()))) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateAircraftBeaconMarkerOnMap(aircraft.getAddress(), aircraft.getAircraftType(),
                                        aircraft.getClimbRate(), aircraft.getLat(), aircraft.getLon(),
                                        aircraft.getAlt(), aircraft.getGroundSpeed(), aircraft.getRegNumber(),
                                        aircraft.getCN(), aircraft.getModel(), aircraft.isOgnPrivate(),
                                        aircraft.getReceiverName(), aircraft.getTrack());
                            }

                        });
                    }*/
                }
                Timber.d("Finished updating map.");
                timerCurrentlyRunning = false;
            }
        }, initialDelayInSeconds, delayInSeconds, TimeUnit.SECONDS); //update aircrafts every few seconds

        Timber.d("Service resumed updating map");
    }

    public void pauseUpdatingMap() {
        refreshingActive = false; //blocks intents to activity
        if (scheduledTaskExecutor != null) {
            scheduledTaskExecutor.shutdownNow();
            scheduledTaskExecutor = null;
        }

        timerCurrentlyRunning = false;

        Timber.d("Service paused updating map");
    }

    private Map<String, Aircraft> convertAircraftMap(Map<String, AircraftBundle> aircraftBundleMap) {
        Map<String, Aircraft> resultMap = new HashMap<>();
        for (String address : aircraftBundleMap.keySet()) {
            AircraftBundle bundle = aircraftBundleMap.get(address);
            AircraftBeacon beacon = bundle.aircraftBeacon;
            AircraftDescriptor descriptor = bundle.aircraftDescriptor;

            boolean isOgnPrivate = descriptor.isKnown() && (!descriptor.isTracked() || !descriptor.isIdentified());
            Aircraft aircraft = new Aircraft(beacon.getAddress(),
                    beacon.getAircraftType(),beacon.getClimbRate(), beacon.getLat(), beacon.getLon(), beacon.getAlt(), beacon.getGroundSpeed(),
                    descriptor.getRegNumber(), descriptor.getCN(), descriptor.getModel(), isOgnPrivate, beacon.getReceiverName(), beacon.getTrack());
            aircraft.setLastSeen(new Date(beacon.getTimestamp()));
            resultMap.put(address, aircraft);
        }

        return resultMap;
    }

    public class LocalBinder extends Binder {
        public OgnService getService() {
            return OgnService.this;
        }
    }
}
