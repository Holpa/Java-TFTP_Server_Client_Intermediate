// SYSC3303 PROJECT TEAM 3 ITERATION 5
// Iteration 1: Jessica Morris | 100882290
// Iteration 2: No changes
// Iteration 3: Ahmad Holpa, Tamer Kakish, Jessica Morris
// Iteration 4: No changes
// Iteration 5: No changes

import java.io.IOException;
import java.net.*;

public class SocketListener extends Thread implements TFTPHost {

	private Server s;
	private int port;
	private DatagramSocket socket;
	private DatagramPacket receivePacket;
	private boolean stopRequested;

	/*
	 * Constructor. Doesn't instantiate any Datagram objects, just stores the
	 * information on the port to monitor.
	 */
	public SocketListener(int port, Server s) {
		this.port = port;
		stopRequested = false;
		this.s = s;
	}

	/*
	 * Runs the SocketListener thread.
	 */
	public void run() {

		// Instantiate the socket
		try {
			socket = new DatagramSocket(port);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// nb: without the timeout, if the server receives a stop request, the
		// SocketListener thread won't quit until it has received and serviced a
		// request
		try {
			socket.setSoTimeout(500);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			// This probably won't happen
		}

		System.out.println("SocketListener: Thread monitoring port " + port
				+ ".");

		// Run until server receives a stop request.
		while (!stopRequested) {

			// Step 1) Receive packet.
			byte[] data = new byte[MAX_PACKET_SIZE];
			receivePacket = new DatagramPacket(data, MAX_PACKET_SIZE);
			try {
				socket.receive(receivePacket);
			} catch (IOException e) {
				// Don't really care if the socket times out.
			}

			// Step 2) Determine packet type.
			byte[] packetData = receivePacket.getData();
			int dataSize = receivePacket.getLength();
			int TID = receivePacket.getPort();
			InetAddress machine = receivePacket.getAddress();

			if (TID > -1) {
				// Step 3) Initialize a thread to deal with the request.
				System.out
						.println("SocketListener: Received request from "+machine.toString()+":"+TID+". Starting new thread to service request...");

				ServerRequestThread newThread = new ServerRequestThread(s,
						packetData, dataSize, TID, machine);
				s.addRequest();
				newThread.run();
			}
		}

		// If execution reaches this point, then a stop was requested.
		socket.close();

		while (s.getNumberOfRequests() > 0) {
			continue; // Busywait
		}

		System.out.println("SocketListener has quit.");

	}

	/*
	 * Requests that the SocketListener thread stop. The thread will not stop
	 * immediately when a stop request is sent.
	 */
	public void requestStop() {
		stopRequested = true;
		System.out.println("SocketListener: Waiting for "
				+ s.getNumberOfRequests() + " request thread(s) to finish...");
	}
}
