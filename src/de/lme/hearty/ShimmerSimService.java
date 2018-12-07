/**
 * 
 */
package de.lme.hearty;


import java.io.File;

import android.app.Service;
import android.content.Intent;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;
import de.lme.hearty.ShimmerDataSource.OnShimmerDataListener;
import de.lme.hearty.ShimmerDataSource.SampleRate;
import de.lme.hearty.ShimmerDataSource.ShimmerData;
import de.lme.hearty.ShimmerDataSource.State;
import de.lme.plotview.DataSource;
import de.lme.plotview.Plot;
import de.lme.plotview.Plot.PlotStyle;
import de.lme.plotview.SamplingPlot;




/**
 * Data delivery service for EGC signal samples. The samples are acquired either by connecting to a SHIMMER node via
 * bluetooth or by simulation samples from various file-based sources.
 * 
 * @author sistgrad
 * 
 */
public class ShimmerSimService extends Service implements OnShimmerDataListener
{
	public static final String					TAG							= "de.lme.shimService";
	// Intent Strings
	public static final String					INTENT_EXTRA_SHIM_MODE		= "de.lme.shimmode";
	public static final String					INTENT_EXTRA_DATA_SOURCE	= "de.lme.datasource";
	public static final String					INTENT_EXTRA_DATA_COL		= "de.lme.col";
	public static final String					INTENT_EXTRA_DATA_MULT		= "de.lme.mult";
	public static final String					INTENT_EXTRA_DATA_COUNT		= "de.lme.simcount";

	// Event message types
	public static final int						SHIM_MESSAGE_INIT			= 0;
	public static final int						SHIM_MESSAGE_SHUTDOWN		= 1;
	public static final int						SHIM_MESSAGE_EVENT			= 2;
	public static final int						SHIM_MESSAGE_SIM_EVENT		= 3;
	public static final int						SHIM_MESSAGE_SIM_END		= 4;

	// Operation modes
	public static final int						SHIM_MODE_LIVE				= 0;
	public static final int						SHIM_MODE_SIMULATION		= 1;
	public static final int						SHIM_MODE_INTERN			= 2;
	public static final int						SHIM_MODE_BLUETOOTH			= 3;
	public static final int						SHIM_MODE_INVALID			= 4;

	/** icp callback method for connected app process */
	private static IShimmerSimServiceCallback	m_callback;


	private SamplingPlot						simPlot						= null;
	private int									simPlotIter					= 0;
	/** Post delay in milliseconds */
	private float								simDelay					= 5f;
	/** Virtual simulation time */
	private float								simVirtualTime				= 0f;
	/** Regular simulation time */
	private long								simTime						= 0;


	/**
	 * Encapsulates connection information for the current listener.
	 * 
	 * @author sistgrad
	 * 
	 */
	public static class ShimConnection
	{
		/** Simulation mode */
		public int					simMode			= SHIM_MODE_BLUETOOTH;

		/** Record String of MIT-BIH record file or bluetooth address */
		public String				dataSource		= "default";
		/** Data column in record file */
		public int					dataColumn		= 0;
		/** Multiplier */
		public int					dataMultiplier	= 1;
		/** Number of samples to simulate */
		public int					simCount		= 0;

		public ShimmerDataSource	shimDataSource	= null;


		/**
		 * Establish bt connection.
		 * 
		 * @param sm
		 * @return
		 */
		public boolean connectBluetooth (ShimmerManager sm)
		{
			if (simMode != SHIM_MODE_BLUETOOTH)
			{
				return false;
			}

			shimDataSource = sm.getShimmerDeviceByAddress( dataSource );

			if (shimDataSource == null)
			{
				// error
				return false;
			}

			shimDataSource.setSamplingRate( SampleRate.SAMPLING_100HZ );

			shimDataSource.connect();
			shimDataSource.enableECG();
			shimDataSource.disableGyro();

			if (shimDataSource.getState() != State.CONNECTED)
			{
				return false;
			}


			return true;
		}


		/**
		 * Close bt connection.
		 */
		public void disconnectBluetooth ()
		{
			if (shimDataSource != null)
			{
				shimDataSource.stop();
				shimDataSource.disconnect();
				shimDataSource = null;
			}
		}
	}


