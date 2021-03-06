package app.communication;

import java.io.IOException;
import java.net.*;

import app.Debug;
import app.communication.CRC8;

public class Connection {

	private static final int RETRY_LIMIT = 10;
	private static final int DELAY_TOLLERANCE = 25;
	private static final int PORT = 6666;
	private static final int BUFFER_SIZE = 20;
	// private static final int ACK_PORT = 7777;

	private static final byte[] ACK = { (byte) 0x00 };
	private static final byte[] NACK = { (byte) 0xFF };

	private static byte sn = 00000000;

	private static InterfaceAddress interfaceAddress = null;

	private static class Listener extends Thread {

		private DatagramSocket receiveSocket;

		public Listener() {
			try {
				receiveSocket = new DatagramSocket(PORT);
			} catch (Exception e) {
				System.err.print(e.getMessage());
			}
		}

		public void run() {
			while (true) {
				byte[] buf = new byte[BUFFER_SIZE];
				DatagramPacket receivePacket = new DatagramPacket(buf,
						buf.length);
				try {
					receiveSocket.receive(receivePacket);
					byte[] message = receivePacket.getData();

					byte[] data = new byte[receivePacket.getLength() - 3];
					System.arraycopy(message, 2, data, 0, data.length);

					byte flag = message[0];
					byte sn = message[1];
					byte crc = message[receivePacket.getLength() - 1];

					InetAddress address = receivePacket.getAddress();
					int ackPort = receivePacket.getPort();

					if (CRC8.calculate(message, receivePacket.getLength() - 1) == crc) {
						if (flag == (byte) 0x00) {
							DatagramPacket ack = new DatagramPacket(ACK,
									ACK.length, address, ackPort);
							receiveSocket.send(ack);
						}
						if (!Cache.isThere(address, sn)) {
							MessageSystem.getInstance().enqueue(
									receivePacket.getAddress(), data);
						}
					} else {
						if (flag == (byte) 0x00) {
							Debug.output("Corrupted message received");
							DatagramPacket nack = new DatagramPacket(NACK,
									NACK.length, address, ackPort);
							receiveSocket.send(nack);
						}
					}

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void start(InterfaceAddress intAdr) {
		Listener l = new Listener();
		l.setName("Connection thread");
		l.start();
		interfaceAddress = intAdr;
	}

	public static boolean send(InetAddress address, byte[] data) {

		int retry = 0;
		boolean sent = false;

		// message packet
		byte[] message = new byte[data.length + 3];
		System.arraycopy(data, 0, message, 2, data.length);
		message[0] = (byte) 0x00;
		message[1] = sn; // add SN
		message[message.length - 1] = CRC8.calculate(message,
				message.length - 1); // add CRC
		DatagramPacket sendPacket = new DatagramPacket(message, message.length,
				address, PORT);

		// ack packet
		byte[] buf = new byte[8];
		DatagramPacket ackPacket = new DatagramPacket(buf, buf.length);

		try {
			DatagramSocket socket = new DatagramSocket();
			socket.setReuseAddress(true);

			while (true) {
				// send the packet
				socket.send(sendPacket);

				// acknowledgment delay tolerance (in milliseconds)
				socket.setSoTimeout(DELAY_TOLLERANCE
						* (int) Math.pow(1.5, retry));

				try {
					socket.receive(ackPacket);

					// MIGLIORARE VERIFICA
					if (ackPacket.getData()[0] == ACK[0]) {
						sn++;
						sent = true;
						break;
					} else {
						Debug.output("NACK received");
						retry++;
					}

				} catch (SocketTimeoutException e) {
					if (++retry >= RETRY_LIMIT) {
						System.err.println("Max retry limit reached. Flag: "+Byte.toString(data[0]));
						sent = false;
						break;
					}
				}
			}
			socket.close();

		} catch (Exception e) {
			System.err.println("FATAL ERROR: send function");
			e.printStackTrace();
			System.exit(1);
		}

		return sent;
	}

	public static void sendBroadcast(byte[] data) {
		try {

			InetAddress address = interfaceAddress.getBroadcast();

			DatagramSocket sendSocket = new DatagramSocket();
			sendSocket.setBroadcast(true);

			byte[] message = new byte[data.length + 3];
			System.arraycopy(data, 0, message, 2, data.length);
			message[0] = (byte) 0x80;
			message[1] = sn; // add SN
			message[message.length - 1] = CRC8.calculate(message,
					message.length - 1); // add CRC
			DatagramPacket sendPacket = new DatagramPacket(message,
					message.length, address, PORT);

			sendSocket.send(sendPacket);
			// System.out.println("send broadcast " + (int) sn);
			sendSocket.close();
			sn++;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}