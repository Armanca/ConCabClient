package ro.armanca.concabclient.ui.home;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import ro.armanca.concabclient.Callback.IFFirebaseDriverInfoListener;
import ro.armanca.concabclient.Callback.IFFirebaseFailedListener;
import ro.armanca.concabclient.Common.Common;
import ro.armanca.concabclient.Model.AnimationModel;
import ro.armanca.concabclient.Model.DriverGeoModel;
import ro.armanca.concabclient.Model.DriverInfoModel;
import ro.armanca.concabclient.EventBus.SelectPlaceEvent;
import ro.armanca.concabclient.Model.GeoQueryModel;
import ro.armanca.concabclient.R;
import ro.armanca.concabclient.Remote.IGoogleAPI;
import ro.armanca.concabclient.Remote.RetrofitClient;
import ro.armanca.concabclient.RequestDriverActivity;

public class HomeFragment extends Fragment implements OnMapReadyCallback, IFFirebaseFailedListener, IFFirebaseDriverInfoListener {

    @BindView(R.id.activity_main)
    SlidingUpPanelLayout slidingUpPanelLayout;
    @BindView(R.id.txt_welcome)
    TextView txt_welcome;

    private AutocompleteSupportFragment autocompleteSupportFragment;

    private HomeViewModel homeViewModel;
    private GoogleMap mMap;
    private SupportMapFragment mapFragment;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    //Soferii online
    private double distance = 1.0;
    private static final double LIMIT_RANGE = 8.0; //kilometrii distanta
    private Location previousLocation, currentLocation;  // pt a calcula distanta

    private boolean firstTime = true;
    private boolean isNextLaunch = false;