	/** Current connection info */
	public static ShimConnection			m_con			= new ShimConnection();

	/** Global SHIMMER device manager */
	private ShimmerManager					m_shimManager	= new ShimmerManager();


	/** WFDB signal samples */
	public static DataSource.wfdbEcgSignal	simSignal		= null;


	/* (non-Javadoc)
	 * @see android.app.Service#onBind(android.content.Intent)
	 */
	@Override
	public IBinder onBind (Intent intent)
	{
		synchronized (m_con)
		{
			// copy sim mode
			m_con.simMode = intent.getIntExtra( INTENT_EXTRA_SHIM_MODE, SHIM_MODE_BLUETOOTH );
			if (m_con.simMode >= SHIM_MODE_INVALID || m_con.simMode < 0)
			{
				// error
				return null;
			}

			// copy data source string
			m_con.dataSource = intent.getStringExtra( INTENT_EXTRA_DATA_SOURCE );
			if (m_con.dataSource == null)
			{
				// error
				return null;
			}

			m_con.dataColumn = intent.getIntExtra( INTENT_EXTRA_DATA_COL, 2 );
			m_con.dataMultiplier = intent.getIntExtra( INTENT_EXTRA_DATA_MULT, 1000 );
			m_con.simCount = intent.getIntExtra( INTENT_EXTRA_DATA_COUNT, 0 );

			Log.d( TAG, "Connecting: " + m_con.dataSource + " via " + m_con.simMode );

			// check for bluetooth request and return binder only if bt-com can be established
			if (m_con.simMode == SHIM_MODE_BLUETOOTH)
			{
				if (m_con.connectBluetooth( m_shimManager ) == false)
				{
					// error
					Toast.makeText( this, "Bluetooth connection error.", Toast.LENGTH_SHORT ).show();
					return null;
				}
			}
		}

		return mBinder;
	}


	/* (non-Javadoc)
	 * @see android.app.Service#onUnbind(android.content.Intent)
	 */
	@Override
	public boolean onUnbind (Intent intent)
	{
		if (m_con.simMode == SHIM_MODE_BLUETOOTH && m_con.shimDataSource != null)
		{
			// close bt conn
			synchronized (m_con.shimDataSource)
			{
				m_con.disconnectBluetooth();
			}
		}
		return super.onUnbind( intent );
	}


	private Looper			mServiceLooper;
	private ServiceHandler	mServiceHandler;
	private Thread			m_loadingThread	= null;
	private HandlerThread	m_serviceThread	= null;


