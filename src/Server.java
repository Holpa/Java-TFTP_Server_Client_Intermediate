// SYSC3303 PROJECT TEAM 3 ITERATION 4
// Iteration 1: Jessica Morris
// Iteration 2: No updates
// Iteration 3: No updates
// Iteration 4: No updates
// Iteration 5: 

import java.io.File;
import java.util.Scanner;

public class Server implements TFTPHost {

	private static int numRequests;
	private static boolean verboseOn;
	private static SocketListener sl;
	protected static File defaultDirectory;

	/*
	 * Constructor. Starts the SocketListener thread.
	 */
	public Server() {
		numRequests = 0;
		verboseOn = true;

		sl = new SocketListener(SERVER_PORT, this);
	}

	/*
	 * Increments numRequests. Used to keep track of the number of
	 * ServerRequestThreads.
	 */
	public void addRequest() {
		numRequests++;
	}

	/*
	 * Decrements numRequests. Used to keep track of the number of
	 * ServerRequestThreads.
	 */
	public void removeRequest() {
		numRequests--;
	}

	public int getNumberOfRequests() {
		return numRequests;
	}

	public boolean verboseOn() {
		return verboseOn;
	}

	/*
	 * Main method. Handles initializing the server. The main thread will handle
	 * commands from the keyboard.
	 */
	public static void main(String args[]) {
		Server server = new Server();
		Scanner input = new Scanner(System.in);

		String username = System.getProperty("user.name");
		defaultDirectory = new File("C:\\Users\\" + username
				+ "\\TeamWinners\\Server files\\");
		if (!defaultDirectory.exists()) {
			defaultDirectory.mkdirs();
		}
		sl.start();

		System.out
				.println("Server: All starting threads initialized.\nEnter HELP for a list of commands.");
		System.out.println("Server: Default directory set to "
				+ defaultDirectory.toString());

		// Get input from keyboard forever, until a stop command.
		while (true) {
			String command = input.nextLine();

			// Blank command
			if (command.length() == 0)
				continue;

			// Stop command
			else if (command.matches("(?i)stop")) {
				server.stop();
				input.close();
			}

			// Toggle verbose mode on/off
			else if (command.matches("(?i)verb")) {
				verboseOn = !verboseOn;
				System.out.println("Server: Verbose mode "
						+ (verboseOn ? "ON" : "OFF"));
			}

			// Change directory
			else if (command.matches("(?i)chdir.*")) {
				File newPath = new File(command.substring(6));
				
				// Catch any bad inputs right away
				if (!newPath.exists()) {
					System.out
							.println("Server: ERROR, can't change directory. That path does not exist.");
				} else if (newPath.isHidden()) {
					System.out
							.println("Server: ERROR, can't change directory. That path is hidden.");
				} else if (!newPath.canRead()) {
					System.out
							.println("Server: ERROR, can't change directory. You do not have permission to read from that path.");
				}
				
				// If the input was valid:
				else {
					boolean changePath = true;
					if (!newPath.canWrite()) {
						System.out
								.print("Server: WARNING, you do not have write permissions for that directory. Server will be unable to satisfy WRQs. Continue (Y/N)?: ");
						do {
							command = input.nextLine();
						} while (!command.matches("(?i)^y$|^n$"));
						
						if (command.toLowerCase().equals("n")) {
							changePath = false;
							System.out.println("Server: Directory unchanged.");
						}

					}
					
					// Wait until all requests have finished before changing the directory.
					if (changePath) {
						if (numRequests > 0) System.out.println("Server: Waiting for requests to finish...");
						while (numRequests > 0) {
							continue;
						}
						
						defaultDirectory = newPath;
						System.out.println("Server: Directory changed to new path.");
					}
					
				}
				
			} // end Change directory

			// Print help
			else if (command.matches("(?i)help")) {
				System.out
						.println("------------------------\nAvailable commands:");
				System.out
						.println("\tSTOP\tStops the server after any requests in progress have run.");
				System.out
						.println("\tVERB\tToggles verbose mode on/off. Currently: "
								+ (verboseOn ? "ON" : "OFF"));
				System.out
						.println("\tCHDIR\tChanges the server directory. Syntax: CHDIR C:/folder/mynewdirectory");
				System.out.println("\t-> Current directory: "+defaultDirectory.toString());
				System.out.println("------------------------\n");
			}

			// Wasn't a command
			else {
				System.out.println("Server: Could not recognize command.");
			}
		}
	}

	/*
	 * Stops the server, after all threads have completed their requests.
	 */
	public void stop() {
		System.out.println("Server: STOP acknowledged.");
		sl.requestStop();

		// Wait for SocketListener and its reader/writer threads
		try {
			sl.join();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("Server: All threads have exited. Shutting down.");
		System.exit(0);
	}

}
