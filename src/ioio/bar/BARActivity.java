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
	
	private PowerManager.WakeLock _wakeLock;
	private SensorManager _sensorManager;
	private Sensor _rotationVectorSensor;

	GestureDetector _gestureDetector;
	SharedPreferences _sharedPreferences;
	static int _offsetIndex = 2;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		PreferenceManager.setDefaultValues(this, R.xml.settings, false);
		_sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		_offsetIndex = Integer.parseInt(_sharedPreferences.getString(getString(R.string.color_key), "0"));
		
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
		_lastTimestamp = 0;
		_lastError = 0;
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
		_offsetIndex = Integer.parseInt(_sharedPreferences.getString(getString(R.string.color_key), "2"));
	}
	
	
	class BalancerLooper extends BaseIOIOLooper implements OSCListener {
		
		// ---
		// Declares which types of channels we are going to use and which pins they should be mapped to. The order of the channels
		// in this array is important because it is used to define cues for those channels in the Sequencer.ChannelCue[] array.
		// ---
		private Sequencer.ChannelConfig[] _channelConfig = { 
				new Sequencer.ChannelConfigFmSpeed(Sequencer.Clock.CLK_62K5, 2, new DigitalOutput.Spec(7)),  // LEFT STEP
				new Sequencer.ChannelConfigBinary(false, false, new DigitalOutput.Spec(18)), 				 // LEFT DIR
				new Sequencer.ChannelConfigFmSpeed(Sequencer.Clock.CLK_62K5, 2, new DigitalOutput.Spec(13)), // RIGHT STEP
				new Sequencer.ChannelConfigBinary(false, false, new DigitalOutput.Spec(14))			         // RIGHT DIR 
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
		
//		float _throttle = 0.0f;
		
		UARTServer _uart;
		boolean oneTime = true;
		
		private AnalogInput _IRSensor;

		@Override
		public void setup() throws ConnectionLostException {
			_lastTimestamp = 0;	
			_integratedError = 0;
			_motors[0] = new DRV8834(ioio_, _leftPins, _leftSteps, _leftDir);
			_motors[1] = new DRV8834(ioio_, _rightPins, _rightSteps, _rightDir);
			_sequencer = ioio_.openSequencer(_channelConfig);
			
			_uart = new UARTServer(ioio_, this);
			new Thread(_uart).start();
			
			_IRSensor = ioio_.openAnalogInput(44); // A/D 4 shield
		}

		@Override
		public void loop() throws ConnectionLostException, InterruptedException {
				
			Log.e("IR", String.valueOf(_IRSensor.getVoltage()));
			
			
			float speed = 0;
		    if (_tiltAngle < BALANCE_LIMIT && _tiltAngle > -BALANCE_LIMIT) {
		    	speed = _controlOutput;
//			    	Log.e(_TAG, String.valueOf(speed));
		        _motors[0].setEnable(true);
				_motors[1].setEnable(true);
		    } else  {	
		    	_motors[0].setEnable(false);
				_motors[1].setEnable(false);
				_lastError = 0;
				_sequencer.manualStop();
		    }
		    _motors[0].setSpeed(-speed);
			_motors[1].setSpeed(speed);
			_sequencer.manualStart(_channelCue);
			
			Thread.sleep(SLEEP_MS);
		}

		@Override
		public void disconnected() {
			_sequencer.close();			
			_uart.abort();
			Log.e(_TAG, "IOIO disconnected");
		}
		
		@Override
		public void onLine(float throttle) {
			Log.e("onLine", String.valueOf(throttle));
		}
	}

	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) {
		return new BalancerLooper();
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}
	
	private float[] _offset = { 
			0.0741764932f, // [0] 4.25º
			0.0174532925f, // [1] 1º
			0.0130899694f, // [2] 0.75º
			0.00872664626f,// [3] 0.5º
			0.00436332313f // [4] 0.25º
	};
	
	private static final float BALANCE_LIMIT = 0.872664626f;  // Shutdown motors @ 50º 
	
	private float _kP = 1.49f;
	private float _kI = 13.9f;
	private float _kD = 0.35f;
	
	private volatile float _tiltAngle = 0.0f;
	private long _lastTimestamp = 0;	
	private float _integratedError = 0;
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
		        _tiltAngle = (float)((Math.asin(quaternion[0] * quaternion[0] - quaternion[1] * quaternion[1] - quaternion[2] * quaternion[2] + 
		        		quaternion[3] * quaternion[3]) - _offset[_offsetIndex]));
		        
		        _controlOutput = pidController(-1 * (_tiltAngle * 0.99f), _tiltAngle, _kP, _kI, _kD, dT);
			}
			_lastTimestamp = event.timestamp;
		}
	}
	
	private float pidController(float setpoint, float input, float kP, float kI, float kD, float dT) {
		float error = setpoint - input;
		_integratedError += 0.99f * error; // low-pass IIR filter
		_integratedError = constrain(_integratedError, -1, 1);
		float derivative = error - _lastError;
		_lastError = error;
		return (kP * error + kI * (_integratedError * dT * 1e-9f) + kD * (derivative / dT * 1e-9f));
	}

	private void hideNavigationBar() {
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
	}
	
	private float constrain(final float value, final float min, final float max) {
		if (value < min) return min;
		if (value > max) return max;
		return value;
	}
}