	/* (non-Javadoc)
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate ()
	{
		// === TODO Auto-generated method stub
		super.onCreate();

		if (m_con == null)
			return;

		m_loadingThread = new Thread( new Runnable() {
			public void run ()
			{
				synchronized (m_con)
				{


					// ==============================================
					// == SIMULATED INTERNAL TEST DATA
					// ====>
					if (m_con.simMode == SHIM_MODE_INTERN)
					{
						// load internal test record
						simPlot = new SamplingPlot( "ECG", Plot.generatePlotPaint(), PlotStyle.LINE, 20000 );
						// simPlot.loadFromFile( this, this.getResources().openRawResource( R.raw.ecg_ex ) );
						simPlot.loadFromFile(	ShimmerSimService.this,
												ShimmerSimService.this.getResources().openRawResource( R.raw.ecgpvc ) );
						simDelay = 5f;

						m_con.simCount = simPlot.values.num;
					}
					// <====
					// ==============================================

					// ==============================================
					// == SIMULATED MIT-BIH DATA
					// ====>
					else if (m_con.simMode == SHIM_MODE_LIVE || m_con.simMode == SHIM_MODE_SIMULATION)
					{
						File sigFile = new File( ShimmerSimService.this.getExternalFilesDir( null ), "mitdb" + m_con.dataSource
								+ "sig.csv.gz" );
						File annFile = new File( ShimmerSimService.this.getExternalFilesDir( null ), "mitdb" + m_con.dataSource
								+ "ann.csv" );

						// load MIT-BIH Database record if not already in memory
						// if (simSignal == null || !simSignal.recordName.equals( sigFile.getAbsolutePath() ))
						{
							// m_simCount = 10000;
							Log.d( "ShimService", "Loading... " + m_con.simCount + " " + m_con.dataColumn + " "
									+ m_con.dataMultiplier + " " + sigFile.getAbsolutePath() );
							// load data
							simSignal = new DataSource.wfdbEcgSignal();
							if (simSignal.load( sigFile.getAbsolutePath(),
												annFile.getAbsolutePath(),
												m_con.simCount,
												m_con.dataColumn,
												m_con.dataMultiplier ) != 0)
							{
								Log.d( "ShimService", "error loading file!" );
								return;
							}

							if (m_callback == null || simSignal == null)
							{
								ShimmerSimService.this.stopSelf();
								return;
							}

							// calculate sampling delay
							simDelay = simSignal.sampleInterval * 1000;

							if (m_con.simCount <= 0 || m_con.simCount > simSignal.ml2.num)
								m_con.simCount = simSignal.ml2.num;
						}

					}
					// <====
					// ==============================================

					// ==============================================
					// == BLUETOOTH
					// ====>
					else if (m_con.simMode == SHIM_MODE_BLUETOOTH)
					{

						if (m_con.shimDataSource == null)
						{
							ShimmerSimService.this.stopSelf();
							return;
						}

						m_con.shimDataSource.addOnShimmerDataListener( ShimmerSimService.this );

						m_con.shimDataSource.start();

						simDelay = 10;

					}
					// <====
					// ==============================================


					try
					{
						// inform about sampling rate
						if (m_callback != null)
						{
							m_callback.onShimmerSimEvent( SHIM_MESSAGE_INIT, (long) (1000d / simDelay), 0, (char) 0 );
						}
					}
					catch (RemoteException e)
					{
						if (DeadObjectException.class.isInstance( e ))
						{
							// remove the dead callback
							m_callback = null;
							ShimmerSimService.this.stopSelf();
							// don't start
							return;
						}
					}


					simPlotIter = 0;
					simTime = 0;
					simVirtualTime = 0f;


					m_serviceThread = new HandlerThread( "ShimmerSimService", android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE );
					m_serviceThread.start();

					// Get the HandlerThread's Looper and use it for our Handler
					mServiceLooper = m_serviceThread.getLooper();
					mServiceHandler = new ServiceHandler( mServiceLooper );

					if (m_con.simMode != SHIM_MODE_BLUETOOTH)
					{
						// send initial message if not bluetooth mode
						mServiceHandler.sendEmptyMessage( SIMULATE_MSG );
					}
				}
			}
		} );
		m_loadingThread.start();


		// Thread.currentThread().setPriority( Thread.MAX_PRIORITY );
		// mHandler.sendEmptyMessage( SIMULATE_MSG );
	}


	/* (non-Javadoc)
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy ()
	{
		// Tell the user we stopped.
		Toast.makeText( this, "ShimmerSimService stopped.", Toast.LENGTH_SHORT ).show();

		if (m_con.shimDataSource != null)
		{
			m_con.disconnectBluetooth();
		}

		super.onDestroy();
	}


	/* (non-Javadoc)
	 * @see android.app.Service#onLowMemory()
	 */
	@Override
	public void onLowMemory ()
	{
		// === TODO Auto-generated method stub
		super.onLowMemory();
	}


	// Handler that receives messages from the thread
	private final class ServiceHandler extends Handler
	{
		public ServiceHandler (Looper looper)
		{
			super( looper );
		}


