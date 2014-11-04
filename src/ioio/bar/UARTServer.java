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

import ioio.lib.api.IOIO;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.util.Log;

public class UARTServer implements Runnable {
	
	private static final String _TAG = "UARTServer";
	
	interface OSCListener {
		public void onValueChanged(char oscControl, float value);
	}

	private OSCListener _listener;
	
	private Uart _uart;
	private IOIO _ioio;
	private InputStream _uartInput;
	int _inByte = 0; // in-coming serial byte
	int _inbyteIndex = 0; // in-coming bytes counter
	char _oscControl; // control in TouchOSC sending the message
	int[] _oscMsg = new int[11]; // buffer for incoming OSC packet

	public UARTServer(IOIO ioio, OSCListener listener) {		
		_listener = listener;
		_ioio = ioio;
	}

	@Override
	public void run() {
		try {
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			_uart = _ioio.openUart(5, 4, 9600, Uart.Parity.NONE, Uart.StopBits.ONE);
			_uartInput = _uart.getInputStream();
			while (true) {

				_inByte = _uartInput.read();

				// An OSC address pattern is a string beginning with the character forward slash '/'
				if (_inByte == 47) {
					_inbyteIndex = 0; // a new message received so set array index to 0
				}
				// ASCII values for T = 0x54 | S = 0x57 | B = 42
				if (_inbyteIndex == 0 && (_inByte == 0x54 || _inByte == 0x53 || _inByte == 0x42)) {
					switch (_inByte) {
					case (0x54): // Throttle
						_oscControl = 'T';
						break;
					case (0x53): // Steering
						_oscControl = 'S';
						break;
					case (0x42): // Button
						_oscControl = 'B';
						break;
					default:
						_oscControl = ' ';
					}
					_inbyteIndex++;
				}
				if (_inbyteIndex < 12 && _inbyteIndex > 0) { // it's either time to start or finish reading the message
					_oscMsg[_inbyteIndex - 1] = _inByte; // add the byte to the array
					_inbyteIndex++;
				}
				if (_inbyteIndex == 11) { // end of the OSC message
					_inbyteIndex = -1; // set the pointer to -1 so we stop processing

					byte[] byte_array = new byte[4];
					byte_array[0] = (byte) _oscMsg[10]; // reverse bytes order to decode message
					byte_array[1] = (byte) _oscMsg[9];
					byte_array[2] = (byte) _oscMsg[8];
					byte_array[3] = (byte) _oscMsg[7];
					ByteBuffer byteBuffer = ByteBuffer.allocate(byte_array.length);
					byteBuffer.put(byte_array);

					float value = getOSCValue(byteBuffer.array());
					
					_listener.onValueChanged(_oscControl, value);
				}
			}
		} catch (IOException e) {
			Log.e(_TAG, e.getMessage());
			abort();
		} catch (ConnectionLostException e) {
			Log.e(_TAG, e.getMessage());
			abort();
		}
	}
	
	void abort() {
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
	
	private float getOSCValue(byte[] byte_array_4) {
		int ret = 0;
		for (int i = 0; i < 4; i++) {
			int b = (int) byte_array_4[i];
			if (i < 3 && b < 0) {
				b = 256 + b;
			}
			ret += b << (i * 8);
		}
		return Float.intBitsToFloat(ret);
	}
}
