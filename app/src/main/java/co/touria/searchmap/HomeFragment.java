package co.touria.searchmap;

import android.app.Fragment;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Created by elfassimounir on 1/11/16.
 */
public class HomeFragment extends Fragment implements AdapterView.OnItemClickListener, GoogleApiClient.OnConnectionFailedListener, LocationListener,
        GoogleApiClient.ConnectionCallbacks {

        private static View view;
        private GoogleMap map;
        private AutoCompleteTextView addressView;
        private GoogleApiClient mGoogleApiClient;
        private boolean manuallyChosenPlace;
        private LatLng center;
        private Geocoder geocoder;
        private List<Address> addresses;

        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

                if (view != null) {
                        ViewGroup parent = (ViewGroup) view.getParent();
                        if (parent != null)
                                parent.removeView(view);
                }
                try {
                        view = inflater.inflate(R.layout.fragment_home, container, false);
                } catch (InflateException e) {
        /* map is already there, just return view as it is */
                }

                return view;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {

                super.onViewCreated(view, savedInstanceState);

                mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                        //.enableAutoManage(this, 0 /* clientId */, this)
                        .addApi(Places.GEO_DATA_API)
                        .addApi(Places.PLACE_DETECTION_API)
                        .addApi(LocationServices.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();

                addressView = (AutoCompleteTextView) view.findViewById(R.id.address);
                addressView.setAdapter(new PlaceAutocompleteAdapter(getActivity(), mGoogleApiClient, null, null));

                map = ((MapFragment) getChildFragmentManager().findFragmentById(R.id.map)).getMap();

                map.setMyLocationEnabled(true);

                if(mGoogleApiClient == null){
                        mGoogleApiClient = new GoogleApiClient.Builder(getActivity()).addApi(LocationServices.API).addConnectionCallbacks(this)
                                .addOnConnectionFailedListener(this).build();
                        mGoogleApiClient.connect();
                }

                bindAutoComplete();

                bindCurrentLocationIcon(view);

        }

        @Override
        public void onStart() {
                super.onStart();
                mGoogleApiClient.connect();
        }

        @Override
        public void onStop() {
                mGoogleApiClient.disconnect();
                super.onStop();
        }

        private void bindAutoComplete() {

                setAutoSuggestAdapter();
                addressView.setOnItemClickListener(this);
                addressView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                addressView.setText("");
                        }
                });
        }

        private void bindCurrentLocationIcon(View view) {
                ImageView currentLocationIcon = (ImageView)view.findViewById(R.id.current_location_icon);
                currentLocationIcon.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                setCurrentLocation();
                        }
                });
        }

        private void setCurrentLocation() {

                Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

                if (location != null) {
                        LatLng latLong = new LatLng(location
                                .getLatitude(), location
                                .getLongitude());
                        moveToThisLocation(latLong);
                }
        }

        private void moveToThisLocation(LatLng latLng){
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(latLng).zoom(16f).build();
                map.animateCamera(CameraUpdateFactory
                        .newCameraPosition(cameraPosition));
                map.clear();

        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                manuallyChosenPlace = true;
                final AutocompletePrediction item = ((PlaceAutocompleteAdapter) parent.getAdapter()).getItem(position);
                final String placeId = item.getPlaceId();
                final CharSequence primaryText = item.getPrimaryText(null);
                Log.i("TAG", "Autocomplete item selected: " + primaryText);
                PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById(mGoogleApiClient, placeId);
                placeResult.setResultCallback(mUpdatePlaceDetailsCallback);
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
                Log.e("homefragment", "onConnectionFailed: ConnectionResult.getErrorCode() = "
                        + connectionResult.getErrorCode());

                // TODO(Developer): Check error code and notify the user of error state and resolution.
                Toast.makeText(getActivity(), "Could not connect to Google API Client: Error " + connectionResult.getErrorCode(),
                        Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onLocationChanged(Location location) {

        }

        @Override
        public void onConnected(Bundle bundle) {
                setCurrentLocation();
                bindMarker();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        private void hideKeyboard() {
                addressView.clearFocus();
                InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(addressView.getWindowToken(), 0);
        }

        private void bindMarker() {
                map.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {

                        @Override
                        public void onCameraChange(CameraPosition arg0) {
                                hideKeyboard();
                                if (manuallyChosenPlace) {
                                        manuallyChosenPlace = false;
                                        return;
                                }

                                center = map.getCameraPosition().target;
                                map.clear();

                                try {
                                        new GetLocationAsync(center.latitude, center.longitude).execute();
                                } catch (Exception e) {
                                }
                        }
                });
        }

        private void unsetAutoSuggestAdapter() {
                ArrayAdapter<String> adapter = null;
                addressView.setAdapter(adapter);
        }

        private void setAutoSuggestAdapter() {
                addressView.setAdapter(new PlaceAutocompleteAdapter(getActivity(), mGoogleApiClient, null, null));
        }

        private class GetLocationAsync extends AsyncTask<String, Void, String> {

                double latitude, longitude;
                StringBuilder locationAddress;

                public GetLocationAsync(double latitude, double longitude) {
                        this.latitude = latitude;
                        this.longitude = longitude;
                }

                @Override
                protected void onPreExecute() {
                        addressView.setText(" Getting location... ");
                }

                @Override
                protected String doInBackground(String... params) {

                        try {
                                geocoder = new Geocoder(getActivity().getApplicationContext(), Locale.ENGLISH);
                                addresses = geocoder.getFromLocation(latitude, longitude, 1);
                                locationAddress = new StringBuilder();
                                if (geocoder.isPresent()) {
                                        if(addresses.size() == 0){
                                                return null;
                                        }
                                        Address returnAddress = addresses.get(0);

                                        locationAddress.append(returnAddress.getAddressLine(0) + ", ");
                                        locationAddress.append(returnAddress.getLocality() + ", ");
                                        locationAddress.append(returnAddress.getCountryName());
                                } else {
                                }
                        } catch (IOException e) {
                                Log.e("tag", e.getMessage());
                        }
                        return null;
                }

                @Override
                protected void onPostExecute(String result) {
                        try {
                                unsetAutoSuggestAdapter();
                                addressView.setText(locationAddress);
                                setAutoSuggestAdapter();
                        } catch (Exception e) {
                                e.printStackTrace();
                        }
                }

                @Override
                protected void onProgressUpdate(Void... values) {

                }
        }

        private ResultCallback<PlaceBuffer> mUpdatePlaceDetailsCallback = new ResultCallback<PlaceBuffer>() {
                @Override
                public void onResult(PlaceBuffer places) {
                        if (!places.getStatus().isSuccess()) {
                                // Request did not complete successfully
                                Log.e("TAG", "Place query did not complete. Error: " + places.getStatus().toString());
                                places.release();
                                return;
                        }
                        // Get the Place object from the buffer.
                        final Place place = places.get(0);

                        moveToThisLocation(place.getLatLng());

                        new GetLocationAsync(center.latitude, center.longitude).execute();

                        places.release();
                }
        };

}