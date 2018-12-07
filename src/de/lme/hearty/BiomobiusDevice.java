package de.lme.hearty;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;




class BiomobiusDevice extends ShimmerDataSource
{
	private static final UUID		SPP_UUID	= UUID.fromString( "00001101-0000-1000-8000-00805F9B34FB" );

	// @formatter:off
    private static final byte SHIMMER_FRAMING_BOF = (byte) 0xC0,
	    SHIMMER_FRAMING_EOF = (byte) 0xC1,
	    SHIMMER_START_TRANSFER = (byte) 0x07,
	    SHIMMER_STOP_TRANSFER = (byte) 0x20,
	    SHIMMER_PACKET_SIZE = (byte) 0x16;
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


	public BiomobiusDevice (BluetoothDevice device)
	{
		mmDevice = device;
		mmThread = null;
		mData = new ShimmerData();
		mmID = mmDevice.getName();
		mBuffer = new byte[ BUFFER_SIZE ];
		mBufferWritePos = 0;
		mBufferFillCount = 0;
		setSamplingRate( 100 );
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
			setState( State.CONNECTED );

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

		// BOF| Sensor ID | Data Type | Seq No. | TimeStamp | Len | Acc | Acc |
		// Acc | ECG | ECG | Dummy| CRC | EOF
		// 0 | 1 | 2 | 3 | 4-5 | 6 | 7-8 | 9-10 | 11-12 | 13-14 | 15-16| 17-18|
		// 19-20| 21

		while (mBufferFillCount > 0)
		{

			int pos = (mBufferWritePos - mBufferFillCount + BUFFER_SIZE) % BUFFER_SIZE;

			if (mBuffer[ pos % BUFFER_SIZE ] != SHIMMER_FRAMING_BOF)
			{
				mBufferFillCount--; // No BOF -> drop byte
			}
			else
			{
				if (mBufferFillCount < SHIMMER_PACKET_SIZE)
				{
					return; // Not enough bytes left -> return
				}
				else
				{
					if (mBuffer[ (pos + SHIMMER_PACKET_SIZE - 1) % BUFFER_SIZE ] != SHIMMER_FRAMING_EOF)
					{
						mBufferFillCount--; // No EOF -> drop byte
					}
					else
					{
						// Framing is OK, compute checksum

						/*
						 * if ((pos + SHIMMER_PACKET_SIZE - 4) < BUFFER_SIZE)
						 * mCRC32.update(mBuffer, pos + 1, SHIMMER_PACKET_SIZE -
						 * 4); else { mCRC32.update(mBuffer, pos + 1,
						 * BUFFER_SIZE-pos); mCRC32.update(mBuffer, 0,
						 * SHIMMER_PACKET_SIZE - 4 - (BUFFER_SIZE-pos)); }
						 */

						// long checksum = (((int)mBuffer[(pos+20) %
						// BUFFER_SIZE] & 0x000000FF) << 8) |
						// ((int)mBuffer[(pos+19) % BUFFER_SIZE] & 0x000000FF);

						// if (checksum == mCRC32.getValue())
						{

							// mData.sensor = (int) mBuffer[(pos+1) %
							// BUFFER_SIZE] & 0x000000FF;
							mData.sensor = sensorName;
							mData.datatype = (int) mBuffer[ (pos + 2) % BUFFER_SIZE ] & 0x000000FF;
							mData.packetid = (int) mBuffer[ (pos + 3) % BUFFER_SIZE ] & 0x000000FF;
							mData.timestamp = (long) ( ((int) mBuffer[ (pos + 5) % BUFFER_SIZE ] & 0x000000FF) << 8)
									| ((int) mBuffer[ (pos + 4) % BUFFER_SIZE ] & 0x000000FF);
							mData.accel_x = ( ((int) mBuffer[ (pos + 8) % BUFFER_SIZE ] & 0x000000FF) << 8)
									| ((int) mBuffer[ (pos + 7) % BUFFER_SIZE ] & 0x000000FF);
							mData.accel_y = ( ((int) mBuffer[ (pos + 10) % BUFFER_SIZE ] & 0x000000FF) << 8)
									| ((int) mBuffer[ (pos + 9) % BUFFER_SIZE ] & 0x000000FF);
							mData.accel_z = ( ((int) mBuffer[ (pos + 12) % BUFFER_SIZE ] & 0x000000FF) << 8)
									| ((int) mBuffer[ (pos + 11) % BUFFER_SIZE ] & 0x000000FF);
							mData.sensor_1 = ( ((int) mBuffer[ (pos + 14) % BUFFER_SIZE ] & 0x000000FF) << 8)
									| ((int) mBuffer[ (pos + 13) % BUFFER_SIZE ] & 0x000000FF);
							mData.sensor_2 = ( ((int) mBuffer[ (pos + 16) % BUFFER_SIZE ] & 0x000000FF) << 8)
									| ((int) mBuffer[ (pos + 15) % BUFFER_SIZE ] & 0x000000FF);
							mData.sensor_3 = ( ((int) mBuffer[ (pos + 18) % BUFFER_SIZE ] & 0x000000FF) << 8)
									| ((int) mBuffer[ (pos + 17) % BUFFER_SIZE ] & 0x000000FF);

							mBufferFillCount -= SHIMMER_PACKET_SIZE;

							publishData( mData );
						}
					}
				}
			}
		}
		// No bytes left -> return
	}


	public void setSamplingRate (SampleRate samplingRate)
	{
		// Biomobius does not support different sampling rates
	}


	public void setAccelRange (AccelRange accelRange)
	{
		// Biomobius does not support different acceleration ranges
	}


	public void toggleLED ()
	{
		// Biomobius does not support LEDs
	}


	public void enableGyro ()
	{
		// Biomobius does not support Gyro control
	}


	public void disableGyro ()
	{
		// Biomobius does not support Gyro control
	}


	public void enableECG ()
	{
		// Biomobius does not support ECG control
	}


	public void disableECG ()
	{
		// Biomobius does not support ECG control
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
