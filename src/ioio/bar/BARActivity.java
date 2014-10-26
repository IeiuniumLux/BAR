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

import java.io.IOException;
import java.io.InputStream;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.Sequencer;
import ioio.lib.api.Uart;
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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class BARActivity extends IOIOActivity implements SensorEventListener {

	public static final float STEPS_FREQ = 62500;

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
	
	/*
	M0     -> IOIO:  3 / Shield: 3
	M1     -> IOIO: 24 / Shield: 4
	SLEEP  -> IOIO:  6 / Shield: 5
	STEP   -> IOIO:  7 / Shield: 6
	DIR    -> IOIO: 18 / Shield: 7
	
	M0     -> IOIO: 10  / Shield: 9
	M1     -> IOIO: 11 / Shield: 10
	SLEEP  -> IOIO: 12 / Shield: 11
	STEP   -> IOIO: 13 / Shield: 12
	DIR    -> IOIO: 14 / Shield: 13
	 */
	
	private int[] _leftPins = { 3, 24, 6 };
	private int[] _rightPins = { 10, 11, 12 };

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

	private class DRV8834 {
		private final Sequencer.ChannelCueBinary _dir;
		private final Sequencer.ChannelCueFmSpeed _step;
		private final DigitalOutput _sleep;

		public DRV8834(IOIO ioio, int[] pins, Sequencer.ChannelCueFmSpeed step, Sequencer.ChannelCueBinary dir) throws ConnectionLostException {
			this._dir = dir;
			this._step = step;
			step.period = 0;

			// NOTE: The default state of the ENBL pin is to enable the driver, so this pin can be left disconnected.
			
			ioio.openDigitalOutput(pins[0], true); // M0
			ioio.openDigitalOutput(pins[1], true); // M1
			
			_sleep = ioio.openDigitalOutput(pins[2], false);
		}

		public void setEnable(boolean en) throws ConnectionLostException {
			_sleep.write(en);
		}
		
		public void setSpeed(float speed) throws ConnectionLostException {
			_dir.value = (speed > 0);
			speed = Math.abs(speed) * 10000;
			if (speed < 50) speed = 50;
			if (speed > 3620) speed = 3620;
			_step.period = Math.round(STEPS_FREQ / speed);
		}
	}
	
	
	class BalancerLooper extends BaseIOIOLooper {
		private final DRV8834[] _motors = new DRV8834[2];
		private static final int SLEEP_MS = 2;
		private Sequencer _sequencer;
		
		private Uart _uart;
		private InputStream _uartInput;
		
		int _inByte = 0;      // in-comming serial byte
		int _inbyteIndex = 0; // in-comming bytes counter
		char _oscControl;     // control in TouchOSC sending the message
		int[] _oscMsg = new int[11]; // buffer for incoming OSC packet

		@Override
		public void setup() throws ConnectionLostException {
			_lastTimestamp = 0;	
			_integratedError = 0;
			_motors[0] = new DRV8834(ioio_, _leftPins, _leftSteps, _leftDir);
			_motors[1] = new DRV8834(ioio_, _rightPins, _rightSteps, _rightDir);
			_sequencer = ioio_.openSequencer(_channelConfig);
			
			_uart = ioio_.openUart(5, 4, 9600, Uart.Parity.NONE, Uart.StopBits.ONE);
			_uartInput = _uart.getInputStream();
		}

		@Override
		public void loop() throws ConnectionLostException {
			try {
				_inByte = _uartInput.read();
				
				float speed = 0;
			    if (_tiltAngle < BALANCE_LIMIT && _tiltAngle > -BALANCE_LIMIT) {
			    	speed = _controlOutput;
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
			} catch (InterruptedException e) {
				ioio_.disconnect();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void disconnected() {
			_sequencer.close();
			try {
				if (_uartInput != null)
					_uartInput.close();
				if (_uart != null)
					_uart.close();
			} catch (IOException e) {
				// Nothing to do at this point!
			} finally {
				_uartInput = null;
				_uart = null;
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
	
	private float[] _offset = { 
			0.0829031395f, // [0] 4.75º
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