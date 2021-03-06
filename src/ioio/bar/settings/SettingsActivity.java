/**
 * Released under the MIT License (MIT).
 *
 * Copyright (c) 2014 Al Bencomo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ioio.bar.settings;

import ioio.bar.R;

import java.util.Map;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;


/**
 * Since we're developing for Android 3.0 (API level 11) and higher, we use a PreferenceFragment to display
 * list of Preference objects. PreferenceFragment can be added to any activity so you don't need to use
 * PreferenceActivity. Fragments provide a more flexible architecture so use PreferenceFragment to control
 * the display of settings instead of PreferenceActivity when possible. Your implementation of PreferenceFragment
 * can be as simple as defining the onCreate() method to load a preferences file with addPreferencesFromResource().
 * 
 * Note: A PreferenceFragment doesn't have a its own Context object. If you need a Context object, you can call
 * getActivity(). However, be careful to call getActivity() only when the fragment is attached to an activity.
 * When the fragment is not yet attached, or was detached during the end of its lifecycle, getActivity() will return null.
 * 
 * @author abencomo
 *
 */
public class SettingsActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Display the fragment as the main content.
		getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
	}

	public static class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.settings);
		}

		@Override
		public void onResume() {
			super.onResume();

			// Set up initial values for all list preferences
			Map<String, ?> sharedPreferencesMap = getPreferenceScreen().getSharedPreferences().getAll();
			Preference pref;

			for (Map.Entry<String, ?> entry : sharedPreferencesMap.entrySet()) {
				pref = findPreference(entry.getKey());
				if (pref instanceof ListPreference) {
					ListPreference listPref = (ListPreference) pref;
					pref.setSummary(listPref.getEntry());
				} else if (pref instanceof EditTextPreference) {
					EditTextPreference editTextPref = (EditTextPreference) pref;
					pref.setSummary(editTextPref.getText());
				}
			}

			// Set up a listener whenever a key changes
			getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause() {
			super.onPause();
			// Set up a listener whenever a key changes
			getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			Preference pref = findPreference(key);

			if (pref instanceof ListPreference) {
				ListPreference listPref = (ListPreference) pref;
				pref.setSummary(listPref.getEntry());
			} else if (pref instanceof EditTextPreference) {
				EditTextPreference editTextPref = (EditTextPreference) pref;
				pref.setSummary(editTextPref.getText());
			}

			/** Standard activity result: operation succeeded. */
			getActivity().setResult(Activity.RESULT_OK);
		}
	}
}
