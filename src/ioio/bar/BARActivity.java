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

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class BARActivity extends IOIOActivity implements SensorEventListener {

	public static final float STEPS_FREQ = 62500;

	// ---
	// Declares which types of channels we are going to use and which pins they should be mapped to. The order of the channels
	// in this array is important because it is used to define cues for those channels in the Sequencer.ChannelCue[] array.
	// ---
	private Sequencer.ChannelConfig[] channel_config = { 
			new Sequencer.ChannelConfigFmSpeed(Sequencer.Clock.CLK_62K5, 2, new DigitalOutput.Spec(7)),  // LEFT STEP
			new Sequencer.ChannelConfigBinary(false, false, new DigitalOutput.Spec(18)), 				 // LEFT DIR
			new Sequencer.ChannelConfigFmSpeed(Sequencer.Clock.CLK_62K5, 2, new DigitalOutput.Spec(13)), // RIGHT STEP
			new Sequencer.ChannelConfigBinary(false, false, new DigitalOutput.Spec(14))			         // RIGHT DIR 
	}; 

	// ---
	// FM (frequency modulation) speed channels are useful for driving stepper motors in speed control mode (i.e. how fast it's moving).
	// ---
	private Sequencer.ChannelCueFmSpeed left_steps = new Sequencer.ChannelCueFmSpeed();
	private Sequencer.ChannelCueFmSpeed right_steps = new Sequencer.ChannelCueFmSpeed();
	
	// ---
	// A cue binary channel is to drive the pin "low" "high".
	// ---
	private Sequencer.ChannelCueBinary left_dir = new Sequencer.ChannelCueBinary();
	private Sequencer.ChannelCueBinary right_dir = new Sequencer.ChannelCueBinary();
	
	// ---
	// The order and type of elements in this array must match the Sequencer.ChannelConfig[] array.
	// ---
	private Sequencer.ChannelCue[] channel_cue = { left_steps, left_dir, right_steps, right_dir };
	
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
	
	private int[] left_pins = { 3, 24, 6 };
	private int[] right_pins = { 10, 11, 12 };

	private PowerManager.WakeLock wake_lock;
	private SensorManager sensor_manager;
	private Sensor rotation_vector_sensor;

	GestureDetector gesture_detector;
	SharedPreferences shared_preferences;
	static int offset_index = 2;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		PreferenceManager.setDefaultValues(this, R.xml.settings, false);
		shared_preferences = PreferenceManager.getDefaultSharedPreferences(this);
		offset_index = Integer.parseInt(shared_preferences.getString(getString(R.string.color_key), "0"));
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wake_lock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "BAR");
		sensor_manager = (SensorManager) getSystemService(SENSOR_SERVICE);
		rotation_vector_sensor = sensor_manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		last_timestamp = 0;
		
		gesture_detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public void onLongPress(MotionEvent e) {
				startActivityForResult(new Intent(getApplicationContext(), SettingsActivity.class), 0);
			}
		});
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
		return gesture_detector.onTouchEvent(event);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		wake_lock.acquire();
	}

	@Override
	protected void onResume() {
		super.onResume();
		sensor_manager.registerListener(this, rotation_vector_sensor, SensorManager.SENSOR_DELAY_GAME);
		last_timestamp = 0;
		last_error = 0;
		hideNavigationBar();
	}
	
	@Override
	protected void onStop() {
		wake_lock.release();
		sensor_manager.unregisterListener(this);
		super.onStop();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		offset_index = Integer.parseInt(shared_preferences.getString(getString(R.string.color_key), "2"));
	}

	private class DRV8834 {
		private final Sequencer.ChannelCueBinary dir;
		private final Sequencer.ChannelCueFmSpeed step;
		private final DigitalOutput sleep;

		public DRV8834(IOIO ioio, int[] pins, Sequencer.ChannelCueFmSpeed step, Sequencer.ChannelCueBinary dir) throws ConnectionLostException {
			this.dir = dir;
			this.step = step;
			step.period = 0;

			// NOTE: The default state of the ENBL pin is to enable the driver, so this pin can be left disconnected.
			
			ioio.openDigitalOutput(pins[0], true); // M0
			ioio.openDigitalOutput(pins[1], true); // M1
			
			sleep = ioio.openDigitalOutput(pins[2], false);
		}

		public void setEnable(boolean en) throws ConnectionLostException {
			sleep.write(en);
		}
		
		public void setSpeed(float speed) throws ConnectionLostException {
			dir.value = (speed > 0);
			speed = Math.abs(speed) * 10000;
			if (speed < 40) speed = 40;
			if (speed > 3600) speed = 3600;
			step.period = Math.round(STEPS_FREQ / speed);
		}
	}
	
	
	class BalancerLooper extends BaseIOIOLooper {
		private final DRV8834[] motors = new DRV8834[2];
		private static final int SLEEP_MS = 2;
		private Sequencer sequencer;

		@Override
		public void setup() throws ConnectionLostException {
			last_timestamp = 0;	
			integrated_error = 0;
			motors[0] = new DRV8834(ioio_, left_pins, left_steps, left_dir);
			motors[1] = new DRV8834(ioio_, right_pins, right_steps, right_dir);
			sequencer = ioio_.openSequencer(channel_config);
		}

		@Override
		public void loop() throws ConnectionLostException {
			try {
				float speed = 0;
			    if (tilt_angle < BALANCE_LIMIT && tilt_angle > -BALANCE_LIMIT) {
			    	speed = control_output;
			        motors[0].setEnable(true);
					motors[1].setEnable(true);
			    } else  {	
			    	motors[0].setEnable(false);
					motors[1].setEnable(false);
					last_error = 0;
					sequencer.manualStop();
			    }
			    motors[0].setSpeed(-speed);
				motors[1].setSpeed(speed);
				sequencer.manualStart(channel_cue);
				
				Thread.sleep(SLEEP_MS);
			} catch (InterruptedException e) {
				ioio_.disconnect();
			}
		}

		@Override
		public void disconnected() {
			sequencer.close();
		}
	}

	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) {
		return new BalancerLooper();
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}
	
	private float[] offset = { 
			0.0872664626f, // [0] 5º
			0.0610865238f, // [1] 3.5º
			0.0523598776f, // [2] 3º
			0.0436332313f, // [3] 2.5º
			0.0174532925f, // [4] 1º
	};
	
	private static final float BALANCE_LIMIT = 0.698131701f;  //40 degrees
	
	private float kP = 1.7f;
	private float kI = 12.2f;
	private float kD = 0.5f;
	
	private volatile float tilt_angle = 0.0f;
	private long last_timestamp = 0;	
	private float integrated_error = 0;
	private float last_error = 0.0f;
	private volatile float control_output = 0.0f;
			
	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			if (last_timestamp != 0) {
				long dT = event.timestamp - last_timestamp;
				float[] quaternion = new float[4];

				SensorManager.getQuaternionFromVector(quaternion, event.values);
		        
				// Roll-Tilt-Angle (landscape mode - 90º degree raised up)
		        tilt_angle = (float)((Math.asin(quaternion[0] * quaternion[0] - quaternion[1] * quaternion[1] - quaternion[2] * quaternion[2] + 
		        		quaternion[3] * quaternion[3]) - offset[offset_index]));
		        
		        control_output = pidController(-1 * (tilt_angle * 0.99f), tilt_angle, kP, kI, kD, dT);
			}
			last_timestamp = event.timestamp;
		}
	}
	
	private float pidController(float setpoint, float input, float kP, float kI, float kD, float dT) {
		float error = setpoint - input;
		integrated_error += 0.99f * error; // low-pass IIR filter
		integrated_error = constrain(integrated_error, -1, 1);
		float derivative = error - last_error;
		last_error = error;
		return (kP * error + kI * (integrated_error * dT * 1e-9f) + kD * (derivative / dT * 1e-9f));
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