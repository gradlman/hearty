/**
 * 
 */
package de.lme.hearty;


import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;




/**
 * @author sistgrad
 * 
 */
public class PrefActivity extends PreferenceActivity implements Preference.OnPreferenceChangeListener,
		Preference.OnPreferenceClickListener
{
	@Override
	protected void onCreate (Bundle savedInstanceState)
	{
		super.onCreate( savedInstanceState );

		addPreferencesFromResource( R.xml.pref_plots );


		// pref.setOnPreferenceClickListener( this );
		// pref.setDefaultValue( defaultValue )
	}


	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume ()
	{
		Preference pref = (Preference) findPreference( "pk_bt" );
		pref.setOnPreferenceClickListener( new OnPreferenceClickListener() {

			public boolean onPreferenceClick (Preference preference)
			{
				PrefActivity.this.startActivityForResult( new Intent( getApplicationContext(), DeviceListActivity.class ), 22 );
				return false;
			}
		} );
		super.onResume();
	}


	/* (non-Javadoc)
	 * @see android.preference.PreferenceActivity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult (int requestCode, int resultCode, Intent data)
	{
		if (requestCode == 22)
		{
			if (resultCode == RESULT_OK)
			{
				String addr = data.getStringExtra( DeviceListActivity.EXTRA_DEVICE_ADDRESS );
				if (addr != null && addr.length() > 1)
				{
					Preference pref = (Preference) findPreference( "pk_bt" );
					pref.setSummary( addr );
					PreferenceManager.getDefaultSharedPreferences( this ).edit().putString( "pk_source", "blue" )
							.putString( "pk_bt_source", addr ).commit();
				}
			}
		}
		super.onActivityResult( requestCode, resultCode, data );
	}


	@Override
	protected void onPause ()
	{
		super.onPause();
	}


	/* (non-Javadoc)
	* @see android.preference.Preference.OnPreferenceChangeListener#onPreferenceChange(android.preference.Preference, java.lang.Object)
	*/
	public boolean onPreferenceChange (Preference arg0, Object arg1)
	{
		// TODO Auto-generated method stub
		return false;
	}


	/* (non-Javadoc)
	* @see android.app.Activity#onCreateDialog(int)
	*/
	@Override
	protected Dialog onCreateDialog (int id)
	{
		Dialog dialog = null;

		return dialog;
	}


	/* (non-Javadoc)
	* @see android.preference.Preference.OnPreferenceClickListener#onPreferenceClick(android.preference.Preference)
	*/
	public boolean onPreferenceClick (Preference preference)
	{
		String key = preference.getKey();
		if (key != null && key.equals( "pk_xx" ))
		{
			// Space.startActivity( Space.appContext, Space.ACTION_MONITOR_CALIBRATE, null, 0 );
			return true;
		}

		return false;
	}
}
