package com.joe.currentplace;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.Arrays;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private static final String TAG = "MapsActivity";

    ListView listPlaces;
    private PlacesClient placesClient;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location lastKnownLocation;

    private final LatLng defaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean locationPermissionGranted;

    private static final int MAX_ENTRIES = 5;
    private String[] likelyPlaceNames;
    private String[] likelyPlaceAddresses;
    private String[] likelyPlaceAttributions;
    private LatLng[] likelyPlaceLatLngs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listPlaces = findViewById(R.id.listPlaces);

        String apiKey = getString(R.string.google_maps_key);
        Places.initialize(getApplicationContext(), apiKey);
        placesClient = Places.createClient(this);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_geolocate) {
            pickCurrentPlace();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void getLocationPermission() {
        locationPermissionGranted = false;
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            locationPermissionGranted = true;
        else
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        // Enable the zoom controls for the map
        mMap.getUiSettings().setZoomControlsEnabled(true);

        // Prompt the user for permission
        getLocationPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        locationPermissionGranted = false;
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                locationPermissionGranted = true;
        }
    }

    private void getCurrentPlaceLikelihoods() {
        List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG);
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.builder(placeFields).build();
        @SuppressLint("MissingPermission")
        Task<FindCurrentPlaceResponse> placeResponse = placesClient.findCurrentPlace(request);
        placeResponse.addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FindCurrentPlaceResponse response = task.getResult();
                int count;
                if (response.getPlaceLikelihoods().size() < MAX_ENTRIES)
                    count = response.getPlaceLikelihoods().size();
                else
                    count = MAX_ENTRIES;

                int i = 0;
                likelyPlaceNames = new String[count];
                likelyPlaceAddresses = new String[count];
                likelyPlaceAttributions = new String[count];
                likelyPlaceLatLngs = new LatLng[count];

                for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                    Place currentPlace = placeLikelihood.getPlace();
                    likelyPlaceNames[i] = currentPlace.getName();
                    likelyPlaceAddresses[i] = currentPlace.getAddress();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        likelyPlaceAttributions[i] = (currentPlace.getAttributions() == null) ?
                                null : String.join(" ", currentPlace.getAttributions());
                    likelyPlaceLatLngs[i] = currentPlace.getLatLng();

                    String currentLatLng = (likelyPlaceLatLngs[i] == null) ? "" : likelyPlaceLatLngs[i].toString();

                    Log.i(TAG, "Place " + currentPlace.getName() + " has likelihood: " + placeLikelihood.getLikelihood()
                            + " at " + currentLatLng);

                    i++;

                    if (i > (count - 1))
                        break;
                }
            } else {
                Exception exception = task.getException();
                if (exception instanceof ApiException) {
                    ApiException apiException = (ApiException) exception;
                    Log.e(TAG, "Place not found: " + apiException.getStatusCode());
                }
            }
        });
    }

    private void getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        lastKnownLocation = task.getResult();
                        Log.d(TAG, "Latitude: " + lastKnownLocation.getLatitude());
                        Log.d(TAG, "Longitude: " + lastKnownLocation.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.");
                        Log.e(TAG, "Exception: %s", task.getException());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                    }

                    getCurrentPlaceLikelihoods();
                });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    private void pickCurrentPlace() {
        if (mMap == null)
            return;

        if (locationPermissionGranted)
            getDeviceLocation();
        else {
            Log.i(TAG, "The user did not grant location permission.");
            mMap.addMarker(new MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(defaultLocation)
                    .snippet(getString(R.string.default_info_snippet))
            );

            getLocationPermission();
        }
    }

}
