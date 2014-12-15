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
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {

	private SeekBar _seekBar;
	private TextView _valueText;

	private float _maxValue;
	private float _minValue;
	private float _defaultValue;
	private String _units;

	private float _currentValue;

	public SeekBarPreference(Context context, AttributeSet attrs) {
		super(context, attrs);

		TypedArray array_of_values  = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SeekBarPreference, 0, 0);

		try {
			// Read parameters from the attrs.xml file
			_maxValue = array_of_values.getFloat(R.styleable.SeekBarPreference_maxValue, 6);
			_minValue = array_of_values.getFloat(R.styleable.SeekBarPreference_minValue, 0);
			_defaultValue = array_of_values.getFloat(R.styleable.SeekBarPreference_degreesValue, 0);
			_units = array_of_values.getString(R.styleable.SeekBarPreference_units);
		} finally {
			array_of_values.recycle();
		}
	}

	@Override
	protected View onCreateDialogView() {

		_currentValue = getPersistedFloat(_defaultValue);

		LayoutInflater layoutInflater = LayoutInflater.from(getContext());
		View view = layoutInflater.inflate(R.layout.seekbarpreference, new LinearLayout(getContext()), false);

		_seekBar = (SeekBar) view.findViewById(R.id.seekbar);
		_seekBar.setMax((int) (_maxValue - _minValue) * 100);
		_seekBar.setProgress((int) (_currentValue - _minValue) * 100);
		_seekBar.setOnSeekBarChangeListener(this);

		_valueText = (TextView) view.findViewById(R.id.valueText);
		_valueText.setText(String.valueOf(_currentValue + _minValue) + (_units == null ? "" : _units));

		return view;
	}

	@Override
	public void onProgressChanged(SeekBar seek, int newValue, boolean fromTouch) {
		_currentValue = (newValue + _minValue) / 100.0f;
		_valueText.setText(String.valueOf(_currentValue + _minValue) + (_units == null ? "" : _units));
		callChangeListener(_currentValue);
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		// Return if change was cancelled
		if (!positiveResult) {
			return;
		}

		// Persist current value if needed
		if (shouldPersist()) {
			persistFloat(_currentValue);
		}

		// Notify activity about changes (to update preference summary line)
		notifyChanged();
	}

	@Override
	public CharSequence getSummary() {
		return String.valueOf(getPersistedFloat(_defaultValue)) + (_units == null ? "" : _units);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seek) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seek) {
	}
}