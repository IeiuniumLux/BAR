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

package ioio.bar;

import ioio.bar.UARTServer.OSCListener;
import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.Sequencer;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class BARActivity extends IOIOActivity implements SensorEventListener {

	private static final String _TAG = "BARActivity";
	private static final float DEGREES_RADIANS = 0.0174532925f; // Degrees to Radians
	private static final float BALANCE_LIMIT = 0.785398163f;    // Shutdown motors @ 45º

	private PowerManager.WakeLock _wakeLock;
	private SensorManager _sensorManager;
	private Sensor _rotationVectorSensor;
	private GestureDetector _gestureDetector;
	private SharedPreferences _sharedPreferences;
	
	private float _offset = 0.0f;
	private float _throttle = 0.0f;
	private float _steering = 0.0f;
	private float _proximity = 0.0f;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		PreferenceManager.setDefaultValues(this, R.xml.settings, false);
		_sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		_offset = _sharedPreferences.getFloat(getString(R.string.degrees_key), 0.0f) * DEGREES_RADIANS;

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		_wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "BAR");
		_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		_rotationVectorSensor = _sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		_lastTimestamp = 0;

		_gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public void onLongPress(MotionEvent e) {
				startActivityForResult(new Intent(getApplicationContext(), SettingsActivity.class), 0);
			}
		});
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
		return _gestureDetector.onTouchEvent(event);
	}

	@Override
	protected void onStart() {
		super.onStart();
		_wakeLock.acquire();
	}

	@Override
	protected void onResume() {
		super.onResume();
		_sensorManager.registerListener(this, _rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
		_lastTimestamp = 0L;
		_lastError = 0.0f;
		_equilibriumErrorSum = 0.0f;
		_throttle = 0.0f;
		_steering = 0.0f;
		_proximity = 0.0f;
		hideNavigationBar();
	}

	@Override
	protected void onStop() {
		_wakeLock.release();
		_sensorManager.unregisterListener(this);
		super.onStop();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		// _offsetIndex = Integer.parseInt(_sharedPreferences.getString(getString(R.string.degrees_key), "2"));
		_offset = _sharedPreferences.getFloat(getString(R.string.degrees_key), 0.0f) * DEGREES_RADIANS;
	}
	

	class BalancerLooper extends BaseIOIOLooper implements OSCListener {

		// ---
		// Declares which types of channels we are going to use and which pins they should be mapped to. The order of the channels
		// in this array is important because it is used to define cues for those channels in the Sequencer.ChannelCue[] array.
		// ---
		private Sequencer.ChannelConfig[] _channelConfig = { 
				new Sequencer.ChannelConfigFmSpeed(Sequencer.Clock.CLK_62K5, 2, new DigitalOutput.Spec(7)), 	// LEFT STEP
				new Sequencer.ChannelConfigBinary(false, false, new DigitalOutput.Spec(18)), 					// LEFT DIR
				new Sequencer.ChannelConfigFmSpeed(Sequencer.Clock.CLK_62K5, 2, new DigitalOutput.Spec(13)), 	// RIGHT STEP
				new Sequencer.ChannelConfigBinary(false, false, new DigitalOutput.Spec(14)) 					// RIGHT DIR
		};

		// ---
		// FM (frequency modulation) speed channels are useful for driving stepper motors in speed control mode (i.e. how fast it's moving).
		// ---
		private Sequencer.ChannelCueFmSpeed _leftSteps = new Sequencer.ChannelCueFmSpeed();
		private Sequencer.ChannelCueFmSpeed _rightSteps = new Sequencer.ChannelCueFmSpeed();

		// ---
		// A cue binary channel is to drive the pin "low" "high".
		// ---
		private Sequencer.ChannelCueBinary _leftDir = new Sequencer.ChannelCueBinary();
		private Sequencer.ChannelCueBinary _rightDir = new Sequencer.ChannelCueBinary();

		// ---
		// The order and type of elements in this array must match the Sequencer.ChannelConfig[] array.
		// ---
		private Sequencer.ChannelCue[] _channelCue = { _leftSteps, _leftDir, _rightSteps, _rightDir };

		private int[] _leftPins = { 3, 24, 6 };
		private int[] _rightPins = { 10, 11, 12 };

		private final DRV8834[] _motors = new DRV8834[2];
		private static final int SLEEP_MS = 2;
		private Sequencer _sequencer;

		UARTServer _uart;
		boolean oneTime = true;

		private AnalogInput _IRSensor;
		private int truePulseCounter = 0;

		@Override
		public void setup() throws ConnectionLostException {
			_lastTimestamp = 0;
			_equilibriumErrorSum = 0;
			_motors[0] = new DRV8834(ioio_, _leftPins, _leftSteps, _leftDir);
			_motors[1] = new DRV8834(ioio_, _rightPins, _rightSteps, _rightDir);
			_sequencer = ioio_.openSequencer(_channelConfig);

			_uart = new UARTServer(ioio_, this);
			new Thread(_uart).start();

			_IRSensor = ioio_.openAnalogInput(44); // A/D 4 shield
		}

		@Override
		public void loop() throws ConnectionLostException, InterruptedException {

			float sensorValue = (_IRSensor.getVoltage() > 1.1) ? _IRSensor.getVoltage() : 0.0f;

			if (sensorValue > 0.0f && truePulseCounter < 7) {
				truePulseCounter++;
			} else if (sensorValue == 0.0f && truePulseCounter > 0) {
				truePulseCounter--;
			}

			// _proximity = (truePulseCounter > 6) ? sensorValue * 0.018f : 0.0f;

			_proximity = (truePulseCounter > 6) ? proximityDisplacement(sensorValue, 1.1f, 0.0065f, 0.03f) : 0.0f;

			float speed = 0;
			if (_tiltAngle < BALANCE_LIMIT && _tiltAngle > -BALANCE_LIMIT) {
				speed = _controlOutput;
				_motors[0].setEnable(true);
				_motors[1].setEnable(true);
			} else {
				_motors[0].setEnable(false);
				_motors[1].setEnable(false);
				_lastError = 0.0f;
				_throttle = 0.0f;
				_steering = 0.0f;
				_proximity = 0.0f;
				_sequencer.manualStop();
			}
			_motors[0].setSpeed(-speed + _steering);
			_motors[1].setSpeed(speed + _steering);
			_sequencer.manualStart(_channelCue);

			Thread.sleep(SLEEP_MS);
		}

		@Override
		public void disconnected() {
			_sequencer.close();
			Log.e(_TAG, "IOIO disconnected");
		}

		@Override
		public void onValueChanged(char oscControl, float value) {
			switch (oscControl) {
			case ('T'): // Throttle
				_throttle = value;
				break;
			case ('S'): // Steering
				_steering = value;
				break;
			case ('B'): // Button ON/OFF
				break;
			}
		}
	}

	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) {
		return new BalancerLooper();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	private float _kP = 1.49f;
	private float _kI = 13.9f;
	private float _kD = 0.35f;

	private volatile float _tiltAngle = 0.0f;
	private long _lastTimestamp = 0L;
	private float _equilibriumErrorSum = 0.0f;
	private float _lastError = 0.0f;
	private volatile float _controlOutput = 0.0f;

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			if (_lastTimestamp != 0) {
				long dT = event.timestamp - _lastTimestamp;
				float[] quaternion = new float[4];

				SensorManager.getQuaternionFromVector(quaternion, event.values);

				// Roll-Tilt-Angle (landscape mode - 90º degree raised up)
				_tiltAngle = (float) ((Math.asin(quaternion[0] * quaternion[0] - quaternion[1] * quaternion[1] - quaternion[2] * quaternion[2] + 
						quaternion[3] * quaternion[3]) - (_offset + _throttle + _proximity)));

				_controlOutput = equilibriumPID(-1 * (_tiltAngle - (_throttle * 0.2f)), _tiltAngle, _kP, _kI, _kD, dT);
			}
			_lastTimestamp = event.timestamp;
		}
	}

	private float equilibriumPID(float setpoint, float input, float kP, float kI, float kD, float dT) {
		float error = setpoint - input;
		_equilibriumErrorSum += 0.99f * error; // low-pass IIR filter
		_equilibriumErrorSum = constrain(_equilibriumErrorSum, -1, 1);
		float derivative = error - _lastError;
		_lastError = error;
		return (kP * error + kI * (_equilibriumErrorSum * dT * 1e-9f) + kD * (derivative / dT * 1e-9f));
	}

	private float proximityDisplacement(float current, float previous, float kP, float kI) {
		float displacement = current - previous;
		return (kP * current + kI * displacement * 0.999f);
	}

	private void hideNavigationBar() {
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
	}

	private float constrain(final float value, final float min, final float max) {
		if (value < min)
			return min;
		if (value > max)
			return max;
		return value;
	}
}