// SYSC3303 PROJECT TEAM 3 ITERATION 4
// Iteration 1: Jessica Morris | 100882290
// Iteration 2: Mujtaba Alhabib, Koen Bouwmans, Ahmad Holpa, Tamer Kakish
// Iteration 3: Koen Bouwmans, Ahmad Holpa, Tamer Kakish, Jessica Morris
// Iteration 4: 
// Iteration 5:

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client implements TFTPHost {

	// declare send and receive packets
	private DatagramPacket sendPacket, receivePacket;
	// declare a send/receive socket
	private DatagramSocket sendReceiveSocket;

	// finals
	private static File defaultDirectory;
	private int DESTINATION_PORT = SERVER_PORT;

	// globals
	byte block[] = new byte[MAX_PACKET_SIZE + 1];
	String fileName;
	private int TID;
	private InetAddress targetMachine;

	// method to automatically create the socket once an instance of client is
	// made
	public Client() {
		try {
			// create the socket
			System.out.println("Created Socket");
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.setSoTimeout(SOCKET_TIMEOUT);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	/*
	 * method to call upon a read request order of operation as follows:
	 */
	// send RRQ
	// receive DATA
	// send ACK
	public void RRQ() {
		// declare and initialize an output stream
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		// create a file object with the provided path
		File file = new File(defaultDirectory.getPath() + "\\" + fileName);

		// Check if file exists
		if (file.exists()) {
			System.out.println("ERROR 6: File (" + file.getPath()
					+ ") already exists on Client. Try another filename.");
			try {
				outputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				// This shouldn't happen
				e.printStackTrace();
			}
			return;
		}

		// File didn't already exist, so create file.
		if (!file.exists()) {
			try {
				file.createNewFile();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			// create RRQ
			outputStream.write(0);
			outputStream.write(1);
			outputStream.write(fileName.getBytes());
			outputStream.write(0);
			outputStream.write(MODE_NETASCII.getBytes());
			block = outputStream.toByteArray();

			// SEND RRQ
			sendPacket = new DatagramPacket(block, block.length, targetMachine,
					DESTINATION_PORT);
			sendReceiveSocket.send(sendPacket);
			System.out.println("Sent RRQ.");
			// create a stream to output the information from the server into a
			// file
			FileOutputStream out = new FileOutputStream(file);
			boolean TIDset = false;
			int blockNum = 1;
			int bytesIn;

			do {
				// receive Data N
				block = new byte[MAX_PACKET_SIZE + 1];
				receivePacket = new DatagramPacket(block, block.length);
				int receiveTries = 0;

				while (receiveTries < 3) {
					try {
						sendReceiveSocket.receive(receivePacket);
						break;
					} catch (SocketTimeoutException e) {
						if (!TIDset && receivePacket.getPort() == -1) {
							receiveTries++;
							System.out
									.println("Receiving DATA 1 timed out, resending RRQ...");
							sendReceiveSocket.send(sendPacket);
						} else if (receivePacket.getPort() == -1) {
							receiveTries++;
							System.out.println("Receiving DATA " + blockNum
									+ " timed out, resending ACK "
									+ (blockNum - 1) + "...");
							sendReceiveSocket.send(sendPacket);
						} else
							break;
					}
				}
				if (receiveTries == 3) {
					System.out
							.println("ERROR: Receiving DATA from server timed out. Deleting partial file and terminating transfer.");
					System.out
							.println("Try your request again in a few minutes.");
					out.close();
					file.delete();
					return;
				}

				receiveTries = 0;

				// Set the TID the on the first loop
				if (!TIDset) {
					TID = receivePacket.getPort();
					TIDset = true;
				}

				// Check where the packet came from
				// If it came from a weird location, loop sending an error and
				// waiting for a new packet
				while (receivePacket.getPort() != TID
						|| !receivePacket.getAddress().equals(targetMachine)) {
					byte[] errorMsg = generateErrorMessage((short) 5,
							"Unknown transfer ID. Who are you and how did you get this IP and port number?");
					// send packet to the invalid source
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							receivePacket.getAddress(), receivePacket.getPort());
					sendReceiveSocket.send(sendPacket);
					System.out.println("Sent ERROR 5 to "
							+ receivePacket.getAddress().toString() + ":"
							+ receivePacket.getPort());
					System.out
							.println("Waiting for packet from correct TID...");

					block = new byte[MAX_PACKET_SIZE + 1];
					receivePacket = new DatagramPacket(block, block.length);
					while (receiveTries < 3) {
						try {
							sendReceiveSocket.receive(receivePacket);
							break;
						} catch (SocketTimeoutException e) {
							if (receivePacket.getPort() == -1) {
								receiveTries++;
								System.out.println("Receiving DATA "
										+ blockNum
										+ "from correct TID timed out "
										+ ((receiveTries == 1) ? "once"
												: receiveTries + " times"));
							} else
								break;
						}
					}
					if (receiveTries == 3) {
						System.out
								.println("ERROR: Receiving DATA from server timed out. Deleting partial file and terminating transfer.");
						System.out
								.println("Try your request again in a few minutes.");
						out.close();
						file.delete();
						return;
					}
					receiveTries = 0;
				}

				// ERROR packet received, delete partial file
				if (block[0] == 0 && block[1] == 5) {
					unpackPacketError(receivePacket);
					System.out.println("Deleting partial file and terminating transfer.");
					out.close();
					file.delete();
					return;
				}

				// Wrong opcode received
				else if (!(block[0] == 0 && block[1] == 3)) {
					System.out
							.println("ERROR 4: Wrong opcode. Expected: 03. Received: "
									+ block[0] + block[1]);
					// make error message
					byte[] errorMsg = generateErrorMessage((short) 4,
							"Illegal TFTP operation: Wrong opcode. Expected: 03. Received: "
									+ block[0] + block[1]);
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					sendReceiveSocket.send(sendPacket);
					System.out.println("ERROR 4 sent. Deleting partial file and terminating transfer.");
					out.close();
					file.delete();
					return;
				}

				// Received DATA that wasn't <= 512 bytes
				else if (receivePacket.getLength() >= 517) {
					System.out
							.println("ERROR 4: Wrong packet size. Expected: 516 bytes or smaller. Received: "
									+ receivePacket.getLength());
					// make error message
					byte[] errorMsg = generateErrorMessage(
							(short) 4,
							"Illegal TFTP operation: Wrong packet size. Expected: 516 bytes or smaller. Received: "
									+ receivePacket.getLength());
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					sendReceiveSocket.send(sendPacket);
					System.out.println("ERROR 4 sent. Deleting partial file and terminating transfer.");
					out.close();
					file.delete();
					return;
				}

				// Check that there's still room on the disk before writing
				if (file.getFreeSpace() < MIN_DISK_BYTES) {
					System.out
							.println("ERROR 3: Could not complete read request, out of disk space. Try cleaning up your disk, or reading a smaller file.");
					byte[] errorMsg = generateErrorMessage((short) 3,
							"Disk full or allocation exceeded, your file has been deleted, try a smaller file.");

					// Send the error packet to the Server.
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					sendReceiveSocket.send(sendPacket);
					System.out
							.println("ERROR 3 sent. Deleting partial file from disk and terminating transfer.");

					// Remove partial file from disk.
					out.close();
					file.delete();
					return;
				}

				int receivedBlockNum = bytesToInt(block[2], block[3]);
				System.out.println("Received DATA " + receivedBlockNum + ".");

				if (receivedBlockNum > blockNum) {
					// If received block number is larger than expected, send an
					// error.
					System.out
							.println("ERROR 4: Wrong block number. Expected: "
									+ blockNum + ". Received: "
									+ receivedBlockNum);
					// make error message
					byte[] errorMsg = generateErrorMessage((short) 4,
							"Illegal TFTP operation: Wrong block number. Expected: "
									+ blockNum + ". Received: "
									+ receivedBlockNum);
					sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
							targetMachine, TID);
					sendReceiveSocket.send(sendPacket);
					System.out.println("ERROR 5 sent. Deleting partial file and terminating transfer.");
					out.close();
					file.delete();
					return;
				} else if (receivedBlockNum < blockNum) {
					// If received block number is smaller than expected, send
					// an ACK but don't write the data.
					bytesIn = MAX_PACKET_SIZE;
					byte[] ack = { 0, 4, block[2], block[3] };
					sendPacket = new DatagramPacket(ack, ack.length,
							targetMachine, TID);
					sendReceiveSocket.send(sendPacket);
					System.out.println("Sent ACK " + receivedBlockNum + ".");
				} else {
					// Block number is what was expected, write the file.
					bytesIn = receivePacket.getLength();
					
					
					// Check if we still have access to file
					if (!(file.canWrite())) {
						System.out.println("ERROR 3: Could not perform file write, file has been protected.");
						byte[] errorMsg = generateErrorMessage((short) 2,
								"Access violation. "+file.getPath()+" has changed its access rights");

						// Send the error packet to the Client.
						sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
								targetMachine, TID);
						sendReceiveSocket.send(sendPacket);
						System.out.println("ERROR 2 sent.\n");
						// Can't delete the file if we lost writing permissions
						break;
					}
					
					// write to file
					out.write(receivePacket.getData(), 4, bytesIn - 4);

					// send ACK N
					byte[] ack = new byte[4];
					ack[0] = 0;
					ack[1] = 4;
					ack[2] = (byte) ((blockNum >> 8) & 0xff);
					ack[3] = (byte) (blockNum & 0xff);
					sendPacket = new DatagramPacket(ack, ack.length,
							targetMachine, TID);
					sendReceiveSocket.send(sendPacket);
					System.out.println("DATA written.\nSent ACK " + blockNum);

					// increment block num
					blockNum++;
				}
			} while (bytesIn == MAX_PACKET_SIZE);
			// if bytesIn<packetSize then we will not receive anymore
			// close file output stream
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * method to call upon a write request order of operation as follows:
	 */
	// send WRQ
	// receive ACK0
	// send DATA1
	// receive ACK1
	// send DATA2
	// receive ACK2
	public void WRQ() {
		int blockNum = 0;
		int TID = -1;
		int receiveTries = 0;
		File file = new File(defaultDirectory.getPath() + "\\" + fileName);
		// CREATE WRQ
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		try {
			if (!file.exists()) {
				System.out
						.println("ERROR 1: File to read from ("
								+ file.getPath()
								+ ") does not exist. Check the folder to ensure that the file exists, then try your request again.");
				return;
			}
			outputStream.write(0);
			outputStream.write(2);
			outputStream.write(fileName.getBytes());
			outputStream.write(0);
			outputStream.write(MODE_NETASCII.getBytes());
			block = outputStream.toByteArray();
			outputStream.close();

			// SEND WRQ (0,2,filename,0,mode,0,0,0,...,0)
			sendPacket = new DatagramPacket(block, block.length, targetMachine,
					DESTINATION_PORT);
			sendReceiveSocket.send(sendPacket);
			System.out.println("Sent WRQ.");

			// Receive ACK0 (0,4,0,0)
			byte[] data = new byte[MAX_PACKET_SIZE+1];
			receivePacket = new DatagramPacket(data, data.length);
			while (receiveTries < 3) {
				try {
					sendReceiveSocket.receive(receivePacket);
					break;
				} catch (SocketTimeoutException e) {
					if (receivePacket.getPort() == -1) {
						receiveTries++;
						System.out
								.println("Receiving ACK 0 timed out, resending WRQ...");
						sendReceiveSocket.send(sendPacket);
					} else
						break;
				}
			}
			if (receiveTries == 3) {
				System.out
						.println("ERROR: Did not receive response from server. Try your request again in a few minutes.");
				return;
			}

			receiveTries = 0;
			TID = receivePacket.getPort();

			// ERROR packet received
			if (data[0] == 0 && data[1] == 5) {
				unpackPacketError(receivePacket);
				return;
			}

			// ACK was not 4 bytes long
			if (receivePacket.getLength() != 4) {
				System.out
						.println("ERROR 4: ACK packet was not correct size. Expected: 4 bytes. Received: "
								+ receivePacket.getLength());
				// make error message
				byte[] errorMsg = generateErrorMessage((short) 4,
						"ERROR 4: ACK packet was not correct size. Expected: 4 bytes. Received: "
								+ receivePacket.getLength());
				sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
						targetMachine, TID);
				sendReceiveSocket.send(sendPacket);
				System.out.println("ERROR 4 sent.");
				return;
			}

			// Wrong Opcode
			if (data[0] != 0 && data[1] != 4) {
				System.out
						.println("ERROR 4: Wrong opcode. Expected: 04. Received: "
								+ data[0] + data[1]);
				// make error message
				byte[] errorMsg = generateErrorMessage((short) 4,
						"Illegal TFTP operation: Wrong opcode. Expected: 04. Received: "
								+ data[0] + data[1]);
				sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
						targetMachine, TID);
				sendReceiveSocket.send(sendPacket);
				System.out.println("ERROR 4 sent.");
				return;
			}

			int receivedBlockNum = bytesToInt(data[2], data[3]);
			if (!(receivedBlockNum == blockNum)) {
				System.out.println("ERROR 4: Wrong block number. Expected: "
						+ blockNum + ". Received: " + receivedBlockNum);
				// make error message
				byte[] errorMsg = generateErrorMessage((short) 4,
						"Received wrong block number. Expected: " + blockNum
								+ ". Received: " + receivedBlockNum);
				sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
						targetMachine, TID);
				sendReceiveSocket.send(sendPacket);
				System.out.println("ERROR 4 sent.");
				return;
			}

			System.out.println("Received ACK 0");
			blockNum++;

			
			if (file.exists()) {
				// if the file exists, then
				FileInputStream in;
				in = new FileInputStream(file);
				int bytesRead;
				boolean dontSendData = false;
				boolean finalACKReceived = false;
				long totalBlocks = (file.length() / 512) + 1;

				do {
					// create DATA (0,3,datax512 bytes)
					if (!dontSendData) {
						byte[] byteData = new byte[BLOCK_SIZE];
						if (!(file.canRead())) {
							System.out.println("ERROR 2: Could not perform file read, access privileges have been revoked.");
							byte[] errorMsg = generateErrorMessage((short) 2,
									"Access violation. "+file.getPath()+" has changed its access rights");

							// Send the error packet to the Client.
							sendPacket = new DatagramPacket(errorMsg, errorMsg.length,
									targetMachine, TID);
							sendReceiveSocket.send(sendPacket);
							System.out.println("ERROR 2 sent.");
							// Can't delete the file if we lost writing permissions
							break;
						}
						bytesRead = in.read(byteData);

						if (bytesRead == -1) {
							bytesRead = 0;
						}

						outputStream = new ByteArrayOutputStream();
						outputStream.write(0);
						outputStream.write(3);
						outputStream.write((byte) ((blockNum >> 8) & 0xff));
						outputStream.write((byte) (blockNum & 0xff));
						outputStream.write(byteData);
						block = outputStream.toByteArray();
						outputStream.close();

						// send DATA
						sendPacket = new DatagramPacket(block, bytesRead + 4,
								targetMachine, TID);
						sendReceiveSocket.send(sendPacket);
						System.out.println("Sent DATA " + blockNum+".");
					} else {
						bytesRead = (blockNum == totalBlocks) ? 0 : BLOCK_SIZE;
					}

					// Receive ACK
					data = new byte[MAX_PACKET_SIZE + 1];
					receivePacket = new DatagramPacket(data, data.length);
					while (receiveTries < 3) {
						try {
							sendReceiveSocket.receive(receivePacket);
							break;
						} catch (SocketTimeoutException e) {
							if (receivePacket.getPort() == -1) {
								receiveTries++;
								System.out.println("Receiving ACK " + blockNum
										+ " timed out, resending DATA "
										+ blockNum + "...");
								sendReceiveSocket.send(sendPacket);
							} else
								break;
						}
					}
					if (receiveTries == 3) {
						System.out
								.println("ERROR: Receiving ACK from server timed out. Terminating transfer.");
						System.out
								.println("Try your request again in a few minutes.");
						break;
					}

					receiveTries = 0;

					// Check where the packet came from
					// If it came from a weird location, loop sending an error
					// and waiting for a new packet
					while (receivePacket.getPort() != TID
							|| !receivePacket.getAddress()
									.equals(targetMachine)) {
						byte[] errorMsg = generateErrorMessage((short) 5,
								"Unknown transfer ID. Who are you and how did you get this IP and port number?");
						// send packet to the invalid source
						sendPacket = new DatagramPacket(errorMsg,
								errorMsg.length, receivePacket.getAddress(),
								receivePacket.getPort());
						sendReceiveSocket.send(sendPacket);
						System.out.println("Sent ERROR 5 to "
								+ receivePacket.getAddress().toString() + ":"
								+ receivePacket.getPort());
						System.out
								.println("Waiting for packet from correct TID...");

						block = new byte[MAX_PACKET_SIZE + 1];
						receivePacket = new DatagramPacket(block, block.length);
						while (receiveTries < 3) {
							try {
								sendReceiveSocket.receive(receivePacket);
								break;
							} catch (SocketTimeoutException e) {
								if (receivePacket.getPort() == -1) {
									receiveTries++;
									System.out.println("Receiving ACK "
											+ blockNum
											+ "from correct TID timed out "
											+ ((receiveTries == 1) ? "once"
													: receiveTries + " times"));
								} else
									break;
							}
						}
						if (receiveTries == 3) {
							System.out
									.println("ERROR: Receiving ACK from server timed out. Terminating transfer.");
							System.out
									.println("Try your request again in a few minutes.");
							in.close();
							return;
						}
					}
					receiveTries = 0;

					// Error received
					if (data[0] == 0 && data[1] == 5) {
						unpackPacketError(receivePacket);
						in.close();
						return;
					}

					// Wrong Opcode
					if (!(data[0] == 0 && data[1] == 4)) {
						System.out
								.println("ERROR 4: Wrong opcode. Expected: 04. Received: "
										+ data[0] + data[1]);
						// make error message
						byte[] errorMsg = generateErrorMessage((short) 4,
								"Illegal TFTP operation: Wrong opcode. Expected: 04. Received: "
										+ data[0] + data[1]);
						sendPacket = new DatagramPacket(errorMsg,
								errorMsg.length, targetMachine, TID);
						sendReceiveSocket.send(sendPacket);
						System.out.println("ERROR 4 sent.");
						in.close();
						return;
					}

					// ACK packet was wrong length
					if (receivePacket.getLength() != 4) {
						System.out
								.println("ERROR 4: ACK was wrong size. Expected: 4 bytes. Received: "
										+ receivePacket.getLength());
						// make error message
						byte[] errorMsg = generateErrorMessage((short) 4,
								"ACK was wrong size. Expected: 4 bytes. Received: "
										+ receivePacket.getLength());
						sendPacket = new DatagramPacket(errorMsg,
								errorMsg.length, targetMachine, TID);
						sendReceiveSocket.send(sendPacket);
						System.out.println("ERROR 4 sent.");
						in.close();
						return;
					}

					// Check block number
					receivedBlockNum = bytesToInt(data[2], data[3]);
					System.out
							.println("Received ACK " + receivedBlockNum + ".");

					// If block number is bigger than expected, send error.
					if (receivedBlockNum > blockNum) {
						System.out
								.println("ERROR 4: Wrong block number. Expected: "
										+ blockNum
										+ ". Received: "
										+ receivedBlockNum);
						// make error message
						byte[] errorMsg = generateErrorMessage((short) 4,
								"Illegal TFTP operation: Wrong block number. Expected: "
										+ blockNum + ". Received: "
										+ receivedBlockNum);
						sendPacket = new DatagramPacket(errorMsg,
								errorMsg.length, targetMachine, TID);
						sendReceiveSocket.send(sendPacket);
						System.out.println("ERROR 4 sent.");
						in.close();
						return;
					}
					// If the block number is smaller than expected, ignore the
					// duplicate ACK.
					else if (receivedBlockNum < blockNum) {
						dontSendData = true;
						finalACKReceived = false;
					} else {
						// Else, the block number is expected, carry on as
						// usual.
						dontSendData = false;
						finalACKReceived = receivedBlockNum == totalBlocks;
						blockNum++;
					}
				} while (!finalACKReceived);
				in.close();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/*
	 * UI(console) to acquire user input
	 */
	public void getUserInput() {
		Scanner input = new Scanner(System.in);

		// Get username - for the default directory
		String username = System.getProperty("user.name");
		System.out.print("Enter server address: ");
		String serverAddr = input.nextLine();
		try {
			targetMachine = InetAddress.getByName(serverAddr);
		} catch (UnknownHostException e) {
			System.out.println("Error setting server address, setting to 127.0.0.1...");
			try {
				targetMachine = InetAddress.getLocalHost();
			} catch (UnknownHostException e1) {
				//this shouldn't happen
			}
		}
		defaultDirectory = new File("C:\\Users\\" + username
				+ "\\TeamWinners\\Client files\\");
		if (!defaultDirectory.exists()) defaultDirectory.mkdirs();
		System.out.println("Client: Directory set to "+defaultDirectory.toString());

		//MODE CHECK
		String mode = new String();
		System.out.println("1 for test mode(error sim included), 2 for real mode (error sim excluded)");
		do {
                    mode = input.nextLine();
                } while (!mode.matches("^1$|^2$"));
		if(Integer.parseInt(mode)==1){
		    DESTINATION_PORT = ERRORSIM_PORT;
		}
		else{
		    DESTINATION_PORT = SERVER_PORT;
		}
		
		
		// Get input from keyboard forever, until a stop command.
		while (true) {
		    String s = new String();
		    System.out
					.println("1 to exit, 2 write request, 3 read request, 4 to change directory, 5 to change server address.");

			do {
				s = input.nextLine();
			} while (!s.matches("^1$|^2$|^3$|^4$|^5$"));
			int RQ = Integer.parseInt(s);

			// exit command
			if (RQ == 1) {
				// close input scanner stream,and socket
				input.close();
				System.out.println("System exiting");
				sendReceiveSocket.close();
				System.exit(1);
			} else if (RQ == 2) {
				System.out.println("File name to to write to (Server side): ");
				fileName = input.next();
				// call upon a write request
				WRQ();
			} else if (RQ == 3) {
				System.out.println("File name to read from (Server side): ");
				fileName = input.next();
				// call upon a read request
				RRQ();
			} else if (RQ == 4) {
				System.out.println("Current filepath is "
						+ defaultDirectory.toString());
				System.out.print("Enter new valid filepath: ");
				File testF = new File(input.nextLine());
				if (!testF.isDirectory()) {
					System.out
							.println("The path you gave is not a valid directory.");
				} else {
					defaultDirectory = testF;
					System.out.println("Test directory updated.");
				}
			} else if (RQ == 5) {
				System.out.print("Enter new server address: ");
				InetAddress oldAddress = targetMachine;
				try {
					targetMachine = InetAddress.getByName(input.nextLine());
					System.out.println("Server address updated.");
				} catch (UnknownHostException e) {
					System.out
							.println("Error: Could not recognize the given hostname. Address not updated.");
					targetMachine = oldAddress;
				}
			}
		}
	}

	public static void main(String args[]) {
		// create the client and call the method getUserInput
		//System.out.println("Creating Client");
		Client c = new Client();
		//System.out.println("Getting input from the user");
		c.getUserInput();
	}

	private void unpackPacketError(DatagramPacket p) {
		byte[] msg = p.getData();
		int msgSize = p.getLength();
		String errorMsg = byteArrayToString(msg, 4, msgSize - 1);

		if (msg[0] == 0 && msg[1] == 5) {
			System.out.print("Client: Received ERROR " + msg[2] + msg[3]
					+ " with message \"");
			System.out.println(errorMsg + "\".");
		}
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
