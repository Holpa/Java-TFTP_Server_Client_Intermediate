// SYSC3303 PROJECT TEAM 3 ITERATION 4
// Iteration 3: Jessica Morris
// Iteration 4: Jessica Morris

public interface TFTPHost {
	
	// Define all constants
	public static final int BLOCK_SIZE = 512;
	public static final int MAX_PACKET_SIZE = 516;
	public static final int MIN_DISK_BYTES = 512; // adjust this to check for error 3
	public static final int SERVER_PORT = 69;
	public static final int ERRORSIM_PORT = 68;
	public static final String MODE_NETASCII = "netascii";
	public static final String MODE_OCTET = "octet";
	public static final int SOCKET_TIMEOUT = 2000;
	
}
