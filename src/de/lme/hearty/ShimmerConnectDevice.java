package de.lme.hearty;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;




class ShimmerConnectDevice extends ShimmerDataSource
{
	private static final UUID		SPP_UUID	= UUID.fromString( "00001101-0000-1000-8000-00805F9B34FB" );

	// @formatter:off
    private static final byte SHIMMER_DATA_PACKET = (byte) 0x00,
	    SHIMMER_ACK = (byte) 0xFF, SHIMMER_START_TRANSFER = (byte) 0x07,
	    SHIMMER_STOP_TRANSFER = (byte) 0x20,
	    SHIMMER_TOGGLE_LED = (byte) 0x06,
	    SHIMMER_SET_SAMPLING_RATE = (byte) 0x05,
	    SHIMMER_SET_ACCEL_RANGE = (byte) 0x09,
	    SHIMMER_SET_SENSORS = (byte) 0x08,

	    SHIMMER_SAMPLING_1000HZ = (byte) 1,
	    SHIMMER_SAMPLING_500HZ = (byte) 2,
	    SHIMMER_SAMPLING_250HZ = (byte) 4,
	    SHIMMER_SAMPLING_200HZ = (byte) 5,
	    SHIMMER_SAMPLING_166HZ = (byte) 6,
	    SHIMMER_SAMPLING_125HZ = (byte) 8,
	    SHIMMER_SAMPLING_100HZ = (byte) 10,
	    SHIMMER_SAMPLING_50HZ = (byte) 20,
	    SHIMMER_SAMPLING_10HZ = (byte) 100,
	    SHIMMER_SAMPLING_0HZ_OFF = (byte) 255,

	    SHIMMER_RANGE_1_5G = (byte) 0, SHIMMER_RANGE_2_0G = (byte) 1,
	    SHIMMER_RANGE_4_0G = (byte) 2, SHIMMER_RANGE_6_0G = (byte) 3,

	    SHIMMER_SENSOR_ACCEL = (byte) 0x80,
	    SHIMMER_SENSOR_GYRO = (byte) 0x40,
	    SHIMMER_SENSOR_ECG = (byte) 0x10;
 // @formatter:on

	private static final int		BUFFER_SIZE			= 512;

	private final BluetoothDevice	mmDevice;
	private BluetoothSocket			mmSocket;
	private InputStream				mmInStream;
	private OutputStream			mmOutStream;
	private Thread					mmThread;
	private String					mmID;
	private ShimmerData				mData;

	private byte[]					mBuffer;
	private int						mBufferWritePos;
	private int						mBufferFillCount;

	private int						mChannelCount;


	public ShimmerConnectDevice (BluetoothDevice device)
	{
		mmDevice = device;
		mmThread = null;
		mData = new ShimmerData();
		mmID = mmDevice.getName();
		mBuffer = new byte[ BUFFER_SIZE ];
		mBufferWritePos = 0;
		mBufferFillCount = 0;
	}


	public String getID ()
	{
		return mmID;
	}


	public void connect ()
	{
		try
		{
			mmSocket = mmDevice.createRfcommSocketToServiceRecord( SPP_UUID );

			mmSocket.connect();

			mmInStream = mmSocket.getInputStream();
			mmOutStream = mmSocket.getOutputStream();

			SystemClock.sleep( 500 );
			setState( State.CONNECTED );
			setAccelRange( AccelRange.RANGE_1_5G );
			SystemClock.sleep( 100 );
			setSamplingRate( SampleRate.SAMPLING_10HZ );
			SystemClock.sleep( 100 );

			disableGyro();

		}
		catch (IOException connectException)
		{
			try
			{
				mmSocket.close();
			}
			catch (IOException closeException)
			{}
			return;
		}

		mmThread = new Thread( new Runnable() {
			public void run ()
			{
				receiveData();
			}
		} );

		mmThread.start();
	}


