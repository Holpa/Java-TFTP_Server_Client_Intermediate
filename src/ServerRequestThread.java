// SYSC3303 PROJECT TEAM 3 ITERATION 5
// Iteration 1: Jessica Morris | 100882290
// Iteration 2: Mujtaba Alhabib, Koen Bouwmans, Ahmad Holpa, Tamer Kakish
// Iteration 3: Koen Bouwmans, Ahmad Holpa, Tamer Kakish, Jessica Morris
// Iteration 4: Mujtaba Alhabib, Ahmad Holpa, Jessica Morris
// Iteration 5: 

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;

import javax.swing.*;

@SuppressWarnings("serial")
public class ServerRequestThread extends JFrame implements Runnable, TFTPHost {
	private Server server;
	private byte[] requestData;
	private int requestDataSize;
	private int TID; // Transfer ID = port number of first request packet
	private DatagramSocket socket;
	private DatagramPacket receivePacket, sendPacket;
	private InetAddress targetMachine;
	private JTextArea ta;

	public ServerRequestThread(Server s, byte[] data, int dataSize, int TID,
			InetAddress target) {
		super("Thread " + TID);
		server = s;
		requestData = data;
		requestDataSize = dataSize;
		this.TID = TID;
		targetMachine = target;

		// Set up UI elements
		Box box = Box.createVerticalBox();
		ta = new JTextArea(20, 50);
		ta.setEditable(false);
		JScrollPane pane1 = new JScrollPane(ta,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		box.add(pane1);
		getContentPane().add(box);

		// Initialize socket, set socket timeout
		try {
			socket = new DatagramSocket();
			socket.setSoTimeout(SOCKET_TIMEOUT);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * RUN THE THREAD - DO READ OR WRITE OR SEND ERRORS
	 */
	public void run() {
		// Request in progress, let the server know
		System.out.println("Thread " + TID + " initialized.");

		// Create UI
		this.pack();
		this.setVisible(true);

		// Check for any errors in the request - if there are, send an error
		// message. Packet format is expected to be opcode filename 0 mode 0
		byte[] opcode = { requestData[0], requestData[1] };
		StringBuilder fileName = new StringBuilder();
		StringBuilder mode = new StringBuilder();
		int i = 2;
		while (requestData[i] != 0 && i < requestDataSize) {
			fileName.append((char) requestData[i]);
			i++;
		}
		// Don't need to check if it's formatted as [ ... filename 0 mode ... ]
		// If it's not properly formatted (e.g. [ ... filename mode ... ]) then
		// the mode will not be valid anyway
		i++; // skip the 0 between filename and mode
		while (requestData[i] != 0 && i < requestDataSize) {
			mode.append((char) requestData[i]);
			i++;
		}

		// Print request information to the UI
		ta.append("------ REQUEST PACKET DETAILS ------\n");
		ta.append("Opcode: " + requestData[0] + " " + requestData[1] + "\n");
		ta.append("Filename: " + fileName.toString() + "\n");
		ta.append("Mode: " + mode.toString() + "\n\n");

		// Check the mode
		if (!(mode.toString().matches("(?i)^octet$|^netascii$"))) {
			ta.append("ERROR 4: Invalid mode. Mode should be octet or netascii. Found: "
					+ mode.toString() + "\n");
			byte[] errorMsg = generateErrorMessage((short) 4,
					"Invalid mode. Mode should be octet or netascii. Found: "
							+ mode.toString());
			sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
					targetMachine, TID);
			try {
				socket.send(sendPacket);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			server.removeRequest();
			socket.close();
			ta.append("\nRequest finished.\n");
			System.out.println("Thread " + TID + " has exited.");
			return;
		} else {
			// Else, perform read, or write, or bad opcode
			String filePath = server.defaultDirectory + "\\"
					+ fileName.toString();
			File file = new File(filePath);

			if (opcode[0] == 0 && opcode[1] == 1) {
				try {
					doRead(file);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (opcode[0] == 0 && opcode[1] == 2) {
				try {
					doWrite(file);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				ta.append("ERROR 4: Invalid opcode.\n");
				byte[] errorMsg = generateErrorMessage((short) 4,
						"Invalid opcode. Expected 01 or 02. Found: "+opcode[0]+opcode[1]+"");
				sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
						targetMachine, TID);
				try {
					socket.send(sendPacket);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				server.removeRequest();
			}
		}
		if (socket != null)
			socket.close();

		// Request completed, let the server know
		ta.append("\nRequest finished.\n");
		System.out.println("Thread " + TID + " has exited.");
		server.removeRequest();
	}

	/*
	 * Reads from a file. Read request steps: 1) Read from file 2) Grab data in
	 * 512-byte chunks 3) Construct and send data packet to client 4) Receive
	 * ACK from client 5) Continue 1-4 until end of file reached (last chunk is
	 * <512 bytes)
	 */
	public void doRead(File file) throws IOException {
		int blockNum = 1;
		int receiveTries = 0;
		ta.append("Reading file \"" + file.toString() + "\".\n");

		// 1) Read from file
		if (file.exists() && file.canRead()) {
			FileInputStream in;
			in = new FileInputStream(file);
			long totalBlocks = (file.length() / 512) + 1;

			byte[] data;
			int bytesRead;
			boolean dontSendData = false;
			boolean finalACKReceived = false;

			// 2) Grab data in 512-byte chunks
			do {
				// 3) CHECK FOR ERRORS
				// 3.1) Check if still have access to the file
				if (!(file.canRead())) {
					ta.append("\nERROR 2: Could not perform file read, file is protected.\n");
					byte[] errorMsg = generateErrorMessage((short) 2,
							"Access violation, cannot read from file. "+file.getPath()+" has changed its access rights.");

					// Send the error packet to the Client.
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					socket.send(sendPacket);
					ta.append("ERROR 2 sent.\n");
					break;
				}				

				// 4) Construct and send DATA
				if (!dontSendData) {
					data = new byte[BLOCK_SIZE];
					bytesRead = in.read(data);

					if (bytesRead == -1) {
						bytesRead = 0;
					}
					
					ByteArrayOutputStream msg = new ByteArrayOutputStream();
					msg.write(0);
					msg.write(3);
					msg.write((byte) (blockNum >> 8));
					msg.write((byte) (blockNum));
					msg.write(data);

					// Send packet
					sendPacket = new DatagramPacket(msg.toByteArray(),
							bytesRead + 4, targetMachine, TID);
					socket.send(sendPacket);
					if (server.verboseOn()) {
						ta.append("DATA " + blockNum + " sent. (Size: "
								+ (bytesRead + 4) + " bytes)\n");
					}
				} else {
					bytesRead = (blockNum == totalBlocks) ? 0 : BLOCK_SIZE;
				}

				// 4) Receive ACK / Error from Client
				data = new byte[MAX_PACKET_SIZE + 1];
				receivePacket = new DatagramPacket(data, data.length);
				while (receiveTries < 3) {
					try {
						socket.receive(receivePacket);
						break;
					} catch (SocketTimeoutException e) {
						if (receivePacket.getPort() == -1) {
							receiveTries++;
							ta.append("Receiving ACK " + blockNum
									+ " timed out, resending DATA " + blockNum
									+ "...\n");
							socket.send(sendPacket);
						} else
							break;
					}
				}
				if (receiveTries == 3) {
					ta.append("ERROR: Too many packets timed out, terminating transfer.\n");
					break;
				}

				receiveTries = 0;

				// 4.1) Check where the packet came from
				// If it came from a weird location, loop sending an error and
				// waiting for a new packet
				while (receivePacket.getPort() != TID
						|| !(receivePacket.getAddress().equals(targetMachine))) {
					byte[] errorMsg = generateErrorMessage((short) 5,
							"Unknown transfer ID. Who are you and how did you get this IP and port number?");
					// send packet to the invalid source
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							receivePacket.getAddress(), receivePacket.getPort());
					socket.send(sendPacket);
					ta.append("ERROR: Sent error message 5 to "
							+ receivePacket.getAddress().toString() + ":"
							+ receivePacket.getPort() + ".\n");
					data = new byte[BLOCK_SIZE + 5];
					receivePacket = new DatagramPacket(data, data.length);
					while (receiveTries < 3) {
						try {
							socket.receive(receivePacket);
							break;
						} catch (SocketTimeoutException e) {
							if (receivePacket.getPort() == -1) {
								receiveTries++;
								ta.append("Receiving ACK "
										+ blockNum
										+ "from correct TID timed out "
										+ ((receiveTries == 1) ? "once"
												: receiveTries + " times")
										+ ", still waiting...\n");
							} else
								break;
						}
					}
					if (receiveTries == 3) {
						ta.append("ERROR: Too many packets timed out, terminating transfer.\n");
						break;
					}

					receiveTries = 0;
				}

				// 4.2) Check if received error from Client
				if (data[0] == 0 && data[1] == 5) {
					ta.append("\nERROR: Received code "
							+ data[2]
							+ data[3]
							+ " with message \""
							+ byteArrayToString(data, 4,
									receivePacket.getLength()) + "\"\n");
					break;
				}

				// 4.3) Check ACK packet size
				if (receivePacket.getLength() != 4) {
					ta.append("\nERROR 4: ACK packet was not size 4.\n");
					byte[] errorMsg = generateErrorMessage((short) 4,
							"Illegal TFTP operation: Wrong packet size, expected 4 bytes, found: "+receivePacket.getLength()+" bytes");
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					socket.send(sendPacket);
					break;
				}

				// 4.4) Check ACK opcode
				if (data[0] != 0 && data[1] != 4) {
					ta.append("\nERROR 4: Illegal TFTP operation Wrong Opcode, expected 04. found: "+data[0]+data[1]+"\n");
					byte[] errorMsg = generateErrorMessage((short) 4,
							"Invalid opcode. expected 04, found: "+data[0]+data[1]);
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					socket.send(sendPacket);
					break;
				}

				// 4.5) Check ACK block number
				// If it's smaller than expected, it's a duplicate, don't send
				// duplicate DATA
				// If it's bigger than expected, we have a problem
				// If it's exactly as expected, send DATA
				int receivedBlockNum = bytesToInt(data[2], data[3]);

				if (server.verboseOn()) {
					ta.append("ACK " + receivedBlockNum + " received.\n");
				}

				if (receivedBlockNum < blockNum) {
					dontSendData = true;
					finalACKReceived = false;
				} else if (receivedBlockNum > blockNum) {
					ta.append("\nERROR: 4 Illegal TFTP operation, wrong block number, expected: "+blockNum+". Found"+receivedBlockNum+"\n");
					byte[] errorMsg = generateErrorMessage((short) 4,
							"Illegal TFTP operation, wrong block number, expected: "+blockNum+". Found"+receivedBlockNum);
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					socket.send(sendPacket);
					break;
				} else {
					dontSendData = false;
					finalACKReceived = receivedBlockNum == totalBlocks;
					if (blockNum < 65535) blockNum++;
					else blockNum = 0;
				}
			} while (!finalACKReceived);

			in.close();

		}

		// Couldn't read from the file because it doesn't exist.
		else if (!(file.exists())) {
			ta.append("\nERROR:Couldn't read a new message because file doesn't exist.\n");
			byte[] errorMsg = generateErrorMessage((short) 1,
					"File "+file.getPath()+" does not exist");
			sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
					targetMachine, TID);
			socket.send(sendPacket);
			ta.append("ERROR 1 sent.\n");
		}

		// Else, couldn't read from the file because Server doesn't have
		// permission to do so.
		else {
			ta.append("\nERROR: Could not perform file read, file is protected.\n");
			byte[] errorMsg = generateErrorMessage((short) 2,
					"Access violation. "+file.getPath()+" has changed its access rights");
			sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
					targetMachine, TID);
			socket.send(sendPacket);
			ta.append("ERROR 2 sent.\n");
		}
	}

	/*
	 * Performs a write to a file, if possible. Write request steps: 1) Check if
	 * file can be created/written to 2) Open the file to modify 3) Send first
	 * ACK to client (block 0) 4) Receive data packet [0 4 data] 5) Write data
	 * from packet to file 6) Send ACK to client 7) Repeat 4-7 until last packet
	 * (<512 bytes data) received
	 */
	public void doWrite(File file) throws IOException {
		short blockNum = 1;

		// 1) Check if file can be created/written to
		if (!file.exists()) {
			file.getParentFile().mkdirs();
			file.createNewFile();
			ta.append("Created file \"" + file.toString() + "\".\n");
		}

		else {
			ta.append("ERROR: File \"" + file.getPath()
					+ "\" already exists.\n");

			// Send ERROR 6 packet to client.
			byte[] errorMsg = generateErrorMessage((short) 6,
					"File: \"" + file.getPath()
					+ "\" already exists.");
			sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
					targetMachine, TID);
			socket.send(sendPacket);
			ta.append("ERROR 6 sent.\n");
			return;
		}

		// 2) Open the file to modify
		ta.append("Initializing file write via port " + TID + ".\n");
		FileOutputStream out = new FileOutputStream(file);
		int bytesIn;
		byte[] data;

		// 3) Send first ACK to client (block 0)
		data = new byte[4];
		data[0] = 0;
		data[1] = 4;
		data[2] = 0;
		data[3] = 0;
		sendPacket = new DatagramPacket(data, data.length, targetMachine, TID);
		socket.send(sendPacket);
		if (server.verboseOn()) {
			ta.append("ACK 0 sent.\n");
		}

		do {
			// 4) Receive data packet
			data = new byte[MAX_PACKET_SIZE + 1];
			int receiveTries = 0;

			receivePacket = new DatagramPacket(data, data.length);
			while (receiveTries < 3) {
				try {
					socket.receive(receivePacket);
					break;
				} catch (SocketTimeoutException e) {
					if (receivePacket.getPort() == -1) {
						receiveTries++;
						ta.append("Receiving DATA " + blockNum
								+ " timed out, resending ACK " + (blockNum - 1)
								+ "...\n");
						socket.send(sendPacket);
					} else
						break;
				}
			}
			if (receiveTries == 3) {
				ta.append("ERROR: Receiving DATA from Client took too long, terminating transfer.\n");
				break;
			}

			receiveTries = 0;
			int packetSize = receivePacket.getLength();

			// 5) CHECK FOR ERRORS
			// 5.1) Make sure packet came from right machine and port
			// If it came from a weird location, loop sending an error and
			// waiting for a new packet
			while (receivePacket.getPort() != TID
					|| !receivePacket.getAddress().equals(targetMachine)) {
				byte[] errorMsg = generateErrorMessage((short) 5,
						"Unknown transfer ID. Who are you and how did you get this IP and port number?");
				// send packet to the invalid source
				sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
						receivePacket.getAddress(), receivePacket.getPort());
				socket.send(sendPacket);
				ta.append("ERROR: Sent error message 5 to "
						+ receivePacket.getAddress().toString() + ":"
						+ receivePacket.getPort());
				data = new byte[BLOCK_SIZE + 5];
				receivePacket = new DatagramPacket(data, data.length);
				while (receiveTries < 3) {
					try {
						socket.receive(receivePacket);
						break;
					} catch (SocketTimeoutException e) {
						if (receivePacket.getPort() == -1) {
							receiveTries++;
							ta.append("Receiving ACK "
									+ blockNum
									+ "from correct TID timed out "
									+ ((receiveTries == 1) ? "once"
											: receiveTries + " times")
									+ ", still waiting...\n");
						} else
							break;
					}
				}
				if (receiveTries == 3) {
					ta.append("ERROR: Too many packets timed out, terminating transfer.\n");
					break;
				}

				receiveTries = 0;
			}

			// 5.2) Check if error received from Client
			if (data[0] == 0 && data[1] == 5) {
				ta.append("\nERROR: Received code " + data[2] + data[3]
						+ " with message \""
						+ byteArrayToString(data, 4, receivePacket.getLength())
						+ "\"\n");
				out.close();
				file.delete();
				return;
			}

			// 5.3) Check packet size
			if (packetSize > MAX_PACKET_SIZE) {
				ta.append("\nERROR: Received DATA packet was bigger than 516 bytes.\n");
				byte[] errorMsg = generateErrorMessage((short) 4,
						"Received DATA packet was bigger than 516 bytes. Received: "+packetSize+" bytes");
				sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
						targetMachine, TID);
				socket.send(sendPacket);
				ta.append("ERROR 4 sent.\n");
				out.close();
				file.delete();
				return;
			}

			// 5.4) Check opcode
			if (!(data[0] == 0 && data[1] == 3)) {
				ta.append("\nERROR: Received wrong opcode from client. Expected: 03. Found: "
						+ data[0] + data[1] + "\n");
				byte[] errorMsg = generateErrorMessage((short) 4,
						"Illegal TFTP operation Wrong Opcode from client. Expected: 03. Found: "
						+ data[0] + data[1]);
				sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
						targetMachine, TID);
				socket.send(sendPacket);
				ta.append("ERROR 4 sent.\n");
				out.close();
				file.delete();
				return;
			}

			// 5.5) Check block number.
			// If it's smaller than expected, then it is a duplicate DATA and
			// ignore it.
			// If it's bigger than expected, then that's a problem, send an
			// error and stop.
			// Otherwise, it's expected, so write it to the file.
			int receivedBlockNum = bytesToInt(data[2], data[3]);
			if (server.verboseOn()) {
				ta.append("DATA " + receivedBlockNum + " received. (Size: "
						+ packetSize + " bytes)\n");
			}

			if (receivedBlockNum < blockNum) {
				bytesIn = BLOCK_SIZE;
				// Send an ACK for the duplicate data
				byte[] ack = {0, 4, data[2], data[3]};
				sendPacket = new DatagramPacket(ack, ack.length, targetMachine, TID);
				socket.send(sendPacket);
				if (server.verboseOn()) ta.append("ACK "+receivedBlockNum+" sent.\n");
			}
			
			else if (receivedBlockNum > blockNum) {
				ta.append("\nERROR: 4 Illegal TFTP Illegal TFTP operation, wrong block number, expected: "+blockNum+". Found"+receivedBlockNum+"\n");
				byte[] errorMsg = generateErrorMessage((short) 4,
						"Illegal TFTP operation, wrong block number, expected: "+blockNum+". Found"+receivedBlockNum);
				sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
						targetMachine, TID);
				socket.send(sendPacket);
				ta.append("ERROR 4 sent.\n");
				out.close();
				file.delete();
				return;
			}
			
