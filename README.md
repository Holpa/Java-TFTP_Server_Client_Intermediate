Setup Instructions:

1. Start Eclipse
2. Start a new java project (any name will do).
3. Drag the following java files into the src directory under the project
	Client.java
	ErrorSimulator.java
	Server.java
	ServerRequestThread.java
	SocketListener.java
    
    OR just clone the project
    
4. Copy all testXbytes.txt files to your Eclipse workspace (usually C:/Users/yourusername/workspace)
5. Run the classes in this order:
	Server
	ErrorSimulator
	Client
6. In the Server console, enter your username. This will be used to store files for the Server.
7. In the Client console, follow the prompts to perform a read/write request. The Client will use files in your Eclipse workspace.
8. The Server can be stopped any time by writing “stop” into the console. The Client can be stopped after a request by entering “1”.

Testing Example
For a WRQ using the 512-byte text file: In the Client, type 2. Enter “test512bytes.txt” as the file to read from. Enter “test512bytes-WRITE.txt” as the file name to write to. Let the request run. Check the server folder (C:/Users/yourusername/TeamWinners) to find test512bytes-WRITE.txt and verify its contents and size.