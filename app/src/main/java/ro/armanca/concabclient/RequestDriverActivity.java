package ro.armanca.concabclient;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.SquareCap;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.ui.IconGenerator;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import ro.armanca.concabclient.Common.Common;
import ro.armanca.concabclient.Model.DriverGeoModel;
import ro.armanca.concabclient.Model.EventBus.DeclineAndRemoveRequestFromDriver;
import ro.armanca.concabclient.Model.EventBus.DeclineRequestFromDriver;
import ro.armanca.concabclient.Model.EventBus.DriverAcceptTripEvent;
import ro.armanca.concabclient.Model.EventBus.SelectPlaceEvent;
import ro.armanca.concabclient.Model.TripPlanModel;
import ro.armanca.concabclient.Remote.IGoogleAPI;
import ro.armanca.concabclient.Remote.RetrofitClient;
import ro.armanca.concabclient.Utils.UserUtils;

public class RequestDriverActivity extends FragmentActivity implements OnMapReadyCallback {

    //View
    @BindView(R.id.finding_your_ride_layout)
    CardView finding_your_ride_layout;
    @BindView(R.id.confirm_concab_layout)
    CardView confirm_concab_layout;
    @BindView(R.id.btn_confirm_concab)
    Button btn_confirm_concab;
    @BindView(R.id.confirm_pickup_layout)
    CardView confirm_pickup_layout;
    @BindView(R.id.btn_confirm_pickup)
    Button btn_confirm_pickup;
    @BindView(R.id.txt_address_pickup)
    TextView txt_address_pickup;
    @BindView(R.id.driver_info_layout)
    CardView driver_info_layout;
    @BindView(R.id.txt_driver_name)
    TextView txt_driver_name;
    @BindView(R.id.img_driver)
    ImageView img_driver;
    @BindView(R.id.txt_rating)
    TextView txt_rating;


    @BindView(R.id.fill_maps)
    View fill_maps;

    @BindView(R.id.main_layout)
    RelativeLayout main_layout;

    private Circle lastUserCircle;
    private long duration = 1000;
    private ValueAnimator lastPulseAnimator;

    //Invartire camera
    private ValueAnimator animator;
    private static final int DESIRED_NUM_OF_SPINS=5;
    private static final int DESIRED_SECONDS_PER_ONE_FULL_360_SPIN=40;
    private DriverGeoModel lastDriverCall;
    private String driverOldPosition="";
    private Handler handler;
    private float v;
    private double lat,lng;
    private int index,next;
    private LatLng start,end;



    @OnClick(R.id.btn_confirm_concab)
    void onConfirmConcab(){
        confirm_pickup_layout.setVisibility(View.VISIBLE);
        confirm_concab_layout.setVisibility(View.GONE);

        setDataPickup();
    }

    @OnClick(R.id.btn_confirm_pickup)
    void onConfirmPickup(){
        if(mMap == null) return;
        if(selectPlaceEvent == null) return;

        //golire harta
        mMap.clear();
        //Tilt
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(selectPlaceEvent.getOrigin())
                .tilt(45f)
                .zoom(16f)
                .build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        //start animare
        addMarkerWithPulseAnimation();


    }

    private void addMarkerWithPulseAnimation() {
        confirm_pickup_layout.setVisibility(View.GONE);
        fill_maps.setVisibility(View.VISIBLE);
        finding_your_ride_layout.setVisibility(View.VISIBLE);

        originMarker = mMap.addMarker(new MarkerOptions()
            .icon(BitmapDescriptorFactory.defaultMarker())
            .position(selectPlaceEvent.getOrigin()));

        addPulsatingEffect(selectPlaceEvent);

    }


