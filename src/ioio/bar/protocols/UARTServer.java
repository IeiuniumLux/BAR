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
package ioio.bar.protocols;

import ioio.lib.api.IOIO;
import ioio.lib.api.Uart;
import ioio.lib.api.exception.ConnectionLostException;

import java.io.InputStream;

import android.util.Log;

public class UARTServer implements Runnable {
	
	public interface UARTListener {
		public void onInputStreamReceived(InputStream inputStream);
	}

	private UARTListener _listener;
	private Uart _uart;
	private IOIO _ioio;

	public UARTServer(IOIO ioio, UARTListener listener) {		
		_listener = listener;
		_ioio = ioio;
	}

	@Override
	public void run() {
		try {
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			_uart = _ioio.openUart(5, 4, 9600, Uart.Parity.NONE, Uart.StopBits.ONE);
			InputStream _inputStream = _uart.getInputStream();
			while (true) {
				_listener.onInputStreamReceived(_inputStream);
			}
		} catch (ConnectionLostException e) {
			Log.e("UARTServer", e.getMessage());
			abort();
		}
	}
	
	public void abort() {
//		if (_inputStream != null) {
//			try {
//				_inputStream.close();
//				Log.e("abort()", "CLOSE");
//			} catch (IOException e) {
//				Log.e("abort()", e.getMessage());
//			}
//		}
		if (_uart != null) {
			_uart.close();
			_uart = null;
		}
	}
}