		@Override
		public void handleMessage (Message msg)
		{
			switch (msg.what)
			{
				case SIMULATE_MSG:

					try
					{
						// timer = System.nanoTime();

						if (m_callback == null)
							return;

						simTime += simDelay;


						// Send regular simulation events
						if (m_con.simMode == SHIM_MODE_LIVE)
						{
							if (m_callback != null)
								m_callback.onShimmerSimEvent(	SHIM_MESSAGE_SIM_EVENT,
																simTime,
																simSignal.ml2.values[ simPlotIter ],
																(char) simSignal.labels.get( simPlotIter, 0 ) );
						}
						else if (m_con.simMode == SHIM_MODE_INTERN)
						{
							if (m_callback != null)
								m_callback.onShimmerSimEvent(	SHIM_MESSAGE_SIM_EVENT,
																simTime,
																simPlot.values.values[ simPlotIter ],
																(char) 0 );
						}

						// Simulate entire record
						else if (m_con.simMode == SHIM_MODE_SIMULATION)
						{
							// non-real-time
							for (simPlotIter = 0; simPlotIter < m_con.simCount; ++simPlotIter)
							{
								if (m_callback == null)
									return;

								simVirtualTime += simDelay;
								simTime = (long) simVirtualTime;

								m_callback.onShimmerSimEvent(	SHIM_MESSAGE_SIM_EVENT,
																simTime,
																simSignal.ml2.values[ simPlotIter ],
																(char) simSignal.labels.get( simPlotIter, 0 ) );

							}
							m_callback.onShimmerSimEvent( SHIM_MESSAGE_SIM_END, 0, 0, (char) 0 );
							return;
						}
					}
					catch (RemoteException e)
					{
						if (DeadObjectException.class.isInstance( e ))
						{
							// remove the dead callback
							m_callback = null;
							if (mServiceLooper != null)
								mServiceLooper.quit();
							m_con = new ShimConnection();
							simSignal = null;
							ShimmerSimService.this.stopSelf();
						}
					}
					catch (NullPointerException e)
					{
						e.printStackTrace();
						m_callback = null;
						if (mServiceLooper != null)
							mServiceLooper.quit();
						m_con = new ShimConnection();
						simSignal = null;
						ShimmerSimService.this.stopSelf();
					}


					// increase sample counter
					++simPlotIter;


					// send next event message in simDelay milliseconds
					switch (m_con.simMode)
					{
						case SHIM_MODE_INTERN:
						case SHIM_MODE_LIVE:
							if (simPlotIter < m_con.simCount)
							{
								sendMessageDelayed( obtainMessage( SIMULATE_MSG ), (long) simDelay );
							}
							break;

						default:
							break;
					}

					break;

				default:
					super.handleMessage( msg );
			}
		}
	}


	private final IShimmerSimService.Stub	mBinder			= new IShimmerSimService.Stub() {

																public synchronized void registerCallback (
																		IShimmerSimServiceCallback cb) throws RemoteException
																{
																	m_callback = cb;
																	m_callback.onShimmerSimEvent(	SHIM_MESSAGE_INIT,
																									(long) (1000d / simDelay),
																									0,
																									(char) 0 );
																}


																public synchronized void unregisterCallback (
																		IShimmerSimServiceCallback cb) throws RemoteException
																{
																	m_callback = null;

																	if (mServiceLooper != null)
																		mServiceLooper.quit();

																	simPlotIter = 0;

																	m_con.disconnectBluetooth();

																	m_con = new ShimConnection();

																	simSignal = null;

																	ShimmerSimService.this.stopSelf();
																}
															};

	private static final int				SIMULATE_MSG	= 1;


	/* (non-Javadoc)
	 * @see de.lme.hearty.ShimmerDataSource.OnShimmerDataListener#onShimmerData(de.lme.hearty.ShimmerDataSource.ShimmerData)
	 */
	public void onShimmerData (ShimmerData data)
	{
		try
		{
			// Log.d( TAG, "Shimmer data event " + data );
			if (m_callback != null)
			{
				m_callback.onShimmerSimEvent( SHIM_MESSAGE_SIM_EVENT, data.timestamp, // timestamp
												data.accel_x, // value, TODO: change to sensor_1
												(char) 0 // label (acc. to MIT-BIH)
						);
			}
		}
		catch (RemoteException e)
		{
			if (DeadObjectException.class.isInstance( e ))
			{
				// remove the dead callback
				m_callback = null;
				if (mServiceLooper != null)
					mServiceLooper.quit();
			}
		}
		catch (NullPointerException e)
		{
			e.printStackTrace();
			m_callback = null;
			if (mServiceLooper != null)
				mServiceLooper.quit();
		}
	}

}
