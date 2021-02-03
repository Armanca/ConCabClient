package ro.armanca.concabclient.Utils;

import android.content.Context;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import ro.armanca.concabclient.Common.Common;
import ro.armanca.concabclient.Model.DriverGeoModel;
import ro.armanca.concabclient.EventBus.SelectPlaceEvent;
import ro.armanca.concabclient.Model.FCMSendData;
import ro.armanca.concabclient.Model.TokenModel;
import ro.armanca.concabclient.R;
import ro.armanca.concabclient.Remote.IFCMService;
import ro.armanca.concabclient.Remote.RetrofitFCMClient;

public class UserUtils {

    public static void updateUser(View view, Map<String, Object> updateData) {
        FirebaseDatabase.getInstance()
                .getReference(Common.CLIENT_INFO_REFERENCE)
                .child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .updateChildren(updateData)
                .addOnFailureListener(e -> Snackbar.make(view,e.getMessage(),Snackbar.LENGTH_SHORT).show())
                .addOnSuccessListener(aVoid -> Snackbar.make(view,"Update cu succes",Snackbar.LENGTH_SHORT).show());
    }

    public static void updateToken(Context context, String token) {
        TokenModel tokenModel= new TokenModel(token);
        FirebaseDatabase.getInstance()
                .getReference(Common.TOKEN_REFERENCE)
                .child(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .setValue(tokenModel)
                .addOnFailureListener(e -> Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show()).
                addOnSuccessListener(aVoid -> {

                });
    }

    public static void sendRequestDriver(Context context, RelativeLayout main_layout, DriverGeoModel foundDriver, SelectPlaceEvent selectPlaceEvent) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();
        IFCMService ifcmService = RetrofitFCMClient.getInstance().create(IFCMService.class);

        //obtinem Token
        FirebaseDatabase
                .getInstance()
                .getReference(Common.TOKEN_REFERENCE)
                .child(foundDriver.getKey())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(snapshot.exists()){
                            TokenModel tokenModel =  snapshot.getValue(TokenModel.class);
                            Map<String,String> notificationData = new HashMap<>();
                            notificationData.put(Common.NOTI_TITLE,Common.REQUEST_DRIVER_TITLE);
                            notificationData.put(Common.NOTI_CONTENT,"O cerere de cursă nouă");
                            notificationData.put(Common.CLIENT_KEY,FirebaseAuth.getInstance().getCurrentUser().getUid());

                            notificationData.put(Common.CLIENT_PICKUP_LOCATION_STRING,selectPlaceEvent.getOriginString());
                            notificationData.put(Common.CLIENT_PICKUP_LOCATION, new StringBuilder("")
                                        .append(selectPlaceEvent.getOrigin().latitude)
                                        .append(",")
                                        .append(selectPlaceEvent.getOrigin().longitude)
                                    .toString());


                            notificationData.put(Common.CLIENT_DESTINATION_STRING,selectPlaceEvent.getAddress());
                            notificationData.put(Common.CLIENT_DESTINATION, new StringBuilder("")
                                    .append(selectPlaceEvent.getDestination().latitude)
                                    .append(",")
                                    .append(selectPlaceEvent.getDestination().longitude)
                                    .toString());

                            FCMSendData fcmSendData = new FCMSendData(tokenModel.getToken(),notificationData);

                            compositeDisposable.add(ifcmService.sendNotification(fcmSendData)
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(fcmResponse -> {
                                    if(fcmResponse.getSuccess() == 0)
                                    {
                                        compositeDisposable.clear();
                                        Snackbar.make(main_layout, context.getString(R.string.request_driver_failed),Snackbar.LENGTH_LONG).show();

                                    }

                                }, throwable -> {
                                    compositeDisposable.clear();
                                    Snackbar.make(main_layout, throwable.getMessage(),Snackbar.LENGTH_LONG).show();
                                }));



                        }
                        else
                        {
                            Snackbar.make(main_layout, context.getString(R.string.token_not_found),Snackbar.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                        Snackbar.make(main_layout,error.getMessage(),Snackbar.LENGTH_LONG).show();

                    }
                });
    }
}
