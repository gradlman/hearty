package de.lme.hearty;


import java.util.ArrayList;




public abstract class ShimmerDataSource
{

	public static interface OnShimmerDataListener
	{
		abstract void onShimmerData (ShimmerData data);
	}

	public static interface OnShimmerStateChangedListener
	{
		abstract void onShimmerStateChanged (State state);
	}

	class ShimmerData
	{
		public int	sensor;
		public int	datatype;
		public int	packetid;
		public long	timestamp;
		public int	accel_x;
		public int	accel_y;
		public int	accel_z;
		public int	sensor_1;
		public int	sensor_2;
		public int	sensor_3;
	}

	public static enum State
	{
		DISCONNECTED, CONNECTED, ACQUIRING, ERROR
	}

	public static enum SampleRate
	{
		SAMPLING_1000HZ, SAMPLING_500HZ, SAMPLING_250HZ, SAMPLING_200HZ, SAMPLING_166HZ, SAMPLING_125HZ, SAMPLING_100HZ, SAMPLING_50HZ, SAMPLING_10HZ, SAMPLING_0HZ_OFF;
	}

	public static enum AccelRange
	{
		RANGE_1_5G, RANGE_2_0G, RANGE_4_0G, RANGE_6_0G;
	}


	private ArrayList< OnShimmerDataListener >			onShimmerDataListeners			= new ArrayList< OnShimmerDataListener >();
	private ArrayList< OnShimmerStateChangedListener >	onShimmerStateChangedListeners	= new ArrayList< OnShimmerStateChangedListener >();

	protected State										state							= State.DISCONNECTED;
	protected double									samplingRate;


	public void addOnShimmerDataListener (OnShimmerDataListener listener)
	{
		onShimmerDataListeners.add( listener );
	}


	public void removeOnShimmerDataListener (OnShimmerDataListener listener)
	{
		onShimmerDataListeners.remove( listener );
	}


	public void addOnShimmerStateChangedListener (OnShimmerStateChangedListener listener)
	{
		onShimmerStateChangedListeners.add( listener );
	}


	public void removeOnShimmerStateChangedListener (OnShimmerStateChangedListener listener)
	{
		onShimmerStateChangedListeners.remove( listener );
	}


	protected void setState (final State state)
	{
		this.state = state;
		publishState();
	}


	private void publishState ()
	{
		for (OnShimmerStateChangedListener listener : onShimmerStateChangedListeners)
		{
			listener.onShimmerStateChanged( this.state );
		}
	}


	protected void publishData (final ShimmerData data)
	{
		if (state == State.ACQUIRING)
			for (OnShimmerDataListener listener : onShimmerDataListeners)
			{
				listener.onShimmerData( data );
			}
	}


	public double getSamplingRate ()
	{
		return samplingRate;
	}


	protected void setSamplingRate (double samplingRate)
	{
		this.samplingRate = samplingRate;
	}


	public State getState ()
	{
		return state;
	}


	abstract public void connect ();


	abstract public void disconnect ();


	abstract public void start ();


	abstract public void stop ();


	abstract public void setSamplingRate (SampleRate samplingRate);


	abstract public void setAccelRange (AccelRange accelRange);


	abstract public void toggleLED ();


	abstract public void enableGyro ();


	abstract public void disableGyro ();


	abstract public void enableECG ();


	abstract public void disableECG ();


	/**
	 * Returns a human-readable name for this data source (e.g. bluetooth name, filename, etc...)
	 * 
	 * @return String containing the name.
	 */
	abstract public String getName ();


	protected int	sensorName;


	public void setSensorID (int sensorID)
	{
		sensorName = sensorID;
	}
}
