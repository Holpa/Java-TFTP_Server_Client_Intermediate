// SYSC3303 PROJECT TEAM 3 ITERATION 3
// Iteration 1: Ahmad Holpa | 100877933
// Iteration 2: Jessica Morris | 100882290
// Iteration 3: Jessica Morris, Mujtaba Alhabib
// Iteration 4: Koen Bouwmans
// Iteration 5: Jessica Morris

import java.io.*;
import java.net.*;
import java.util.*;

public class ErrorSimulator implements TFTPHost {

	// UDP Datagram Packets for sending and receiving
	private static DatagramPacket sendPacket, receivePacket;

	// UDP Datagram Sockets for receiving requests, and sending/receiving DATA
	// and ACK packets
	private static DatagramSocket receiveSocket, clientSendReceiveSocket,
			serverSendReceiveSocket;

	// Host addresses
	private static InetAddress clientAddr, serverAddr;

	/*
	 * Constructor. Initializes the DatagramSockets.
	 */
	public ErrorSimulator(String serverAddress) {
		try {
			receiveSocket = new DatagramSocket(68);
			serverSendReceiveSocket = new DatagramSocket();
			clientSendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		try {
			serverAddr = InetAddress.getByName(serverAddress);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * Main method.
	 */
	public static void main(String args[]) {
		// Get server IP address
		Scanner input = new Scanner(System.in);
		System.out.print("Enter server address: ");
		String s = "";
		s = input.nextLine();

		// Create the simulator
		ErrorSimulator sim = new ErrorSimulator(s);

		// Variables for storing error simulation type
		boolean simError = false;
		int packetNumToCorrupt = 0;
		boolean corruptDATA = false;
		int corruptOption = 0;

		System.out.print("Would you like to run an error simulation (Y/N)? ");
		do {
			s = input.nextLine();
		} while (!s.matches("(?i)^y$|^n$"));

		simError = s.matches("(?i)y");

		if (simError) {
			System.out
					.print("Which packet number to produce error on (0 for request, 1 onwards for DATA/ACK)? ");
			do {
				s = input.nextLine();
			} while (!s.matches("^\\d+$"));
			packetNumToCorrupt = Integer.parseInt(s);

			if (packetNumToCorrupt > 0) {
				System.out.print("ACK (A) or DATA (D)? ");
				do {
					s = input.nextLine();
				} while (!s.matches("^[aAdD]$"));
				corruptDATA = s.matches("d|D");
			}

			if (packetNumToCorrupt == 0) {
				System.out.println("REQUEST CORRUPTION OPTIONS:");
				System.out.println("\t(1) Change opcode to 65 65");
				System.out
						.println("\t(2) Change format (remove 0 between mode and filename)");
				System.out
						.println("\t(3) Change mode from netascii/octet to BANANAS");
				System.out.println("\t(4) Drop the request packets");
				System.out.println("\t(5) Send duplicate request packets");
				System.out.print("Enter option: ");
				do {
					s = input.nextLine();
				} while (!s.matches("^[1-5]$"));
				corruptOption = Integer.parseInt(s);
			} else {
				System.out.println("DATA/ACK CORRUPTION OPTIONS:");
				System.out.println("\t(1) Change opcode to 65 65");
				System.out
						.println("\t(2) Change block number (will be 1 larger)");
				System.out.println("\t(3) Change packet size (add one byte)");
				System.out
						.println("\t(4) Send the packet from a new TID, as well as the expected one");
				System.out.println("\t(5) Delete one packet");
				System.out.println("\t(6) Delay the packet by 3 seconds");
				System.out.println("\t(7) Duplicate the packet");
				System.out.print("Enter option: ");
				do {
					s = input.nextLine();
				} while (!s.matches("^[1-7]$"));
				corruptOption = Integer.parseInt(s);
			}
		}

		sim.passOnTFTP(simError, packetNumToCorrupt, corruptDATA, corruptOption);
		input.close();
	}

	/*
	 * Method for handling doing the actual receiving and sending between the
	 * client and server. Firstly, it receives the WRQ/RRQ from the client.
	 * After storing the type of request, it passes the request on to the server
	 * via port 69.
	 */
	private void passOnTFTP(boolean simError, int packetNum,
			boolean corruptDATA, int corruptOption) {
		// Step 1) Receive request from Client
		System.out.println("Simulator: Waiting for request from Client...");
		receiveRequestFromClient();

		byte[] data;
		int clientPort, serverPort = -1, msgSize;
		boolean isRead; // True for RRQ, false for WRQ.

		// Step 2) Grab info from Client's request.
		clientPort = receivePacket.getPort();
		clientAddr = receivePacket.getAddress();
		msgSize = receivePacket.getLength();
		data = receivePacket.getData();
		isRead = (data[1] == 1);

		// Step 3) Change the request if necessary.
		ByteArrayOutputStream rq = new ByteArrayOutputStream();
		if (simError && packetNum == 0) {
			// Option 1: Change opcode
			if (corruptOption == 1) {
				rq.write(65);
				rq.write(65);
			} else {
				rq.write(data[0]);
				rq.write(data[1]);
			}
			// Option 2: Change format
			int i = 2;
			while (data[i] != 0) {
				rq.write(data[i]);
				i++;
			}
			if (!(corruptOption == 2)) {
				rq.write(0);
			}
			i++;

			// Option 3: Change the mode
			if (corruptOption == 3) {
				try {
					rq.write(new String("BANANAS").getBytes());
				} catch (IOException e) {
					// this shouldn't happen
				}
			} else {
				while (data[i] != 0) {
					rq.write(data[i]);
					i++;
				}
			}
			rq.write(0);
			data = rq.toByteArray();
			msgSize = rq.size();
			try {
				rq.close();
			} catch (IOException e) {
				// this shouldn't happen
			}
		}

		// Step 4) Send the request (or not).
		if (corruptOption == 4 && packetNum == 0) {
			System.out.println("--- DROPPING THE REQUEST PACKETS ---");
			System.out.println("Simulator has quit");
			return;
		} else if (corruptOption == 5 && packetNum == 0) {
			System.out.println("--- SENDING DUPLICATE REQUESTS ---");
			sendPacketToServer(data, msgSize, 69);
		}
		sendPacketToServer(data, msgSize, 69);
		System.out.println("Simulator: Request sent to Server");

		int currentPacketNum = (corruptDATA) ? 0 : (isRead) ? 0 : -1;
		int dataSize = 0; // keeps track of the number of bytes in the most
							// recent DATA
		boolean breakDuringLoop = false;
		boolean serverPortSet = false;

		// Loop sending and receiving packets between the client and the server.
		// If the request is a read request, then the loop gracefully exits
		// after it has received a DATA < 516b from the Server and fired back
		// the Client's ACK.
		// If the request is a write request, then the loop will have to be
		// broken in the middle, see later comments.
		do {
			System.out.println("Simulator: Waiting for packet from Server.");
			receivePacketFromServer();
			if ((!isRead && !corruptDATA) || (isRead && corruptDATA))
				currentPacketNum++;
			data = receivePacket.getData();
			dataSize = (isRead) ? receivePacket.getLength() : dataSize;
			msgSize = receivePacket.getLength();

			// Set the server port on the first loop.
			if (!serverPortSet) {
				serverPort = receivePacket.getPort();
				serverPortSet = true;
			}

			// If server sent an error, pass it to the client and quit.
			if (data[0] == 0 && data[1] == 5) {
				System.out.println("--- Received ERROR from Server");
				sendPacketToClient(data, msgSize, clientPort);
				break;
			}

			// Check if it's time to corrupt the packet, and mess with it.
			boolean dontSendPacket = false;
			if (simError && currentPacketNum == packetNum) {
				switch (corruptOption) {
				case 1: // change opcode
					System.out.println("--- Changing opcode");
					data[0] = 65;
					data[1] = 65;
					break;
				case 2: // change block number
					System.out.println("--- Changing block number");
					data[3]++;
					break;
				case 3: // change packet size
					System.out.println("--- Adding a byte");
					ByteArrayOutputStream temp = new ByteArrayOutputStream();
					for (int i = 0; i < msgSize; i++)
						temp.write(data[i]);
					temp.write('A'); // add one byte
					data = temp.toByteArray();
					msgSize = temp.size();
					break;
				case 4: // send packet from different TID
					sendPacketFromDifferentTID(clientAddr, clientPort, false);
					break;
				case 5: // delete one packet
					System.out.println("--- Not sending packet");
					dontSendPacket = true;
					break;
				case 6: // delay the packet
					System.out.println("--- Delaying packet by 3 seconds...");
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						// shouldn't happen
					}
					break;
				case 7: // send a duplicate
					System.out.println("--- Sending duplicate packet");
					sendPacketToClient(data, msgSize, clientPort);
					break;
				}
			}

			if (!dontSendPacket)
				sendPacketToClient(data, msgSize, clientPort);

			// If WRQ, then last DATA packet from client was <516 bytes, so loop
			// stops after sending the last ACK to the client
			if (breakDuringLoop)
				break;

			System.out.println("Simulator: Waiting for packet from Client.");
			receivePacketFromClient();

			if ((isRead && !corruptDATA) || (!isRead && corruptDATA))
				currentPacketNum++;
			data = receivePacket.getData();
			dataSize = (!isRead) ? receivePacket.getLength() : dataSize;
			msgSize = receivePacket.getLength();

			// If client sent an error, pass it to the server and quit.
			if (data[0] == 0 && data[1] == 5) {
				System.out.println("--- Received ERROR from Client");
				sendPacketToServer(data, msgSize, serverPort);
				break;
			}

			// Again, check if it's time to corrupt the packet or not.
			dontSendPacket = false;
			if (simError && currentPacketNum == packetNum) {
				switch (corruptOption) {
				case 1: // change opcode
					System.out.println("--- Changing opcode");
					data[0] = 65;
					data[1] = 65;
					break;
				case 2: // change block number
					System.out.println("--- Changing block number");
					data[3]++;
					break;
				case 3: // change packet size
					System.out.println("--- Adding a byte");
					ByteArrayOutputStream temp = new ByteArrayOutputStream();
					for (int i = 0; i < msgSize; i++)
						temp.write(data[i]);
					temp.write('A'); // add one byte
					data = temp.toByteArray();
					msgSize = temp.size();
					break;
				case 4: // send packet from different TID
					sendPacketFromDifferentTID(serverAddr, serverPort, true);
					break;
				case 5: // delete one packet
					System.out.println("--- Not sending packet");
					dontSendPacket = true;
					break;
				case 6: // delay the packet
					System.out.println("--- Delaying packet by 3 seconds");
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						// shouldn't happen
					}
					break;
				case 7: // send a duplicate
					System.out.println("--- Sending duplicate packet");
					sendPacketToServer(data, msgSize, serverPort);
					break;
				}
			}
			if (!dontSendPacket)
				sendPacketToServer(data, msgSize, serverPort);

			// If WRQ, check if we need to break next loop
			if (!isRead && dataSize < 516)
				breakDuringLoop = true;

		} while (dataSize == 516 || !isRead);
		// a WRQ needs to break in the middle of the loop

		System.out.println("Simulator: Request complete.");
	}

	/*
	 * General method for receiving a packet.
	 */
	private void receivePacket(DatagramSocket socket) {
		byte[] data = new byte[MAX_PACKET_SIZE * 2];
		receivePacket = new DatagramPacket(data, data.length);
		try {
			socket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		printPacket(receivePacket, true, false);
	}

	/*
	 * General method for sending a packet.
	 */
	private void sendPacket(DatagramSocket socket, byte[] data, int msgSize,
			InetAddress target, int port) {
		sendPacket = new DatagramPacket(data, msgSize, target, port);
		printPacket(sendPacket, false, true);
		try {
			socket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/*
	 * Prints a packet's information.
	 */
	private void printPacket(DatagramPacket packet, boolean isReceived,
			boolean printData) {
		if (isReceived) {
			System.out.print("Simulator: Received packet from ");
		} else {
			System.out.print("Simulator: Sending packet to ");
		}

		System.out.println(packet.getAddress() + ":" + packet.getPort());
		System.out.println("\tWith opcode: " + packet.getData()[0] + " "
				+ packet.getData()[1]);
		System.out.println("\tPacket size: " + packet.getLength() + " bytes.");

		if (printData) {
			System.out.print("\tContaining: ");
			for (int i = 2; i < packet.getLength(); i++) {
				System.out.print((char) packet.getData()[i]);
			}
			System.out.println();
		}
	}

	/*
	 * Specific version of receivePacket, used for receiving a request packet
	 * from the Client.
	 */
	public void receiveRequestFromClient() {
		receivePacket(receiveSocket);
	}

	/*
	 * Specific version of receivePacket, used for receiving a packet from the
	 * Server.
	 */
	public void receivePacketFromServer() {
		receivePacket(serverSendReceiveSocket);
	}

	/*
	 * Specific version of receivePacket, used for receiving a packet from the
	 * Client.
	 */
	public void receivePacketFromClient() {
		receivePacket(clientSendReceiveSocket);
	}

	/*
	 * Specific version of receivePacket, used for sending a packet to the
	 * Server.
	 */
	public void sendPacketToServer(byte[] data, int msgSize, int port) {
		sendPacket(serverSendReceiveSocket, data, msgSize, serverAddr, port);
	}

	/*
	 * Specific version of receivePacket, used for sending a packet to the
	 * Client.
	 */
	public void sendPacketToClient(byte[] data, int msgSize, int port) {
		sendPacket(clientSendReceiveSocket, data, msgSize, clientAddr, port);
	}

	private void sendPacketFromDifferentTID(InetAddress target, int targetPort,
			boolean isServer) {
		byte[] data;

		// Create a new socket to send the packet from
		DatagramSocket tempSocket = null;
		try {
			tempSocket = new DatagramSocket();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Send the packet to the client/server
		sendPacket = new DatagramPacket(receivePacket.getData(),
				receivePacket.getLength(), target, targetPort);
		try {
			tempSocket.send(sendPacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.print("Simulator: Send packet to ");
		if (isServer)
			System.out.print("Server");
		else
			System.out.print("Client");
		System.out.println(" using TID " + tempSocket.getLocalPort());

		// Get the error back from the client/server
		data = new byte[MAX_PACKET_SIZE * 2];
		receivePacket = new DatagramPacket(data, data.length);
		try {
			tempSocket.receive(receivePacket);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (data[0] == 0 && data[1] == 5) {
			System.out.print("--- Received error " + data[2] + data[3]
					+ " from ");
			if (isServer)
				System.out.print("Server");
			else
				System.out.print("Client");
			System.out.println(" when using different port.");
		} else {
			System.out.print("HOUSTON, WE HAVE A PROBLEM: ");
			if (isServer)
				System.out.print("Server");
			else
				System.out.print("Client");
			System.out.println(" did not send an error packet!");
		}
		tempSocket.close();
	}
}
