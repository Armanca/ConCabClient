package ro.armanca.concabclient.Model.EventBus;

public class ShowNotificationFinishTrip {
    private String tripKey;

    public String getTripKey() {
        return tripKey;
    }

    public void setTripKey(String tripKey) {
        this.tripKey = tripKey;
    }

    public ShowNotificationFinishTrip(String tripKey) {
        this.tripKey = tripKey;
    }
}
