package ro.armanca.concabclient.EventBus;

public class DriverAcceptTripEvent {
    private String tripIp;

    public DriverAcceptTripEvent(String tripIp) {
        this.tripIp = tripIp;
    }

    public String getTripIp() {
        return tripIp;
    }

    public void setTripIp(String tripIp) {
        this.tripIp = tripIp;
    }
}
