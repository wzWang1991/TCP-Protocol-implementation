import java.io.*;
import java.net.*;
import java.util.regex.Pattern;



public class Client {
	static String[] argsForReceiver = new String[5];
	static boolean loginFlag = false;
	
	public static void main(String args[]){
		int serverPort=0;
		if(args.length!=7){
			System.out.println("You should use 7 arguments to connect to server.");
			return;
		}
		
		
		String connectIP=args[0];
		//Check IP whether it is valid.
		if(!isIPAddress(connectIP)){
			System.out.println("You should input a valid IP address.");
			return;
		}
		
		//Parse the port number.
		try{
			serverPort=Integer.parseInt(args[1]);
		}catch(Exception e){
			System.out.println("You should input a valid port number.");
			return;
		}
		
		System.arraycopy(args, 2, argsForReceiver, 0, 5);
		
		//Try to connect to server.
		Socket socket = null;
		
		try{
			socket=new Socket(connectIP, serverPort);
		}catch(Exception e){
			System.out.println("Cannot connect to server. Please check and try again.");
			return;
		}
		
		//Create a thread for user input.
		Thread userInput;
		try{
			userInput = new UserInputThread(socket);
			userInput.start();
		}catch(Exception e)
		{
			System.out.println("Cannot create thread for user input.");
			return;
		}
		
		//Main loop for socket receiving.
		try{
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			while(true){
				String tmp = in.readLine();
				if(tmp!=null){
					if(!loginFlag){
						String[] tmpSplit = tmp.split(" ");
						if(tmpSplit[0].equals("Login")) loginFlag=true;
					}
					System.out.println(tmp);
					//System.out.print(">");
				}
				else{
					throw new SocketException();
				}
			}
			
		}catch(SocketException e){
			System.out.println("Socket closed. Press ENTER to exit.");
			try {
				if(!socket.isConnected()) socket.close();
			} catch (IOException e1) {
				System.out.println("Can not close socket.");
				//e1.printStackTrace();
			}
			userInput.interrupt();
			try {
				userInput.join();
			} catch (InterruptedException e1) {
				System.out.println("Thread.join failed.");
				// TODO Auto-generated catch block
				//e1.printStackTrace();
			}
			//System.out.println("Error"+e);
			return;
			
		} catch (IOException e) {
			System.out.println("Something wrong happened with IOstreams.");
			return;
			//e.printStackTrace();
		}
	}
	
	
	public static class UserInputThread extends Thread{
		private Socket socket;
		private BufferedReader userIn;
		PrintWriter dataOut;
		
		public UserInputThread(Socket socket){
			this.socket = socket;
		}
		
		public void run(){
			try{
				userIn=new BufferedReader(new InputStreamReader(System.in));
				dataOut=new PrintWriter(socket.getOutputStream());	
				String readLine = "";
				while(true){
					//System.out.print(">");
					readLine = userIn.readLine();					
					if(Thread.interrupted()) break;
					
					if(readLine.equals("GET") && loginFlag){
						dataOut.println(readLine);
						dataOut.flush();
						receiver fileReceiver = new receiver(argsForReceiver);
						continue;
					}
					
					//If user directly press ENTER;
					if(readLine==null || readLine.hashCode()==0) {
						//System.out.print(">");
						continue;
					}
					dataOut.println(readLine);
					dataOut.flush();
				}
			}catch(Exception e){
				System.out.println(e);
			}
			
		}
		
	}
	
	//Test a string whether it is a IP address. From a blog(http://zfzaizheli.iteye.com/blog/1042179).
	public static boolean isIPAddress( String str )  
	{  
	    Pattern pattern = Pattern.compile( "^((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5]|[*])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5]|[*])$" );  
	    return pattern.matcher( str ).matches();  
	} 
}
