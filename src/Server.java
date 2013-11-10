import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Pattern;

public class Server {
	//Default port for server.
	final static int DEFAULT_PORT = 20137;
	//Block time, in seconds.
	final static int BLOCK_TIME = 60;
	//Used in wholasthr command, in minutes.
	final static int WHOLASTHR_TIME = 60;
	//The switch of displaying the log records.
	static boolean logFlag = true;
	//Save the username and password from file.
	static HashMap<String, String> userMap = new HashMap<String, String>();
	// Save the users who are connecting to server
	static ArrayList<User> userOnlineList = new ArrayList<User>();
	// Save the users who have connected to server
	static ArrayList<User> userTotalList = new ArrayList<User>();
	// Save clients who are blocked
	static ArrayList<User> userBlockList = new ArrayList<User>();
	
	static boolean userGETFlag;

	// Class to describe a user, saving username, logintime, its socket and status.
	public static class User {
		public String userName;
		public Calendar loginTime;
		public Socket socket;
		public Boolean online;

		public User(String userName, Calendar loginTime, Socket socket) {
			this.userName = userName;
			this.loginTime = loginTime;
			this.socket = socket;
			this.online = true;
		}
	}

	// Read file to get usernames and passwords. Save them to a hashmap.
	public static void readFile(String file, HashMap<String, String> userMap) {
		try {
			FileReader f = new FileReader(file);
			BufferedReader br = new BufferedReader(f);
			
			String oneLine;
			while ((oneLine = br.readLine()) != null) {
				String[] lineSpirit = oneLine.split(" ");
				userMap.put(lineSpirit[0], lineSpirit[1]);
				//logOutput(lineSpirit[0]+lineSpirit[1]);
			}
			br.close();
			f.close();
		} catch (IOException e) {
			System.out.println("Can't open userpass.ini");
			System.exit(-1);
			//e.printStackTrace();
		}
	}
	
