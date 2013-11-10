import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


public class sender {

	public sender(String[] args){
		main(args);
	}
	
	public static void main(String[] args) {
		if(args.length!=6){
			System.out.println("You should use proper arguments.");
			return;
		}
		String fileName = args[0];
		String remoteIP = args[1];
		String remotePortString = args[2];
		String ackPortString = args[3];
		String windowSizeString = args[4];
		String logFileName = args[5];
		
		int remotePort = Integer.parseInt(remotePortString);
		int ackPort = Integer.parseInt(ackPortString);
		int windowSize = Integer.parseInt(windowSizeString);
		if(windowSize<1 || windowSize >65535){
			System.out.println("Window size should be between 1 and 65535.");
			return;
		}
		
		TCPSender fileSender = new TCPSender(remoteIP,remotePort,ackPort,windowSize);
		fileSender.setLogFile(logFileName);
		
		//Read content from file.
		FileInputStream in;
		DataInputStream dis = null;
		try {
			in = new FileInputStream(fileName);  
	        dis=new DataInputStream(in); 
		} catch (FileNotFoundException e) {
			System.out.println("Can't find the file with name "+fileName);
			
			//e.printStackTrace();
		}
		

		byte[] packetDataBuf;
		try {
			while(true){
				packetDataBuf = new byte[556*windowSize*100]; 
				int readCounter = dis.read(packetDataBuf, 0, 556*windowSize*100);
				if(readCounter==-1) break;
				byte[] packetData = new byte[readCounter];
				System.arraycopy(packetDataBuf, 0, packetData, 0, readCounter);
				fileSender.send(packetData);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		fileSender.close();
		
		System.out.println("Delivery completed successfully.");
		System.out.println("Total bytes sent = "+fileSender.getTotalBytesSent());
		System.out.println("Segments sent = "+fileSender.getTotalSegementsSent());
		System.out.println("Segments retransmitted = "+fileSender.getTotalSegmentsRetransmitted());
		//System.exit(0);

	}

}
