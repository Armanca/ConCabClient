package ro.armanca.concabclient.Remote;

import io.reactivex.Observable;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import ro.armanca.concabclient.Model.FCMResponse;
import ro.armanca.concabclient.Model.FCMSendData;

public interface IFCMService {
    @Headers({
            "Content-Type:application/json",
            "Authorization:key=AAAAnKIS3W4:APA91bG9N0jomdoCFLcHz1qymp1k-H9sUqarzFL6tsnVS2-1QFldIxZQuWYAwfL2YYrc8mz5VI4HMkhca_Uz2D1JWSWEJopN9Lpx_sp8UDQuBkXFUFBtey5EOx2PrT6qkreCbv_K85Fs"
    })
    @POST("fcm/send")
      Observable<FCMResponse>  sendNotification(@Body FCMSendData body);
}