			else {
				// 5.6) Check if disk is full
				if (file.getFreeSpace() < MIN_DISK_BYTES) {
					// ERROR 3: Disk full if less then 512 bytes available we
					// cannot write 512 byte block to file.
					ta.append("\nERROR: Could not complete WRQ, disk space exceeded.\n");
					byte[] errorMsg = generateErrorMessage((short) 3,
							"Disk full or allocation exceeded, your file has been deleted, try a smaller file");

					// Send the error packet to the Client.
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					socket.send(sendPacket);
					ta.append("ERROR 3 sent.\n");

					// Remove partial file from disk.
					out.close();
					file.delete();
					return;
				}

				// 5.7) Check that we still have permission to write to file
				if (!(file.canWrite())) {
					ta.append("\nERROR: Could not perform file write, file is protected.\n");
					byte[] errorMsg = generateErrorMessage((short) 2,
							"Access violation. "+file.getPath()+" has changed its access rights");

					// Send the error packet to the Client.
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					socket.send(sendPacket);
					ta.append("ERROR 2 sent.\n");
					// Can't delete the file if we lost writing permissions
					break;
				}

				// 6) Write data from packet to file
				bytesIn = receivePacket.getLength() - 4;
				// minus four because the first four bytes are not data

				out.write(receivePacket.getData(), 4, bytesIn);
				ta.append("DATA "+blockNum+" written to file.\n");

				// 7) Send ACK to client
				data = new byte[4];
				data[0] = 0;
				data[1] = 4;
				data[2] = (byte) (blockNum >> 8);
				data[3] = (byte) (blockNum);

				sendPacket = new DatagramPacket(data, data.length,
						targetMachine, TID);
				socket.send(sendPacket);

				if (server.verboseOn()) {
					ta.append("ACK " + blockNum + " sent.\n");
				}

				if (blockNum < 65535) blockNum++;
				else blockNum = 0;
			}
		} while (bytesIn == BLOCK_SIZE);

		out.close();

	}

	/*
	 * Generates the byte array for an error packet.
	 */
	private byte[] generateErrorMessage(short errorCode, String message) {
		ByteArrayOutputStream msg = new ByteArrayOutputStream();
		msg.write(0);
		msg.write(5);
		msg.write((byte) (errorCode >> 8));
		msg.write((byte) (errorCode));
		try {
			msg.write(message.getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// This shouldn't happen
			e.printStackTrace();
		}
		msg.write(0);

		return msg.toByteArray();
	}

	private String byteArrayToString(byte[] input, int startIndex, int endIndex) {
		if (startIndex < input.length && endIndex < input.length) {
			StringBuilder toReturn = new StringBuilder();
			for (int i = startIndex; i < endIndex; i++) {
				toReturn.append((char) input[i]);
			}

			return toReturn.toString();
		} else
			return "";
	}

	private int bytesToInt(byte byte1, byte byte2) {
		int intpart1 = (int) byte1;
		int intpart2 = (int) byte2;

		// make sure we dont have negative block numbers
		if (intpart1 < 0)
			intpart1 += 256;
		if (intpart2 < 0)
			intpart2 += 256;

		// add both blocks into one
		intpart1 = intpart1 << 8;
		intpart1 += intpart2;

		return intpart1;
	}
}
