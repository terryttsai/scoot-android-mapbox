package com.terrytsai.scoot.scootnativemapbox;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationSource;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.UiSettings;
import com.mapbox.mapboxsdk.style.functions.Function;
import com.mapbox.mapboxsdk.style.functions.stops.Stop;
import com.mapbox.mapboxsdk.style.functions.stops.Stops;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.layers.FillLayer;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;
import com.mapbox.services.commons.geojson.Feature;
import com.mapbox.services.commons.geojson.FeatureCollection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.fillOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconSize;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener {

    private MapView mapView;
    private MapboxMap mapboxMap;
    private FloatingActionButton floatingActionButton;
    private LocationEngine locationEngine;
    private LocationEngineListener locationEngineListener;
    private PermissionsManager permissionsManager;
    private boolean firstPositionFound;
    private String vehiclesList;
    private String locationsList;
    private String streetParkingList;
    private FeatureCollection streetParkingDots;

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

    private class VehicleJsonTask extends AsyncTask<String, String, String> {

        protected void onPreExecute() {
            super.onPreExecute();

            super.onPreExecute();
            Toast.makeText(MainActivity.this, "Downloading Scoot Data...", Toast.LENGTH_SHORT).show();
        }

        protected String doInBackground(String... params) {


            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();


                InputStream stream = connection.getInputStream();

                reader = new BufferedReader(new InputStreamReader(stream));

                StringBuffer buffer = new StringBuffer();
                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer.append(line+"\n");
                    Log.d("Response: ", "> " + line);

                }

                return buffer.toString();


            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            vehiclesList = formatJSONForVehicles(result);
            setMapboxVehiclesSource();
        }
    }

    private String formatJSONForVehicles(String input) {
        String fragment = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[";
        String fragmentEnd = "]}},";
        String vehiclesString = "{\"type\":\"FeatureCollection\",\"features\":[";
        String vehiclesStringEnd = "]}";

        try {
            JSONObject data = new JSONObject(input);

            JSONArray scooters = data.getJSONArray("scooters");

            for (int i = 0; i < scooters.length(); i++) {
                JSONObject scoot = scooters.getJSONObject(i);
                vehiclesString += fragment + scoot.getString("longitude") + "," + scoot.getString("latitude") + fragmentEnd;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        vehiclesString = vehiclesString.replaceAll(",$", "");
        vehiclesString += vehiclesStringEnd;
        return vehiclesString;
    }

    private void setMapboxVehiclesSource() {
        try {
            mapboxMap.addSource(new GeoJsonSource("vehicles", vehiclesList));
        } catch (Error error) {
            System.out.println(error);
        }

        CircleLayer scootersLayer = new CircleLayer("vehicles", "vehicles");
        scootersLayer.setSourceLayer("vehicles");
        scootersLayer.setProperties(
                circleRadius(Function.zoom(Stops.exponential(
                        Stop.stop(12f, circleRadius(1.5f)),
                        Stop.stop(15f, circleRadius(6f))
                ))),
                circleColor(Color.argb(1, 244, 67, 54))
        );

        mapboxMap.addLayer(scootersLayer);
    }

    private class LocationJsonTask extends AsyncTask<String, String, String> {

        protected void onPreExecute() {
            super.onPreExecute();

            super.onPreExecute();
            Toast.makeText(MainActivity.this, "Downloading Location Data...", Toast.LENGTH_SHORT).show();
        }

        protected String doInBackground(String... params) {


            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();


                InputStream stream = connection.getInputStream();

                reader = new BufferedReader(new InputStreamReader(stream));

                StringBuffer buffer = new StringBuffer();
                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer.append(line+"\n");
                    Log.d("Response: ", "> " + line);

                }

                return buffer.toString();


            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            String[] locationsArray = formatJSONForLocations(result);
            locationsList = locationsArray[0];
            streetParkingList = locationsArray[1];
            setMapboxLocationsSource();
        }
    }

    private String[] formatJSONForLocations(String input) {
        String fragment = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[";
        String fragmentEnd = "]}},";
        String locationsString = "{\"type\":\"FeatureCollection\",\"features\":[";
        String locationsStringEnd = "]}";

        String streetParkingString = "{\"type\":\"FeatureCollection\",\"features\":[";
        String streetParkingStringEnd = "]}";

        List<Feature> streetParkingCoordinates = new ArrayList<>();

        try {
            JSONObject data = new JSONObject(input);

            JSONArray locations = data.getJSONArray("locations");

            for (int i = 0; i < locations.length(); i++) {
                JSONObject location = locations.getJSONObject(i);
                if (location.getInt("location_type_id") == 1) {
                    locationsString += fragment + location.getString("longitude") + "," + location.getString("latitude") + fragmentEnd;
                }
                if (location.getInt("location_type_id") == 4) {
                    String streetParkingObj = location.getString("geofence_geojson");
                    streetParkingString += streetParkingObj.substring(1, streetParkingObj.length() - 1) + ",";

                    String streetParkingDotsStr = location.getString("parking_geojson");
                    if (!streetParkingDotsStr.equals("")) {
                        JSONArray streetParkingDotsArray = new JSONArray(streetParkingDotsStr);
                        for (int j = 0; j < streetParkingDotsArray.length(); j++) {
                            Feature streetParkingDotsFeature = Feature.fromJson(streetParkingDotsArray.get(j).toString());
                            streetParkingCoordinates.add(streetParkingDotsFeature);
                        }
                    }
                }
            }

            streetParkingDots = FeatureCollection.fromFeatures(streetParkingCoordinates);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        locationsString = locationsString.replaceAll(",$", "");
        locationsString += locationsStringEnd;

        streetParkingString = streetParkingString.replaceAll(",$", "");
        streetParkingString += streetParkingStringEnd;

        String[] result = {locationsString, streetParkingString};
        return result;
    }


    private void setMapboxLocationsSource() {
        try {
            mapboxMap.addSource(new GeoJsonSource("locations-source", locationsList));
            mapboxMap.addSource(new GeoJsonSource("street-parking-source", streetParkingList));
            mapboxMap.addSource(new GeoJsonSource("street-parking-dots-source", streetParkingDots));
        } catch (Error error) {
            System.out.println(error);
        }

        FillLayer streetParkingLayer = new FillLayer("streetParking", "street-parking-source");

        streetParkingLayer.setProperties(
                fillColor(Color.parseColor("#008CFF")),
                fillOpacity(0.18f)
        );

        mapboxMap.addLayer(streetParkingLayer);

        CircleLayer streetParkingDotsLayer = new CircleLayer("street-parking-dots-layer", "street-parking-dots-source")
                .withProperties(
                        circleRadius(Function.zoom(Stops.exponential(
                                Stop.stop(12f, circleRadius(1.5f)),
                                Stop.stop(15f, circleRadius(6f))
                        ))),
                        circleColor(Color.argb(1, 55, 148, 179)),
                        circleOpacity(.3f)
                );

        mapboxMap.addLayer(streetParkingDotsLayer);

        SymbolLayer locationMarkersLayer = new SymbolLayer("locations-layer", "locations-source")
                .withProperties(
                        iconImage("my-marker-image"),
                        iconAllowOverlap(true),
                        iconSize(Function.zoom(Stops.exponential(
                                Stop.stop(12f, iconSize(0.5f)),
                                Stop.stop(15f, iconSize(1.5f))
                        )))
                );

        mapboxMap.addLayer(locationMarkersLayer);
    }

    @Override
    public void onMapReady(final MapboxMap mapboxMap) {

//                // Should be the bounds of the map, just need to get it implemented
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

        new VehicleJsonTask().execute("https://app.scoot.co/api/v1/scooters.json");
        new LocationJsonTask().execute("https://app.scoot.co/api/v3/locations.json");

        Bitmap icon = BitmapFactory.decodeResource(MainActivity.this.getResources(), R.drawable.blue_marker_view);

        // Add the marker image to map
        mapboxMap.addImage("my-marker-image", icon);
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
