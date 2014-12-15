package ioio.bar.protocols;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import android.util.Log;

public class UDPServer implements Runnable {
	public interface UDPListener {
		public void onPacketReceived(DatagramPacket packet);
	}
	
	private static final int DATAGRAM_SIZE = 1536;//32*1024;

	private UDPListener _listener;
	private int _port;
	private DatagramSocket _socket;

	public UDPServer(int port, UDPListener listener) {
		_listener = listener;
		_port = port;
		new Thread(this).start();
	}

	public void terminate() {
		Thread.currentThread().interrupt();
    }
	
	@Override
	public void run() {
		try {
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			
			_socket = new DatagramSocket(_port);
//			_socket.setBroadcast(rue);

			while (!Thread.currentThread().isInterrupted()) {
				byte[] buffer = new byte[DATAGRAM_SIZE];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				
				/**
				 * This method blocks until a packet is received or a timeout has expired.
				 */
				_socket.receive(packet);
				
				_listener.onPacketReceived(packet);
			}
		} catch (IOException e) {
			Log.e("UDPServer::run()-IOException", e.getMessage());
		} catch (ArrayIndexOutOfBoundsException e) {
			Log.e("UDPServer::run()-ArrayIndexOutOfBoundsException", "ArrayIndexOutOfBoundsException:  " + e);
		}
	}
	
//	public void write(String str) throws IOException {
//	_socket.send(str.getBytes());
//}

	public void abort() {
		if (_socket != null) {
			if (_socket.isConnected()) {
				_socket.disconnect();
			}
			_socket.close();
			_socket = null;
		}
	}
}
