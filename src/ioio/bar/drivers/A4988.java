/*
 * Copyright 2014 Ytai Ben-Tsvi. All rights reserved.
 *
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL ARSHAN POURSOHI OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied.
 */
package ioio.bar.drivers;

import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.Sequencer;
import ioio.lib.api.exception.ConnectionLostException;

public class A4988 {
	
	public static final float STEPS_FREQ = 62500;
	
	private final Sequencer.ChannelCueBinary dir_;
	private final Sequencer.ChannelCueFmSpeed stp_;
	private final DigitalOutput slp_;

	public A4988(IOIO ioio, int startPin, Sequencer.ChannelCueFmSpeed step,
			Sequencer.ChannelCueBinary dir) throws ConnectionLostException {
		dir_ = dir;
		stp_ = step;
		stp_.period = 0;
		slp_ = ioio.openDigitalOutput(startPin + 2, false);
		ioio.openDigitalOutput(startPin + 3, true);  // rst
		ioio.openDigitalOutput(startPin + 4, true);  // ms3
		ioio.openDigitalOutput(startPin + 5, true);  // ms2
		ioio.openDigitalOutput(startPin + 6, true);  // ms1
		ioio.openDigitalOutput(startPin + 7, false); // en
	}

	public void setEnable(boolean en) throws ConnectionLostException {
		slp_.write(en);
	}

	public void setSpeed(float speed) throws ConnectionLostException {
		dir_.value = (speed > 0);
		speed = Math.abs(speed) * 10000;
		if (speed < 10) speed = 10;
		if (speed > STEPS_FREQ / 3) speed = STEPS_FREQ / 3;
		stp_.period = Math.round(STEPS_FREQ / speed);
	}
}
