/**
 * 
 */
package de.lme.hearty;


import android.app.Dialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;




/**
 * @author sistgrad
 * 
 */
public class AttachFeatureActivity extends PreferenceActivity implements Preference.OnPreferenceChangeListener,
		Preference.OnPreferenceClickListener
{
	@Override
	protected void onCreate (Bundle savedInstanceState)
	{
		super.onCreate( savedInstanceState );

		addPreferencesFromResource( R.xml.pref_attfeat );

		Preference pref = (Preference) findPreference( "pk_af_rr" );
		pref.setOnPreferenceClickListener( this );
		// pref.setDefaultValue( defaultValue )
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
		if (key != null && key.equals( "pk_g_calibrate" ))
		{
			// Space.startActivity( Space.appContext, Space.ACTION_MONITOR_CALIBRATE, null, 0 );
			return true;
		}

		return false;
	}
}
