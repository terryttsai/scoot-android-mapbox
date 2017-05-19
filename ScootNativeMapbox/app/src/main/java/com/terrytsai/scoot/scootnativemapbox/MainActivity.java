package com.terrytsai.scoot.scootnativemapbox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerViewOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationSource;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.UiSettings;
import com.mapbox.mapboxsdk.style.layers.FillLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;

import java.util.List;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillOpacity;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener {

    private MapView mapView;
    private MapboxMap mapboxMap;
    private FloatingActionButton floatingActionButton;
    private LocationEngine locationEngine;
    private LocationEngineListener locationEngineListener;
    private PermissionsManager permissionsManager;
    private boolean firstPositionFound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.access_token));
        setContentView(R.layout.activity_main);

        // Create a mapView
        mapView = (MapView) findViewById(R.id.mapview);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        firstPositionFound = false;

        locationEngine = LocationSource.getLocationEngine(this);
        locationEngine.activate();
    }

    @Override
    public void onMapReady(final MapboxMap mapboxMap) {
        final MarkerViewOptions markerViewOptions = new MarkerViewOptions()
                .position(new LatLng(37.774929, -122.419416))
                .title("Hi there!")
                .snippet("I am a snippet");

//                // Should be the bounds of the map, if only I can get this implemented
////                LatLngBounds latLngBounds = new LatLngBounds.Builder()
////                    .include(new LatLng(36.532128, -93.489121)) // Northeast
////                    .include(new LatLng(25.837058, -106.646234)) // Southwest
////                    .build();

        this.mapboxMap = mapboxMap;

        UiSettings uisettings = mapboxMap.getUiSettings();
        uisettings.setRotateGesturesEnabled(false);
        uisettings.setTiltGesturesEnabled(false);

        mapboxMap.getMyLocationViewSettings().setForegroundTintColor(ContextCompat.getColor(this, R.color.colorLocationDot));

        mapboxMap.setOnMarkerClickListener(new MapboxMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(@NonNull Marker marker) {
                mapboxMap.easeCamera(CameraUpdateFactory.newLatLng(new LatLng(marker.getPosition())), 300);
                return true;
            }
        });

        mapboxMap.addMarker(markerViewOptions);

        floatingActionButton = (FloatingActionButton) findViewById(R.id.location_toggle_fab);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mapboxMap != null) {
                    enableLocation(true);
                }
            }
        });

        permissionsManager = new PermissionsManager(this);
        if (!PermissionsManager.areLocationPermissionsGranted(this)) {
            permissionsManager.requestLocationPermissions(this);
        } else {
            enableLocation(true);
        }

        try {
            String innerSunsetGeofenceListString = "[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[-122.466659545898,37.7620638129224],[-122.46687412262,37.7657787461117],[-122.476830482483,37.7653377135766],[-122.476519346237,37.7612834836084],[-122.469996213913,37.7615888308421],[-122.470135688782,37.7638179334808],[-122.468975633383,37.7638672327322],[-122.468869686127,37.7620129221311],[-122.466659545898,37.7620638129224]]]}}]";
            GeoJsonSource innerSunsetStreetParking = new GeoJsonSource("south-mission-street-parking", "{\"type\":\"FeatureCollection\",\"features\":" +
                    innerSunsetGeofenceListString  + "}");

            mapboxMap.addSource(innerSunsetStreetParking);

            FillLayer innerSunsetArea = new FillLayer("south-mission-street-parking-fill", "south-mission-street-parking");

            innerSunsetArea.setProperties(
                    fillColor(Color.parseColor("#008CFF")),
                    fillOpacity(0.08f)
            );

            mapboxMap.addLayer(innerSunsetArea);
        } catch (Error error) {
            System.out.println(error);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, "This app needs location permissions to function", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocation(true);
        } else {
            Toast.makeText(this, "You didn't grant location permissions.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void enableLocation(boolean enabled) {
        if (enabled) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Toast.makeText(this, "LOCATION PERMISSION NOT GRANTED!!!", Toast.LENGTH_SHORT).show();
                return;
            }
            Location lastLocation = locationEngine.getLastLocation();
            if (lastLocation != null) {
                mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation), 16), 2000);
            }

            locationEngineListener = new LocationEngineListener() {
                @Override
                public void onConnected() {
                    // no need to do anything here
                }

                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        if (!firstPositionFound) {
                            mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location), 16), 3800);
                            firstPositionFound = true;
                        }
//                        locationEngine.removeLocationEngineListener(this);
                    }
                }
            };
            locationEngine.addLocationEngineListener(locationEngineListener);
        }
        mapboxMap.setMyLocationEnabled(enabled);
    }
}
