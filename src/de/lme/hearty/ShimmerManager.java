package de.lme.hearty;


import java.util.HashSet;
import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;




public class ShimmerManager extends Object
{
	private final BluetoothAdapter	mmBluetoothAdapter;


	public ShimmerManager ()
	{
		mmBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}


	public Set< ShimmerDataSource > getShimmerDevices ()
	{
		Set< ShimmerDataSource > mmShimmerDevices;
		mmShimmerDevices = new HashSet< ShimmerDataSource >();
		mmShimmerDevices.clear();

		Set< BluetoothDevice > pairedDevices = mmBluetoothAdapter.getBondedDevices();

		if (pairedDevices.size() > 0)
		{
			for (BluetoothDevice device : pairedDevices)
			{
				mmShimmerDevices.add( new ShimmerConnectDevice( device ) );
			}
		}

		return mmShimmerDevices;
	}


	public ShimmerDataSource getShimmerDeviceByID (String id)
	{
		Set< BluetoothDevice > pairedDevices = mmBluetoothAdapter.getBondedDevices();

		// mBluetoothAdapter.getRemoteDevice(address);

		if (pairedDevices.size() > 0)
		{
			for (BluetoothDevice device : pairedDevices)
			{
				if (device.getName().equals( id ))
					return new ShimmerConnectDevice( device );
			}
		}

		return null;
	}


	public ShimmerDataSource getShimmerDeviceByAddress (String address)
	{
		Set< BluetoothDevice > pairedDevices = mmBluetoothAdapter.getBondedDevices();

		// mBluetoothAdapter.getRemoteDevice(address);

		if (pairedDevices.size() > 0)
		{
			for (BluetoothDevice device : pairedDevices)
			{
				if (device.getAddress().equals( address ))
					return new ShimmerConnectDevice( device );
			}
		}

		return null;
	}

}