    //Listener
    IFFirebaseDriverInfoListener ifFirebaseDriverInfoListener;
    IFFirebaseFailedListener ifFirebaseFailedListener;
    private String cityName;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private IGoogleAPI iGoogleAPI;


    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onStop() {
        compositeDisposable.clear();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        super.onDestroy();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if(isNextLaunch)
        {
            loadAvailableDrivers();
        }
        else
            isNextLaunch = true;
    }


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);


        mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        initViews(root);
        init();

        return root;
    }

    private void initViews(View root) {
        ButterKnife.bind(this, root);

        Common.setVWelcomeMessage(txt_welcome);
    }

    private void init() {

        Places.initialize(getContext(), getString(R.string.google_maps_key));
        autocompleteSupportFragment = (AutocompleteSupportFragment) getChildFragmentManager()
                .findFragmentById(R.id.autocomplete_fragment);
        autocompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.ADDRESS, Place.Field.NAME, Place.Field.LAT_LNG));
        autocompleteSupportFragment.setHint(getString(R.string.where_to));
        autocompleteSupportFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                //Snackbar.make(getView(),""+place.getLatLng(),Snackbar.LENGTH_LONG).show();
                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Snackbar.make(getView(),getString(R.string.permission_require),Snackbar.LENGTH_LONG).show();
                    return;
                }
                fusedLocationProviderClient.getLastLocation()
                        .addOnSuccessListener(location -> {

                            LatLng origin = new LatLng(location.getLatitude(),location.getLongitude());
                            LatLng destination = new LatLng(place.getLatLng().latitude,place.getLatLng().longitude);

                            startActivity(new Intent(getContext(), RequestDriverActivity.class));
                            EventBus.getDefault().postSticky(new SelectPlaceEvent(origin,destination,place.getAddress()));

                        });

            }

            @Override
            public void onError(@NonNull Status status) {
                Snackbar.make(getView(),""+status.getStatusMessage(),Snackbar.LENGTH_LONG).show();
            }
        });



        iGoogleAPI = RetrofitClient.getInstance().create(IGoogleAPI.class);

        ifFirebaseFailedListener = this;
        ifFirebaseDriverInfoListener = this;


        if(ActivityCompat.checkSelfPermission(getContext(),Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            Snackbar.make(mapFragment.getView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show();

            return;
        }

        buildLocationRequest();
        buildLocationCallback();
        updateLocation();


        loadAvailableDrivers();
    }

    private void updateLocation() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getContext());
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

    }

    private void buildLocationCallback() {
        if(locationCallback == null)
        {
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    LatLng newPosition = new LatLng(locationResult.getLastLocation().getLatitude(),
                            locationResult.getLastLocation().getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPosition, 18f));

                    //recalculare distanta si incarcare soferi
                    if (firstTime) {
                        previousLocation = currentLocation = locationResult.getLastLocation();
                        firstTime = false;

                        setRestrictPlacesInCountry( locationResult.getLastLocation());
                    } else {
                        previousLocation = currentLocation;
                        currentLocation = locationResult.getLastLocation();

                    }

                    if (previousLocation.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE)
                        loadAvailableDrivers();
                    else {
                        //nimic..lasam harta
                    }

                }
            };
        }
    }

    private void buildLocationRequest() {
    if(locationRequest == null)
        {
        locationRequest = new LocationRequest();
        locationRequest.setSmallestDisplacement(10f);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }
    }

    private void setRestrictPlacesInCountry(Location location) {
        try{
            Geocoder geocoder = new Geocoder(getContext(),Locale.getDefault());
            List<Address> addressesList = geocoder.getFromLocation(location.getLatitude(),location.getLongitude(),1);
            if(addressesList.size() >0 )
            autocompleteSupportFragment.setCountry(addressesList.get(0).getCountryCode());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private void loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(getView(), getString(R.string.permission_require), Snackbar.LENGTH_SHORT).show();
            return;
        }
        fusedLocationProviderClient.getLastLocation()
                .addOnFailureListener(e -> Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show())
                .addOnSuccessListener(location -> {
                    //Initializare toti soferii din oras
                    Geocoder geocoder = new Geocoder(getContext(), Locale.getDefault());
                    List<Address> addressList;
                    try {
                        addressList = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                       if(addressList.size() >0 )
                             cityName = addressList.get(0).getLocality();
                       if(!TextUtils.isEmpty(cityName)) {
                           //query
                           DatabaseReference driver_location_ref = FirebaseDatabase.getInstance()
                                   .getReference(Common.DRIVER_LOCATION_REFERENCE)
                                   .child(cityName);
                           GeoFire geoF = new GeoFire(driver_location_ref);
                           GeoQuery geoQuery = geoF.queryAtLocation(new GeoLocation(location.getLatitude(),
                                   location.getLongitude()), distance);
                           geoQuery.removeAllListeners();

                           geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                               @Override
                               public void onKeyEntered(String key, GeoLocation location) {
                                   //Common.driversFound.add(new DriverGeoModel(key, location));
                                   if(!Common.driversFound.containsKey(key))
                                       Common.driversFound.put(key,new DriverGeoModel(key,location));
                               }

                               @Override
                               public void onKeyExited(String key) {

                               }

                               @Override
                               public void onKeyMoved(String key, GeoLocation location) {

                               }

                               @Override
                               public void onGeoQueryReady() {
                                   if (distance <= LIMIT_RANGE) {
                                       distance++;
                                       loadAvailableDrivers(); //cautam in continuare
                                   } else {
                                       distance = 1.0;//initializare de la inceput
                                       addDriverMarker();
                                   }
                               }

                               @Override
                               public void onGeoQueryError(DatabaseError error) {
                                   Snackbar.make(getView(), error.getMessage(), Snackbar.LENGTH_SHORT).show();
                               }
                           });

                           driver_location_ref.addChildEventListener(new ChildEventListener() {
                               @Override
                               public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                                   GeoQueryModel geoQueryModel = snapshot.getValue(GeoQueryModel.class);
                                   GeoLocation geoLocation = new GeoLocation(geoQueryModel.getL().get(0),
                                           geoQueryModel.getL().get(1));
                                   DriverGeoModel driverGeoModel = new DriverGeoModel(snapshot.getKey(), geoLocation);
                                   Location newDriverLocation = new Location("");
                                   newDriverLocation.setLatitude(geoLocation.latitude);
                                   newDriverLocation.setLongitude(geoLocation.longitude);
                                   float distance = location.distanceTo(newDriverLocation) / 1000;
                                   if (distance <= LIMIT_RANGE)
                                       findDriverByKey(driverGeoModel);

                               }

                               @Override
                               public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                               }

                               @Override
                               public void onChildRemoved(@NonNull DataSnapshot snapshot) {

                               }

                               @Override
                               public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

                               }

                               @Override
                               public void onCancelled(@NonNull DatabaseError error) {

                               }
                           });
                       }
                       else
                           Snackbar.make(getView(),getString(R.string.city_name_empty),Snackbar.LENGTH_LONG).show();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show();
                    }
                });
    }

    private void addDriverMarker() {
        if (Common.driversFound.size() > 0) {
            Observable.fromIterable(Common.driversFound.keySet())
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(key -> {
                                findDriverByKey(Common.driversFound.get(key));
                            }, throwable -> {
                                Snackbar.make(getView(), throwable.getMessage(), Snackbar.LENGTH_SHORT).show();
                            }, () -> {
                            }
                    );

        } else {
            Snackbar.make(getView(), getString(R.string.drivers_not_found), Snackbar.LENGTH_SHORT).show();
        }
    }

    private void findDriverByKey(DriverGeoModel driverGeoModel) {
        FirebaseDatabase.getInstance()
                .getReference(Common.DRIVER_INFO_REFERENCE)
                .child(driverGeoModel.getKey())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.hasChildren()) {
                            driverGeoModel.setDriverInfoModel(snapshot.getValue(DriverInfoModel.class));
                            Common.driversFound.get(driverGeoModel.getKey()).setDriverInfoModel(snapshot.getValue(DriverInfoModel.class));
                            ifFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel);
                        } else {
                            ifFirebaseFailedListener.onFirebaseLoadFailed(getString(R.string.not_key_drivers) + driverGeoModel.getKey());

                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        ifFirebaseFailedListener.onFirebaseLoadFailed(error.getMessage());
                    }
                });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;


        Dexter.withContext(getContext())
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                           Snackbar.make(mapFragment.getView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show();
                            return;
                        }
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setMyLocationButtonEnabled(true);
                        mMap.setOnMyLocationButtonClickListener(() -> {
                            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                    && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                                return false;
                            }
                            fusedLocationProviderClient.getLastLocation()
                                    .addOnFailureListener(e -> Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT)
                                            .show())
                                    .addOnSuccessListener(location -> {
                                        LatLng userLatLang = new LatLng(location.getLatitude(), location.getLongitude());
                                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLang, 18f));
                                    });
                            return true;
                        });


                        View locationButton = ((View) mapFragment.getView().findViewById(Integer.parseInt("1")).getParent())
                                .findViewById(Integer.parseInt("2"));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
                        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                        params.setMargins(0, 0, 0, 250);

                        buildLocationRequest();
                        buildLocationCallback();
                        updateLocation();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        Snackbar.make(getView(), permissionDeniedResponse.getPermissionName() + " need enable ", Snackbar.LENGTH_SHORT).show();

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {

                    }
                })
                .check();


        mMap.getUiSettings().setZoomControlsEnabled(true);

        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(),
                    R.raw.concab_maps_style));
            if (!success)
                Snackbar.make(getView(), "Incarcarea hartii a esuat...", Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFirebaseLoadFailed(String message) {
        Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();

    }

    @Override
    public void onDriverInfoLoadSuccess(DriverGeoModel driverGeoModel) {
        if (!Common.markerList.containsKey(driverGeoModel.getKey()))
            Common.markerList.put(driverGeoModel.getKey(),
                    mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(driverGeoModel.getGeoLocation().latitude,
                            driverGeoModel.getGeoLocation().longitude))
                    .flat(true)
                    .title(Common.buildName(driverGeoModel.getDriverInfoModel().getFirstName(),
                            driverGeoModel.getDriverInfoModel().getLastName()))
                    .snippet(driverGeoModel.getDriverInfoModel().getPhoneNumber())
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_car_traseu))));

        if (!TextUtils.isEmpty(cityName))
        {
            DatabaseReference driverLocation = FirebaseDatabase.getInstance()
                    .getReference(Common.DRIVER_LOCATION_REFERENCE)
                    .child(cityName)
                    .child(driverGeoModel.getKey());
            driverLocation.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.hasChildren())
                    {
                        if (Common.markerList.get(driverGeoModel.getKey()) != null)
                            Common.markerList.get(driverGeoModel.getKey()).remove();
                        Common.markerList.remove(driverGeoModel.getKey());
                        Common.driverLocationSubscribe.remove(driverGeoModel.getKey());
                        if(Common.driversFound != null && Common.driversFound.size() > 0 )
                            Common.driversFound.remove(driverGeoModel.getKey());
                            driverLocation.removeEventListener(this);

                    }
                    else
                    {
                        if (Common.markerList.get(driverGeoModel.getKey()) != null)
                        {
                            GeoQueryModel geoQueryModel = snapshot.getValue(GeoQueryModel.class);
                            AnimationModel animationModel = new AnimationModel(false, geoQueryModel);

                            if (Common.driverLocationSubscribe.get(driverGeoModel.getKey()) != null)
                            {
                                Marker currentMarker = Common.markerList.get(driverGeoModel.getKey());
                                AnimationModel oldPosition = Common.driverLocationSubscribe.get(driverGeoModel.getKey());

                                String from = new StringBuilder()
                                        .append(oldPosition.getGeoQueryModel().getL().get(0))
                                        .append(",")
                                        .append(oldPosition.getGeoQueryModel().getL().get(1))
                                        .toString();

                                String to = new StringBuilder()
                                        .append(animationModel.getGeoQueryModel().getL().get(0))
                                        .append(",")
                                        .append(animationModel.getGeoQueryModel().getL().get(1))
                                        .toString();

                                moveMarkerAnimation(driverGeoModel.getKey(), animationModel, currentMarker, from, to);
                            }
                            else
                            {
                                Common.driverLocationSubscribe.put(driverGeoModel.getKey(), animationModel);
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Snackbar.make(getView(), error.getMessage(), Snackbar.LENGTH_SHORT).show();
                }
            });
        }
    }



    private void moveMarkerAnimation(String key, AnimationModel animationModel, Marker currentMarker, String from, String to) {
        if(!animationModel.isRun())
        {
            //cerere de API
            compositeDisposable.add(iGoogleAPI.getDirections("driving",
                    "less_driving",
                    from,to,
                    getActivity().getString(R.string.google_api_key))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(returnResult -> {
                Log.d("API_RETURN", returnResult);
                try {
                    //conectare JSON
                    JSONObject jsonObject = new JSONObject(returnResult);
                    JSONArray jsonArray = jsonObject.getJSONArray("routes");
                    for(int i=0;i<jsonArray.length();i++){
                        JSONObject route = jsonArray.getJSONObject(i);
                        JSONObject poly = route.getJSONObject("overview_polyline");
                        String polyline = poly.getString("points");
                      //  polylineList = Common.decodePoly(polyline);
                        animationModel.setPolylineList(Common.decodePoly(polyline));
                    }



               //     index = -1;
                //    next = 1;
                    animationModel.setIndex(1);
                    animationModel.setNext(1);


                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                        if(animationModel.getPolylineList()!= null && animationModel.getPolylineList().size() > 1 )
                        {
                            if(animationModel.getIndex() < animationModel.getPolylineList().size() - 2){
                               // index++;
                                animationModel.setIndex(animationModel.getNext()+1);
                               // next=index+1;
                                animationModel.setNext(animationModel.getIndex()+1);
                                //start = polylineList.get(index);
                                animationModel.setStart(animationModel.getPolylineList().get(animationModel.getIndex()));
                               // end = polylineList.get(next);
                                animationModel.setEnd(animationModel.getPolylineList().get(animationModel.getNext()));
                            }

                            ValueAnimator valueAnimator = ValueAnimator.ofInt(0,1);
                            valueAnimator.setDuration(3000);
                            valueAnimator.setInterpolator(new LinearInterpolator());
                            valueAnimator.addUpdateListener(animation -> {
                              //  v = animation.getAnimatedFraction();
                                animationModel.setV(animation.getAnimatedFraction());
                               // lat=v*end.latitude + (1-v)*start.latitude;
                                animationModel.setLat(animationModel.getV() * animationModel.getEnd().latitude + (1- animationModel.getV())
                                * animationModel.getStart().latitude);
                               // lng=v*end.longitude + (1-v)*start.longitude;
                                animationModel.setLng(animationModel.getV() * animationModel.getEnd().longitude + (1- animationModel.getV())
                                        * animationModel.getStart().longitude);
                                LatLng newPos= new LatLng(animationModel.getLat(),animationModel.getLng());
                                currentMarker.setPosition(newPos);
                                currentMarker.setAnchor(0.5f,0.5f);
                                currentMarker.setRotation(Common.getBearing(animationModel.getStart(),newPos));

                            });

                            valueAnimator.start();
                            if(animationModel.getIndex() < animationModel.getPolylineList().size() - 2)
                                animationModel.getHandler().postDelayed(this,1500);
                            else if (animationModel.getIndex() < animationModel.getPolylineList().size() -1 )
                            {
                                animationModel.setRun(false);
                                Common.driverLocationSubscribe.put(key,animationModel); //se face update
                             }
                           }
                        }
                    };

                    animationModel.getHandler().postDelayed(runnable,1500);

                }
                catch (Exception e){
                    Snackbar.make(getView(),e.getMessage(),Snackbar.LENGTH_LONG).show();
                }
            })
            );
        }
    }
}