    private void addPulsatingEffect(SelectPlaceEvent selectPlaceEvent) {

        if(lastPulseAnimator != null ) lastPulseAnimator.cancel();
        if(lastUserCircle != null) lastUserCircle.setCenter(selectPlaceEvent.getOrigin());

        lastPulseAnimator = Common.valueAnimate(duration,animation ->{
            if(lastPulseAnimator != null) lastUserCircle.setRadius((Float)animation.getAnimatedValue());
            else
            {
                lastUserCircle = mMap.addCircle(new CircleOptions()
                .center(selectPlaceEvent.getOrigin())
                .radius((Float)animation.getAnimatedValue())
                .strokeColor(Color.BLACK)
                        .fillColor(Color.parseColor("#A4FDF878"))
                );

            }

        });

        startMapCameraSpinningAnimation(selectPlaceEvent);

    }

    private void startMapCameraSpinningAnimation(SelectPlaceEvent selectPlaceEvent) {
        if(animator != null) animator.cancel();
        animator=ValueAnimator.ofFloat(0,DESIRED_NUM_OF_SPINS*360);
        animator.setDuration(DESIRED_SECONDS_PER_ONE_FULL_360_SPIN*DESIRED_NUM_OF_SPINS*1000);
        animator.setInterpolator(new LinearInterpolator());
        animator.setStartDelay(100);
        animator.addUpdateListener(animation -> {
            Float newBearingValue = (Float) animation.getAnimatedValue();
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                .target(selectPlaceEvent.getOrigin())
                .zoom(16f)
                .tilt(45f)
                .bearing(newBearingValue)
                .build()));
        });
        animator.start();

        //dupa animare, gasim un sofer;
        findNearbyDriver(selectPlaceEvent);

    }

    private void findNearbyDriver(SelectPlaceEvent selectPlaceEvent) {
        if(Common.driversFound.size()>0){

            float min_distance = 0 ;
            DriverGeoModel foundDriver =null;
            Location currentClientLocation = new Location("");
            currentClientLocation.setLatitude(selectPlaceEvent.getOrigin().latitude);
            currentClientLocation.setLongitude(selectPlaceEvent.getOrigin().longitude);
            for(String key:Common.driversFound.keySet())
            {
                Location driverLocation = new Location("");
                driverLocation.setLatitude(Common.driversFound.get(key).getGeoLocation().latitude);
                driverLocation.setLongitude(Common.driversFound.get(key).getGeoLocation().longitude);

                //Comparam 2 locatii
                if(min_distance == 0){
                    min_distance = driverLocation.distanceTo(currentClientLocation);

                    if(!Common.driversFound.get(key).isDecline())
                    {
                        foundDriver = Common.driversFound.get(key);
                        break;
                    }
                    else
                        continue;
                }
                else
                    if(driverLocation.distanceTo(currentClientLocation) < min_distance)
                    {
                        //daca gasim alt sofer mai aproape de client
                        min_distance = driverLocation.distanceTo(currentClientLocation);

                        if(!Common.driversFound.get(key).isDecline())
                        {
                            foundDriver = Common.driversFound.get(key);
                            break;
                        }
                        else
                            continue;
                    }


            }

            if(foundDriver != null){
                UserUtils.sendRequestDriver(this,main_layout,foundDriver,selectPlaceEvent);
                lastDriverCall = foundDriver;
            }
            else
            {
                Toast.makeText(this, getString(R.string.no_driver_accept_request), Toast.LENGTH_SHORT).show();
                lastDriverCall = null;
                finish();
            }
        }
        else
        {
            // nu avem soferi
            Snackbar.make(main_layout,getString(R.string.drivers_not_found),Snackbar.LENGTH_LONG).show();
            lastDriverCall = null;
            finish();

        }
    }

    @Override
    protected void onDestroy() {
        if(animator != null) animator.end();
        super.onDestroy();
    }

    private GoogleMap mMap;

    private SelectPlaceEvent selectPlaceEvent;

    //Trasee
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private IGoogleAPI iGoogleAPI;
    private Polyline yellowPolyLine, blackPolyLine;
    private PolylineOptions polylineOptions, yellowPolylineOptions;
    private List<LatLng> polylineList;
    TextView txt_origin;

    private Marker originMarker, destinationMarker;

    private void setDataPickup() {
        txt_address_pickup.setText(txt_origin != null ? txt_origin.getText() : "Gol");
        mMap.clear();;
        //adauga pickup marker

        addPickupMarker();
    }

    private void addPickupMarker() {
        View view = getLayoutInflater().inflate(R.layout.pickup_info_window,null);

        //Icon pentru marker
        IconGenerator generator = new IconGenerator(this);
        generator.setContentView(view);
        generator.setBackground(new ColorDrawable(Color.TRANSPARENT));
        Bitmap icon = generator.makeIcon();

        originMarker = mMap.addMarker(new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectPlaceEvent.getOrigin()));
    }


    @Override
    protected void onStart() {
        super.onStart();
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);

    }

    @Override
    protected void onStop(){
        compositeDisposable.clear();
        super.onStop();
        if(EventBus.getDefault().hasSubscriberForEvent(SelectPlaceEvent.class))
            EventBus.getDefault().removeStickyEvent(SelectPlaceEvent.class);
        if(EventBus.getDefault().hasSubscriberForEvent(DeclineRequestFromDriver.class))
            EventBus.getDefault().removeStickyEvent(DeclineRequestFromDriver.class);
        if(EventBus.getDefault().hasSubscriberForEvent(DriverAcceptTripEvent.class))
            EventBus.getDefault().removeStickyEvent(DriverAcceptTripEvent.class);
        if(EventBus.getDefault().hasSubscriberForEvent(DeclineAndRemoveRequestFromDriver.class))
            EventBus.getDefault().removeStickyEvent(DeclineAndRemoveRequestFromDriver.class);
        EventBus.getDefault().unregister(this);

    }

    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    public void onDriverAcceptEvent(DriverAcceptTripEvent event){

        FirebaseDatabase.getInstance().getReference(Common.TRIP)
                .child(event.getTripIp())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(snapshot.exists())
                        {
                            TripPlanModel   tripPlanModel = snapshot.getValue(TripPlanModel.class);
                            mMap.clear();
                            fill_maps.setVisibility(View.GONE);
                            if(animator != null) animator.end();
                            CameraPosition cameraPosition = new CameraPosition.Builder()
                                    .target(mMap.getCameraPosition().target)
                                    .tilt(0f)
                                    .zoom(mMap.getCameraPosition().zoom)
                                    .build();
                            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                            //alegerea rutelor
                            String driverLocation = new StringBuilder()
                                    .append(tripPlanModel.getCurrentLat())
                                    .append(",")
                                    .append(tripPlanModel.getCurrentLng())
                                    .toString();

                            compositeDisposable.add(iGoogleAPI.getDirections("driving",
                                    "less_driving",
                                    tripPlanModel.getOrigin(),driverLocation,
                                    getString(R.string.google_api_key))
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(returnResult -> {

                                        PolylineOptions yellowPolylineOptions=null;
                                        List<LatLng> polylineList = null;
                                        Polyline yellowPolyline= null;


                                        try {
                                            //conectare JSON
                                            JSONObject jsonObject = new JSONObject(returnResult);
                                            JSONArray jsonArray = jsonObject.getJSONArray("routes");
                                            for(int i=0;i<jsonArray.length();i++){
                                                JSONObject route = jsonArray.getJSONObject(i);
                                                JSONObject poly = route.getJSONObject("overview_polyline");
                                                String polyline = poly.getString("points");
                                                polylineList = Common.decodePoly(polyline);

                                            }


                                            yellowPolylineOptions = new PolylineOptions();
                                            yellowPolylineOptions.color(Color.YELLOW);
                                            yellowPolylineOptions.width(5);
                                            yellowPolylineOptions.startCap(new SquareCap());
                                            yellowPolylineOptions.jointType(JointType.ROUND);
                                            yellowPolylineOptions.addAll(polylineList);
                                            yellowPolyLine = mMap.addPolyline(yellowPolylineOptions);

                                            JSONObject object = jsonArray.getJSONObject(0);
                                            JSONArray legs = object.getJSONArray("legs");
                                            JSONObject legObjects = legs.getJSONObject(0);

                                            JSONObject time = legObjects.getJSONObject("duration");
                                            String duration = time.getString("text");

                                            JSONObject distanceEstimate = legObjects.getJSONObject("distance");
                                            String distance = distanceEstimate.getString("text");

                                            LatLng origin = new LatLng(
                                                    Double.parseDouble(tripPlanModel.getOrigin().split(",")[0]),
                                                    Double.parseDouble(tripPlanModel.getOrigin().split(",")[1]));

                                            LatLng destination = new LatLng(tripPlanModel.getCurrentLat(),tripPlanModel.getCurrentLng());

                                            LatLngBounds latLngBounds = new LatLngBounds.Builder()
                                                    .include(origin)
                                                    .include(destination)
                                                    .build();

                                            addPickupMarkerWithDuration(duration,origin);
                                            addDriverMarker(destination);

                                            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds,160));
                                            mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.getCameraPosition().zoom-1));

                                            initDriverForMoving(event.getTripIp(),tripPlanModel);

                                            //incarcare informatii si imagine sofer
                                            Glide.with(RequestDriverActivity.this)
                                                    .load(tripPlanModel.getDriverInfoModel().getAvatar())
                                                    .into(img_driver);
                                            txt_driver_name.setText(tripPlanModel.getDriverInfoModel().getFirstName());
                                            txt_rating.setText(String.valueOf(tripPlanModel.getDriverInfoModel().getRating()));
                                            confirm_pickup_layout.setVisibility(View.GONE);
                                            confirm_concab_layout.setVisibility(View.GONE);
                                            driver_info_layout.setVisibility(View.VISIBLE);

                                        }
                                        catch (Exception e){
                                            Toast.makeText(RequestDriverActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    })
                            );


                        }
                        else
                             Snackbar.make(main_layout,getString(R.string.trip_not_found)+event.getTripIp(),Snackbar.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Snackbar.make(main_layout,error.getMessage(),Snackbar.LENGTH_SHORT).show();
                    }
                });

    }

    private void initDriverForMoving(String tripIp, TripPlanModel tripPlanModel) {
            driverOldPosition = new StringBuilder()
                    .append(tripPlanModel.getCurrentLat())
                    .append(",")
                    .append(tripPlanModel.getCurrentLng())
                    .toString();

            FirebaseDatabase.getInstance()
                    .getReference(Common.TRIP)
                    .child(tripIp)
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            TripPlanModel newData =snapshot.getValue(TripPlanModel.class);

                            String driverNewLocation = new StringBuilder()
                                    .append(newData.getCurrentLat())
                                    .append(",")
                                    .append(newData.getCurrentLng())
                                    .toString();

                            if(!driverOldPosition.equals(driverNewLocation))
                                moveMarkerAnimation(destinationMarker,driverOldPosition,driverNewLocation);

                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                                Snackbar.make(main_layout,error.getMessage(),Snackbar.LENGTH_SHORT).show();
                        }
                    });
    }

    private void moveMarkerAnimation(Marker marker, String from, String to) {

        compositeDisposable.add(iGoogleAPI.getDirections("driving",
                "less_driving",
                from,to,
                getString(R.string.google_api_key))
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
                             polylineList = Common.decodePoly(polyline);
                        }



                        yellowPolylineOptions = new PolylineOptions();
                        yellowPolylineOptions.color(Color.YELLOW);
                        yellowPolylineOptions.width(5);
                        yellowPolylineOptions.startCap(new SquareCap());
                        yellowPolylineOptions.jointType(JointType.ROUND);
                        yellowPolylineOptions.addAll(polylineList);
                        yellowPolyLine = mMap.addPolyline(yellowPolylineOptions);

                        JSONObject object = jsonArray.getJSONObject(0);
                        JSONArray legs = object.getJSONArray("legs");
                        JSONObject legObjects = legs.getJSONObject(0);

                        JSONObject time = legObjects.getJSONObject("duration");
                        String duration = time.getString("text");

                        JSONObject distanceEstimate = legObjects.getJSONObject("distance");
                        String distance = distanceEstimate.getString("text");

                        Bitmap bitmap = Common.creatIconWithDuration(RequestDriverActivity.this,duration);
                        originMarker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap));

                            handler= new Handler();

                            index = -1;
                            next = 1;
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if(index<polylineList.size() -2 )
                                    {
                                        index++;
                                        next=index+1;
                                        start =polylineList.get(index);
                                        end = polylineList.get(next);
                                    }
                                ValueAnimator valueAnimator = ValueAnimator.ofInt(0,1);
                                    valueAnimator.setDuration(1500);
                                    valueAnimator.setInterpolator(new LinearInterpolator());
                                    valueAnimator.addUpdateListener(animation -> {
                                        v = animation.getAnimatedFraction();
                                        lng=v*end.longitude+(1-v)*start.longitude;
                                        lat=v*end.latitude+(1-v)*start.latitude;
                                        LatLng newPos = new LatLng(lat,lng);
                                        marker.setPosition(newPos);
                                        marker.setAnchor(0.5f,0.5f);
                                        marker.setRotation(Common.getBearing(start,newPos));

                                        mMap.moveCamera(CameraUpdateFactory.newLatLng(newPos));
                                    });

                                    valueAnimator.start();
                                    if(index < polylineList.size() -2)
                                        handler.postDelayed(this,1500);
                                    else
                                        if(index<polylineList.size() -1)
                                        {

                                        }


                                }
                            },1500);

                            driverOldPosition =to ; //setare noua locatia a soferului

                    }
                    catch (Exception e){
                        Snackbar.make(main_layout,e.getMessage(),Snackbar.LENGTH_LONG).show();
                    }
                },throwable -> {
                    if(throwable !=null)
                        Snackbar.make(main_layout,throwable.getMessage(),Snackbar.LENGTH_SHORT).show();

                })
        );
    }

    private void addDriverMarker(LatLng destination) {
        destinationMarker = mMap.addMarker(new MarkerOptions().position(destination).flat(true)
        .icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_car_traseu)));
    }

    private void addPickupMarkerWithDuration(String duration, LatLng origin) {
        Bitmap icon = Common.creatIconWithDuration(this,duration);
        originMarker = mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon)).position(origin));
    }

    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    public void onSelectPlaceEvent(SelectPlaceEvent event){
        selectPlaceEvent = event;

    }

    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    public void onDeclineRequestEvent(DeclineRequestFromDriver event)
    {
        if(lastDriverCall !=  null)
        {
            Common.driversFound.get(lastDriverCall.getKey()).setDecline(true);
            /// Am respins cererea de cursa, se cauta alt sofer
            findNearbyDriver(selectPlaceEvent);
        }
    }

    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    public void onDeclineAndRemoveRequestEvent(DeclineAndRemoveRequestFromDriver event)
    {
        if(lastDriverCall !=  null)
        {
            Common.driversFound.get(lastDriverCall.getKey()).setDecline(true);
            /// Am respins cererea de cursa si incheiem activitatea
            finish();

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_driver);
        init();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void init() {
        ButterKnife.bind(this);
        iGoogleAPI = RetrofitClient.getInstance().create(IGoogleAPI.class);
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

        drawPath(selectPlaceEvent);

        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this,
                    R.raw.concab_maps_style));
            if (!success)
                Toast.makeText(this, "Incarcarea hartii a esuat... ", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void drawPath(SelectPlaceEvent selectPlaceEvent) {
        //cerere de API
        compositeDisposable.add(iGoogleAPI.getDirections("driving",
                "less_driving",
                selectPlaceEvent.getOriginString(),selectPlaceEvent.getDestinationString(),
                getString(R.string.google_api_key))
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
                            polylineList = Common.decodePoly(polyline);

                        }

                        polylineOptions = new PolylineOptions();
                        polylineOptions.color(Color.BLACK);
                        polylineOptions.width(12);
                        polylineOptions.startCap(new SquareCap());
                        polylineOptions.jointType(JointType.ROUND);
                        polylineOptions.addAll(polylineList);
                        blackPolyLine = mMap.addPolyline(polylineOptions);

                        yellowPolylineOptions = new PolylineOptions();
                        yellowPolylineOptions.color(Color.YELLOW);
                        yellowPolylineOptions.width(5);
                        yellowPolylineOptions.startCap(new SquareCap());
                        yellowPolylineOptions.jointType(JointType.ROUND);
                        yellowPolylineOptions.addAll(polylineList);
                        yellowPolyLine = mMap.addPolyline(yellowPolylineOptions);


                                // Animator
                        ValueAnimator valueAnimator = ValueAnimator.ofInt(0,100);
                        valueAnimator.setDuration(1100);
                        valueAnimator.setRepeatCount(ValueAnimator.INFINITE);
                        valueAnimator.setInterpolator(new LinearInterpolator());
                        valueAnimator.addUpdateListener(animation -> {
                           List<LatLng> points = blackPolyLine.getPoints();
                           int percentValue = (int)animation.getAnimatedValue();
                           int size = points.size();
                           int newPoints= (int)(size * (percentValue/100.0f));
                           List<LatLng> p = points.subList(0,newPoints);
                           yellowPolyLine.setPoints(p);

                        });

                        valueAnimator.start();

                        LatLngBounds latLngBounds = new LatLngBounds.Builder()
                                .include(selectPlaceEvent.getOrigin())
                                .include(selectPlaceEvent.getDestination())
                                .build();

                        //Icon masina pentru loc initial
                        JSONObject object = jsonArray.getJSONObject(0);
                        JSONArray legs = object.getJSONArray("legs");
                        JSONObject legObjects = legs.getJSONObject(0);

                        JSONObject time = legObjects.getJSONObject("duration");
                        String duration = time.getString("text");

                        String start_address = legObjects.getString("start_address");
                        String end_address = legObjects.getString("end_address");

                        addOriginMarker(duration,start_address);

                        addDestinationMarker(end_address);

                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds,160));
                        mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.getCameraPosition().zoom-1));



                    }
                    catch (Exception e){
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
        );
    }

    private void addDestinationMarker(String end_address) {
        View view = getLayoutInflater().inflate(R.layout.destination_info_window,null);
        TextView txt_destination= (TextView)view.findViewById(R.id.txt_destination);

        txt_destination.setText(Common.formatAdresa(end_address));

        IconGenerator generator = new IconGenerator(this);
        generator.setContentView(view);
        generator.setBackground(new ColorDrawable(Color.TRANSPARENT));
        Bitmap icon = generator.makeIcon();

        destinationMarker = mMap.addMarker(new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectPlaceEvent.getDestination()));



    }

    private void addOriginMarker(String duration, String start_address) {
        View view = getLayoutInflater().inflate(R.layout.origin_info_window,null);
        TextView txt_time = (TextView)view.findViewById(R.id.txt_time);
        txt_origin = (TextView)view.findViewById(R.id.txt_origin);

        txt_time.setText(Common.formatDurata(duration));
        txt_origin.setText(Common.formatAdresa(start_address));

        //Icon pentru marker
        IconGenerator generator = new IconGenerator(this);
        generator.setContentView(view);
        generator.setBackground(new ColorDrawable(Color.TRANSPARENT));
        Bitmap icon = generator.makeIcon();

        originMarker = mMap.addMarker(new MarkerOptions()
            .icon(BitmapDescriptorFactory.fromBitmap(icon))
          .position(selectPlaceEvent.getOrigin()));
    }
}