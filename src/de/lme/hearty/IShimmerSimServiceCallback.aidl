package de.lme.hearty;

oneway interface IShimmerSimServiceCallback {
    /**
     * Called when the service has a new value for you.
     */
    void onShimmerSimEvent( int eventID, long timestamp, float sensorValue, char label );
}