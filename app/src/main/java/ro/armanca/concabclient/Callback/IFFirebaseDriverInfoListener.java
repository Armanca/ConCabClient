package ro.armanca.concabclient.Callback;

import ro.armanca.concabclient.Model.DriverGeoModel;

public interface IFFirebaseDriverInfoListener {
    void onDriverInfoLoadSuccess(DriverGeoModel driverGeoModel);
}
