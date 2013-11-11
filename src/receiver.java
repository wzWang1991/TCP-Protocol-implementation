import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


public class receiver {

	
	public receiver(String[] args){
		main(args);
	}
	
	public static void main(String[] args) {
		if(args.length!=5){
			System.out.println("You should use proper arguments.");
			return;
		}
		String fileName = args[0];
		String listeningPortString = args[1];
		String remoteIP = args[2];
		String remotePortString = args[3];
		String logFileName = args[4];
		
		int listeningPort = Integer.parseInt(listeningPortString);
		int remotePort = Integer.parseInt(remotePortString);
		
		TCPReceiver fileReceiver = new TCPReceiver(remoteIP, remotePort, listeningPort);
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(fileName);
		} catch (FileNotFoundException e) {
			System.out.println("Can't create the file with a name "+fileName+" or it can't be opened." );
			System.exit(-1);
			//e.printStackTrace();
		}  
        
		fileReceiver.setLogFile(logFileName);
		
		while(true){
			byte[] tmp = fileReceiver.receive();
			if(tmp==null) break;
			try {
				out.write(tmp);
			} catch (IOException e) {
				System.out.println("ERROR in writting files.");
				//e.printStackTrace();
			}
		}
		try {
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		fileReceiver.close();
		System.out.println("Delivery completed successfully.");

	}

}
	

