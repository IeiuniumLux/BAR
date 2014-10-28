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

public class DRV8834 {
	
	public static final float STEPS_FREQ = 62500;

	
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