	//Arguments for sender
	static String[] argsForSender = new String[6];
	public static void main(String args[]) {
		int listenPort = DEFAULT_PORT;
		
		if(args.length!=7){
			System.out.println("You should enter proper arguments.");
			System.exit(-1);
		}else{
			listenPort = Integer.parseInt(args[0]);
			System.arraycopy(args, 1, argsForSender, 0, 6);
		}

		readFile("userpass.ini", userMap);
		logOutput("Reading User-Password file from userpass.ini...");
		
		// Listening to a port.
		ServerSocket server = null;
		try {
			server = new ServerSocket(listenPort);
			logOutput("Creating SeverSocket...");
		} catch (IOException e) {

			System.out.println("Cannot listen to port " + listenPort + ". Please choose another port and try again.");
			System.exit(-1);
			// e.printStackTrace();
		}
		logOutput("Server is listening port " + listenPort +"...");

		// Start a new socket thread to accept
		try {
			while (true) {
				new SocketThread(server.accept()).start();
				// System.out.println("Accepted.");
			}
		} catch (Exception e) {
			System.out.println("Error:" + e);
		} finally {
			try {
				if(!server.isClosed()) server.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	// Thread for accepting a client.
	public static class SocketThread extends Thread {
		private Socket socket;
		private Calendar loginTime;
		private BufferedReader in;
		private PrintWriter out;
		private User newUser;

		public SocketThread(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			logOutput("A new client accepted.");
			try {
				in = new BufferedReader(new InputStreamReader(
						socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream());

			} catch (IOException e) {
				e.printStackTrace();
			}

			// Check whether this client is in the block list.
			for (int i = 0; i < userBlockList.size(); i++) {
				User userTmp = userBlockList.get(i);
				//Check the client if it meets any blocked user.
				if (socket.getInetAddress().getHostAddress().equals(userTmp.socket.getInetAddress().getHostAddress())) {
					Long timeDifference = Calendar.getInstance().getTimeInMillis() - userTmp.loginTime.getTimeInMillis();
					if (timeDifference < BLOCK_TIME * 1000) {
						out.println("You have been blocked at "+userTmp.loginTime.getTime()+".");
						//out.println("CLOSESOCKET");
						out.flush();
						logOutput("IP address "+ socket.getInetAddress().getHostAddress() +" was blocked. The socket will be closed.");
						try {
							socket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						return;
					} else {
						//The block time passed. Remove it from block list for efficiency.
						userBlockList.remove(userTmp);
					}
				}
			}                                                 

			String inputUsername = null;
			String inputPassword = null;
			int tryTimes = 0;
			
			//Login process loop.
			while (true) {
				//Check if user has tried for 3 times.
				if (tryTimes == 3) {
					// Add this user to block list.
					User blockUser = new User(inputUsername, Calendar.getInstance(), socket);
					userBlockList.add(blockUser);
					logOutput("A client IP " + socket.getInetAddress().getHostAddress() + " has been blocked.");
					out.println("Sorry, you have tried 3 times. Your IP address will be blocked for 60 seconds.");
					//out.println("CLOSESOCKET");
					out.flush();
					try {
						socket.close();
					} catch (IOException e) {
						System.out.println("Something wrong happened when trying to close the socket. ERROR: "+e);
						//e.printStackTrace();
					}
					return;
				}
				
				//Ask user to input username and password.
				try {
					out.println("Username:");
					out.flush();
					inputUsername = in.readLine();
					out.println("Password:");
					out.flush();
					inputPassword = in.readLine();
					//Receiving a null indicating that the socket is closed.
					//So we close the socket and return.
					if(inputUsername==null || inputPassword==null){
						throw new SocketException();
					}
				} catch (IOException e) {
					try {
						
						socket.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					logOutput("The client("+ socket.getInetAddress().getHostAddress()+":"+socket.getPort() +") close the socket.");
					//e.printStackTrace();
					return;
				}
				String savedPassword = userMap.get(inputUsername);
				
				//If we can't find the user
				if (savedPassword == null) {
					out.println("Username doesn't exist!");
					out.flush();
					tryTimes++;
					continue;
				} else {
					//Successfully find the user in the table. Check the password.
					if (!savedPassword.equals(inputPassword)) {
						// System.out.println(savedPassword+inputPassword);
						out.println("Wrong password, please retry!");
						out.flush();
						tryTimes++;
						continue;
					} else {
						// Login successfully. Create a new user and insert it
						// in the table.
						logOutput("User "+ inputUsername + " login successfully.");
						loginTime = Calendar.getInstance();
						newUser = new User(inputUsername, loginTime, socket);
						//Add user to onlineList.
						userOnlineList.add(newUser);
						
						//Before add a user to totalList, we have to confirm that this user name is not in totalList, unless we have to update this record.
						for(int i=0;i<userTotalList.size();i++){
							if(userTotalList.get(i).userName.equals(newUser.userName)){
								userTotalList.remove(i);
							}
						}
						userTotalList.add(newUser);
						
						out.println("Login successfully. Welcome, " + inputUsername+"!");
						out.flush();
						break;
					}
				}
			}
			
			//Main loop for serving a client.
			while (true) {
				String command = null;
				try {
					command = in.readLine();
					//If the client disconnect from server, the command is null.
					if(command==null){
					throw new SocketException();
					}
					logOutput("Command received from user " + inputUsername + ": " + command);
				} catch (IOException e) {
					removeUser(newUser);
					logOutput(inputUsername + "(" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + ") disconnected.");
					try {
						socket.close();
					} catch (IOException e1) {
						System.out.println("Something wrong happened when trying to close the socket. ERROR: "+e);
						//e1.printStackTrace();
					}
					return;
					//e.printStackTrace();
				}
				
				//Process the message from client.
				switch (decodeMessage(command,newUser)) {
				
				case 0:// Null command.
					break;
					
				case -1:// Command can't be decoded.
					out.println("Server cannot recognize your command. Please check and try again.");
					out.flush();
					break;
					
				case 1:// whoelse
					if (userOnlineList.size() == 1) {
						out.println("No other users online now.");
						out.flush();
						break;
					}
					
					//Check if there are other users.
					boolean otherUserFlag = false;
					for (int i = 0; i < userOnlineList.size(); i++) {
						if (!userOnlineList.get(i).userName.equals(newUser.userName)) {
							otherUserFlag=true;
							break;
						}
					}
					
					if (otherUserFlag) {
						out.printf("%-10s %-20s %-6s\n", "USERNAME",
								"IP ADDRESS", "PORT");

						for (int i = 0; i < userOnlineList.size(); i++) {
							if (!userOnlineList.get(i).userName
									.equals(newUser.userName)) {
								User userTmp = userOnlineList.get(i);
								out.printf("%-10s %-20s %-6s\n",
										userTmp.userName, userTmp.socket
												.getInetAddress()
												.getHostAddress(),
										userTmp.socket.getPort());
							}
						}
						out.flush();
					}
					else{
						out.println("No other users online now.");
						out.flush();
					}
					break;
					
				case 2:// wholasthr
					Calendar nowTime = Calendar.getInstance();
					DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					out.printf("%-10s %-15s %-6s %-20s %-10s\n", "USERNAME",
							"IP ADDRESS", "PORT", "LOGIN/ACTIVE TIME", "CONNECTED");
					for (int i = 0; i < userTotalList.size(); i++) {
						User userTmp = userTotalList.get(i);
						boolean outputFlag=false;
						if(userTmp.online) outputFlag=true;
						else{
							Long timeDifference = nowTime.getTimeInMillis() - userTmp.loginTime.getTimeInMillis();
							if (timeDifference < WHOLASTHR_TIME * 60 * 1000) outputFlag=true;
						}
						if(outputFlag){
							out.printf("%-10s %-15s %-6s %-20s %-10s\n",
									userTmp.userName,
									userTmp.socket.getInetAddress().getHostAddress(),
									userTmp.socket.getPort(),
									df.format(userTmp.loginTime.getTime()),
									userTmp.online);
							out.flush();
						}
					}
					break;
					
				case 3:// broadcast
					//Get the words user want to broadcast.
					String broadcastWords = command.substring(10);
					for (int i = 0; i < userOnlineList.size(); i++) {
						if (!userOnlineList.get(i).userName.equals(newUser.userName)) {
							try {
								User userTmp = userOnlineList.get(i);
								PrintWriter tmpOut = new PrintWriter(
										userTmp.socket.getOutputStream());
								tmpOut.println("[Broadcast]["
										+ newUser.userName+"("
										+ newUser.socket.getInetAddress().getHostAddress() + ":"
										+ newUser.socket.getPort() + ")]"
										+ broadcastWords);
								tmpOut.flush();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
					out.println("Broadcast message was sent successfully!");
					out.flush();
					break;
					
				case 4:// logout
					removeUser(newUser);
					try {
						PrintWriter tmpOut = new PrintWriter(newUser.socket.getOutputStream());
						tmpOut.println("Logout successfully.");
						//tmpOut.println("CLOSESOCKET");
						tmpOut.flush();
						newUser.socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					return;
				
				case 5://PM(Personal message)
					String[] PMSplit = command.split(" ");
					//Check the command arguments.
					if(PMSplit.length<4){
						out.println("You should enter \"PM IP PORT message\"");
						out.flush();
						break;
					}
					
					//Check if the first argument is a IP address.
					String PMIP=PMSplit[1];
					if(!isIPAddress(PMIP)){
						out.println("You should use a valid IP address");
						out.flush();
						break;
					}
					int PMPort;
					try{
						PMPort = Integer.parseInt(PMSplit[2]); 
					}catch(Exception e){
						out.println("Can not recognize the Port you input. Please check it again.");
						break;
					}
					
					//Make sure the message will not be sent to user himself.
					if(PMIP.equals(newUser.socket.getInetAddress().getHostAddress()) && PMPort==newUser.socket.getPort()){
						out.println("You can not send a message to yourself.");
						out.flush();
						break;
					}
					
					String PMMessage=PMSplit[3];
					for(int i=4;i<PMSplit.length;i++){
						PMMessage+=" "+PMSplit[i];
					}
					
					//Try to find the IP:Port in the userOnlineTable.
					boolean PMfindFlag=false;
					for (int i = 0; i < userOnlineList.size(); i++) {
						if (userOnlineList.get(i).socket.getInetAddress().getHostAddress().equals(PMIP) && userOnlineList.get(i).socket.getPort()==PMPort) {
							User userTmp = userOnlineList.get(i);
							PrintWriter tmpOut;
							try {
								tmpOut = new PrintWriter(userTmp.socket.getOutputStream());
								tmpOut.println("[PM][From "+ newUser.userName + "(" +socket.getInetAddress().getHostAddress()+":"+socket.getPort()+")]"+PMMessage);
								tmpOut.flush();
								PMfindFlag=true;
								break;
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
					
					//Can not find the destination.
					if(!PMfindFlag){
						out.println("Can not find the IP:Port you want to PM. You can use whoelse to check whether he or she is online.");
						out.flush();
					}else{//Find the destination, success message.
						out.println("PM message was sent successfully!");
						out.flush();
					}
					break;
					
				case 6://PM a user.
					String[] PMuserSplit = command.split(" ");
					if(PMuserSplit.length<3){
						out.println("You should enter \"PMuser username message\"");
						out.flush();
						break;
					}
					
					//Regenerate the message user want to send.
					String PMuserMessage=PMuserSplit[2];
					for(int i=3;i<PMuserSplit.length;i++){
						PMuserMessage+=" "+PMuserSplit[i];
					}
					
					//Find the PM username in the table.
					String PMusername=PMuserSplit[1];
					
					//Check if username equals to name of this user. User can not send messages to himself.
					if(PMusername.equals(newUser.userName)){
						out.println("You can not send a message to yourself.");
						out.flush();
						break;
					}
					
					boolean PMuserfindFlag=false;
					for (int i = 0; i < userOnlineList.size(); i++) {
						if (userOnlineList.get(i).userName.equals(PMusername)) {
							User userTmp = userOnlineList.get(i);
							PrintWriter tmpOut;
							try {
								tmpOut = new PrintWriter(userTmp.socket.getOutputStream());
								tmpOut.println("[PM][From "
										+ newUser.userName
										+ "("
										+ socket.getInetAddress()
												.getHostAddress() + ":"
										+ socket.getPort() + ")]"
										+ PMuserMessage);
								tmpOut.flush();
								PMuserfindFlag=true;
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
					
					//Can not find the user.
					if(!PMuserfindFlag){
						out.println("Can not find the user you want to PM. You can use whoelse to check whether he or she is online");
						out.flush();
					}else{//Find the user.
						out.println("PMuser message was sent successfully!");
						out.flush();
					}
					break;
					
				case 7://GET
					sender fileSender = new sender(argsForSender);
				}
			}
		}
		



		//Function: remove a user from table.
		private void removeUser(User user){
			//Firstly, remove this user from onlineList.
			userOnlineList.remove(user);
			
			//Secondly we have to check if this username is used by multiple clients which are connecting to server.
			boolean userMultipleLoginFlag=false;
			for(int i=0;i<userOnlineList.size();i++){
				if(user.userName.equals(userOnlineList.get(i).userName)){
					userMultipleLoginFlag=true;
				}
			}
			//If this username is only used by one client, we can set its status to inactive.
			if(!userMultipleLoginFlag){
				//DEBUG:The user element saved in totalList may be not the user we want to set status now.
				//There is only one name in TotalList, so we can directly try to find the index of that name.
				for(int i=0;i<userTotalList.size();i++){
					if(userTotalList.get(i).userName.equals(user.userName)){
						userTotalList.get(i).loginTime = Calendar.getInstance();
						userTotalList.get(i).online = false;
						break;
					}
				}
				
			}
			
		}
	}
	
	
	// Function to decode message.
	public static int decodeMessage(String message, User user) {
		//If receive any message from user, update the active time.
		updateActiveTime(user);
		if (message == null)
			return 0;
		String[] messageSplit;
		try {
			messageSplit = message.split(" ");
		} catch (Exception e) {
			return -1;
		}
		if (messageSplit[0].equals("whoelse"))
			return 1;
		if (messageSplit[0].equals("wholasthr"))
			return 2;
		if (messageSplit[0].equals("broadcast"))
			return 3;
		if (messageSplit[0].equals("logout"))
			return 4;
		if (messageSplit[0].equals("PM"))
			return 5;
		if (messageSplit[0].equals("PMuser"))
			return 6;
		if (messageSplit[0].equals("GET"))
			return 7;
		//If cannot recognize the command, return -1.
		return -1;
	}
	



	private static void updateActiveTime(User user) {
		for(int i=0;i<userTotalList.size();i++){
			if(userTotalList.get(i).userName.equals(user.userName)){
				userTotalList.get(i).loginTime=Calendar.getInstance();
			}
		}
	}


	//Convenient for output logs.
	static void logOutput(String msg){
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		if(logFlag) System.out.println("[LOG]["+df.format(new Date())+"]"+msg);
	}
	
	//Test a string whether it is a IP address. From a blog(http://zfzaizheli.iteye.com/blog/1042179).
	public static boolean isIPAddress( String str )  
	{  
	    Pattern pattern = Pattern.compile( "^((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5]|[*])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5]|[*])$" );  
	    return pattern.matcher( str ).matches();  
	} 
}
