package ro.armanca.concabclient.Common;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import ro.armanca.concabclient.Model.ClientModel;
import ro.armanca.concabclient.R;

public class Common {
    public static final String CLIENT_INFO_REFERENCE ="ClientInfo";
    public static final String TOKEN_REFERENCE = "Token";
    public static final String NOTI_CONTENT = "body";
    public static final String NOTI_TITLE = "title";

    public static ClientModel currentClient;

    public static String buildWelcomeMessage() {

        if(Common.currentClient!=null){
            return new StringBuilder("Bine ai venit ")
                    .append(Common.currentClient.getFirstName())
                    .append(" ")
                    .append(Common.currentClient.getLastName()).toString();
        }
        else
        {
            return "";
        }

    }

    public static void showNotification(Context context, int id, String title, String body, Intent intent) {
        PendingIntent pendingIntent = null;
        if(intent != null)
            pendingIntent = PendingIntent.getActivity(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT);
        String NOTIFICATION_CHANNEL_ID="ADR_ConCabClient";
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
        {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,"ConCabClient",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setDescription("ConCabClient");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[]{0,100,500,1000});
            notificationChannel.enableVibration(true);

            notificationManager.createNotificationChannel(notificationChannel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
        builder.setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setSmallIcon(R.drawable.concab_faviconpng)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),R.drawable.concab_faviconpng));
        if(pendingIntent !=null){
            builder.setContentIntent(pendingIntent);
        }
        Notification notification= builder.build();
        notificationManager.notify(id,notification);
    }

}
