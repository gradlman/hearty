package de.lme.hearty;

import de.lme.hearty.IShimmerSimServiceCallback;

interface IShimmerSimService {
	/**
     * Often you want to allow a service to call back to its clients.
     * This shows how to do so, by registering a callback interface with
     * the service.
     */
    void registerCallback(IShimmerSimServiceCallback cb);
    
    /**
     * Remove a previously registered callback interface.
     */
    void unregisterCallback(IShimmerSimServiceCallback cb);
}