	public void disconnect ()
	{
		stop();

		if (state != State.CONNECTED)
			return;

		try
		{
			mmSocket.close();
			setState( State.DISCONNECTED );
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public void start ()
	{
		if (state != State.CONNECTED)
			return;

		try
		{
			mmOutStream.write( SHIMMER_START_TRANSFER );
			setState( State.ACQUIRING );
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public void stop ()
	{
		if (state != State.ACQUIRING)
			return;

		try
		{
			mmOutStream.write( SHIMMER_STOP_TRANSFER );
			setState( State.CONNECTED );
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	private void receiveData ()
	{
		byte[] buffer = new byte[ BUFFER_SIZE ];
		int bytes;

		while (true)
		{
			try
			{
				bytes = mmInStream.read( buffer, 0, BUFFER_SIZE - mBufferFillCount );

				if (bytes > 0)
				{
					if (bytes <= (BUFFER_SIZE - mBufferWritePos))
						System.arraycopy( buffer, 0, mBuffer, mBufferWritePos, bytes );
					else
					{
						System.arraycopy( buffer, 0, mBuffer, mBufferWritePos, (BUFFER_SIZE - mBufferWritePos) );
						System.arraycopy( buffer, (BUFFER_SIZE - mBufferWritePos), mBuffer, 0, bytes
								- (BUFFER_SIZE - mBufferWritePos) );
					}

					mBufferWritePos = (mBufferWritePos + bytes) % BUFFER_SIZE;
					mBufferFillCount = mBufferFillCount + bytes;
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
				break;
			}

			try
			{
				parseData();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				break;
			}
		}
	}


	private void parseData ()
	{

		/*
		 * Data Packet Format:
		 * 
		 * Packet Type | TimeStamp | chan1 | chan2 | ... | chanX Byte: 0 | 1-2 |
		 * 3-4 | 5-6 | ... | chanX
		 */

		while (mBufferFillCount > 0)
		{

			int pos = (mBufferWritePos - mBufferFillCount + BUFFER_SIZE) % BUFFER_SIZE;

			if (mBuffer[ pos % BUFFER_SIZE ] != SHIMMER_DATA_PACKET)
			{
				mBufferFillCount--; // No Data Packet Identifier -> drop byte
			}
			else
			{
				int packageSize = mChannelCount * 2 + 2 + 1;
				if (mBufferFillCount < packageSize)
				{
					return; // Not enough bytes left -> return
				}
				else
				{
					mData.sensor = sensorName;
					mData.datatype = 0xFF;
					mData.packetid = 0xFF;
					mData.timestamp = (long) ( ((int) mBuffer[ (pos + 2) % BUFFER_SIZE ] & 0x000000FF) << 8)
							| ((int) mBuffer[ (pos + 1) % BUFFER_SIZE ] & 0x000000FF);
					if (mChannelCount > 0)
						mData.accel_x = ( ((int) mBuffer[ (pos + 4) % BUFFER_SIZE ] & 0x000000FF) << 8)
								| ((int) mBuffer[ (pos + 3) % BUFFER_SIZE ] & 0x000000FF);
					if (mChannelCount > 1)
						mData.accel_y = ( ((int) mBuffer[ (pos + 6) % BUFFER_SIZE ] & 0x000000FF) << 8)
								| ((int) mBuffer[ (pos + 5) % BUFFER_SIZE ] & 0x000000FF);
					if (mChannelCount > 2)
						mData.accel_z = ( ((int) mBuffer[ (pos + 8) % BUFFER_SIZE ] & 0x000000FF) << 8)
								| ((int) mBuffer[ (pos + 7) % BUFFER_SIZE ] & 0x000000FF);
					if (mChannelCount > 3)
						mData.sensor_1 = ( ((int) mBuffer[ (pos + 10) % BUFFER_SIZE ] & 0x000000FF) << 8)
								| ((int) mBuffer[ (pos + 9) % BUFFER_SIZE ] & 0x000000FF);
					if (mChannelCount > 4)
						mData.sensor_2 = ( ((int) mBuffer[ (pos + 12) % BUFFER_SIZE ] & 0x000000FF) << 8)
								| ((int) mBuffer[ (pos + 11) % BUFFER_SIZE ] & 0x000000FF);
					if (mChannelCount > 5)
						mData.sensor_3 = ( ((int) mBuffer[ (pos + 14) % BUFFER_SIZE ] & 0x000000FF) << 8)
								| ((int) mBuffer[ (pos + 13) % BUFFER_SIZE ] & 0x000000FF);

					mBufferFillCount -= packageSize;

					publishData( mData );
				}
			}
		}
		// No bytes left -> return
	}


	public void setSamplingRate (SampleRate samplingRate)
	{
		if (state != State.CONNECTED)
			return;

		try
		{
			mmOutStream.write( SHIMMER_SET_SAMPLING_RATE );
			switch (samplingRate)
			{
				case SAMPLING_1000HZ:
					mmOutStream.write( SHIMMER_SAMPLING_1000HZ );
					super.setSamplingRate( 1000 );
					break;
				case SAMPLING_500HZ:
					mmOutStream.write( SHIMMER_SAMPLING_500HZ );
					super.setSamplingRate( 500 );
					break;
				case SAMPLING_250HZ:
					mmOutStream.write( SHIMMER_SAMPLING_250HZ );
					super.setSamplingRate( 250 );
					break;
				case SAMPLING_200HZ:
					mmOutStream.write( SHIMMER_SAMPLING_200HZ );
					super.setSamplingRate( 200 );
					break;
				case SAMPLING_166HZ:
					mmOutStream.write( SHIMMER_SAMPLING_166HZ );
					super.setSamplingRate( 166 );
					break;
				case SAMPLING_125HZ:
					mmOutStream.write( SHIMMER_SAMPLING_125HZ );
					super.setSamplingRate( 125 );
					break;
				case SAMPLING_100HZ:
					mmOutStream.write( SHIMMER_SAMPLING_100HZ );
					super.setSamplingRate( 100 );
					break;
				case SAMPLING_50HZ:
					mmOutStream.write( SHIMMER_SAMPLING_50HZ );
					super.setSamplingRate( 50 );
					break;
				case SAMPLING_10HZ:
					mmOutStream.write( SHIMMER_SAMPLING_10HZ );
					super.setSamplingRate( 10 );
					break;
				case SAMPLING_0HZ_OFF:
					mmOutStream.write( SHIMMER_SAMPLING_0HZ_OFF );
					super.setSamplingRate( 0 );
					break;
			}

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public void setAccelRange (AccelRange accelRange)
	{
		if (state != State.CONNECTED)
			return;

		try
		{
			mmOutStream.write( SHIMMER_SET_ACCEL_RANGE );
			switch (accelRange)
			{
				case RANGE_1_5G:
					mmOutStream.write( SHIMMER_RANGE_1_5G );
					break;
				case RANGE_2_0G:
					mmOutStream.write( SHIMMER_RANGE_2_0G );
					break;
				case RANGE_4_0G:
					mmOutStream.write( SHIMMER_RANGE_4_0G );
					break;
				case RANGE_6_0G:
					mmOutStream.write( SHIMMER_RANGE_6_0G );
					break;
			}

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public void toggleLED ()
	{
		if (state != State.CONNECTED)
			return;

		try
		{
			mmOutStream.write( SHIMMER_TOGGLE_LED );

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public void enableGyro ()
	{
		if (state != State.CONNECTED)
			return;

		try
		{
			mmOutStream.write( SHIMMER_SET_SENSORS );
			mmOutStream.write( SHIMMER_SENSOR_ACCEL | SHIMMER_SENSOR_GYRO );
			mmOutStream.write( 0x0 );
			mChannelCount = 6;

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public void enableECG ()
	{
		if (state != State.CONNECTED)
			return;

		try
		{
			mmOutStream.write( SHIMMER_SET_SENSORS );
			mmOutStream.write( SHIMMER_SENSOR_ACCEL | SHIMMER_SENSOR_ECG );
			mmOutStream.write( 0x00 );
			mChannelCount = 5;

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public void disableGyro ()
	{
		if (state != State.CONNECTED)
			return;

		try
		{
			mmOutStream.write( SHIMMER_SET_SENSORS );
			mmOutStream.write( SHIMMER_SENSOR_ACCEL );
			mmOutStream.write( 0x00 );
			mChannelCount = 3;

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	public void disableECG ()
	{
		if (state != State.CONNECTED)
			return;

		try
		{
			mmOutStream.write( SHIMMER_SET_SENSORS );
			mmOutStream.write( SHIMMER_SENSOR_ACCEL );
			mmOutStream.write( 0x00 );
			mChannelCount = 3;

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see de.lme.shimmer.shimmerdisplay.ShimmerDataSource#getName()
	 */
	@Override
	public String getName ()
	{
		return getID();
	}

}