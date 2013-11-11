import java.io.DataOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;



public class TCPReceiver {
	private final int MaxSegmentSize=576;
	
	//Delayed ACK. Wait up to 10 msec for arrival of another in-order segment. 
	//If next in-order segment does not arrive in this interval, send an ACK.
	private final int TCPACKDelayTime = 10;
	
	private String remoteIP;
	private int remotePort;
	private int listeningPort;
	
	private long expectedSequenceNum;
	
	private DatagramSocket UDPSocket;
	
	private Socket ackSocket;
	private DataOutputStream ackSender;
	
	private FileWriter logFileHandle;
	
	private boolean outputFlag;
	private boolean endFlag;
	
	private int windowSize;
	
	//Buffer for rcvPacket.
	private volatile LinkedBlockingQueue<TCPPacket> rcvPacketQueue;
	
	private Timer ACKDelayTimer;
	
	private boolean delayACK;
	
	
	
	public TCPReceiver(String remoteIP, int remotePort, int listeningPort){
		this.remoteIP = remoteIP;
		this.remotePort = remotePort;
		this.listeningPort = listeningPort;
		
		
		try {
			this.UDPSocket = new DatagramSocket(listeningPort);
		} catch (SocketException e) {
			System.out.println("The socket can't be opened, or the socket could not be bind to port "+listeningPort+".");
			System.exit(-1);
			//e.printStackTrace();
		}
		ackSocket = null;
	
		this.expectedSequenceNum = 0;
		this.endFlag=false;
		
		this.windowSize = 0;
		
		this.ACKDelayTimer = new Timer();
		this.delayACK = false;
	}
	
	public byte[] receive(){
		byte[] data = null;
		while(true){
			
			DatagramPacket packet=null;
			byte[] buf = new byte[MaxSegmentSize];  
			packet = new DatagramPacket(buf, buf.length);
		
			try {
				UDPSocket.receive(packet);
			} catch (IOException e) {
				System.out.println("I/O error occurs in UDP receiving.");
				//e.printStackTrace();
			}
			
			
			if(ackSocket==null) setUpAckConnection();
			//Get the real packet data.
			byte[] dataBuf = new byte[packet.getLength()];
			System.arraycopy(packet.getData(), 0, dataBuf, 0, packet.getLength());
			
			TCPPacket rcvPacket = new TCPPacket(dataBuf);
			
			//Check the checksum.
			if(!rcvPacket.analysePacket()){
				writeLog("Received a packet with a wrong checksum from " +packet.getAddress().getHostAddress()+".\n");
				if(delayACK) cancelDelayACK();
				sendAck(expectedSequenceNum);
				continue;
			}
			
			//When windowSize is not set, use windowSize in the packet to set the windowSize.
			if(windowSize==0){
				this.windowSize = rcvPacket.getWindowSize();
				//Initialized the buffer for rcvPacket.
				rcvPacketQueue = new LinkedBlockingQueue<TCPPacket>();
			}
			
			
			//Write log file.
			writeLog("Src="+packet.getAddress().getHostAddress()+":"+rcvPacket.getSourcePort()+", Des=localhost:"+rcvPacket.getDestinationPort()+", Seq#="+
					rcvPacket.getSequenceNumber()+", Ack#="+rcvPacket.getAckNum()+", FIN="+rcvPacket.getFIN()+
					", ACK=0, SYN=0\n");
			
			
			//Check the sequence number.
			
			//Sometimes ack losses, so it transmit a ack with expectedSequenceNum.
			if(rcvPacket.getSequenceNumber()<this.expectedSequenceNum){
				if(delayACK) cancelDelayACK();
				sendAck(expectedSequenceNum);
				continue;
			}
			
			if(rcvPacket.getSequenceNumber()>this.expectedSequenceNum && rcvPacket.getSequenceNumber()<this.expectedSequenceNum+this.windowSize){
				//Cancel the delay ACK.
				if(delayACK) cancelDelayACK();
				
				LinkedBlockingQueue<TCPPacket> tmpQueue = new LinkedBlockingQueue<TCPPacket>();
				boolean insertedFlag =false;
				if(!rcvPacketQueue.isEmpty()){
					Iterator<TCPPacket> it = rcvPacketQueue.iterator();
					while(it.hasNext()){
						TCPPacket packetTmp =it.next();
						if(packetTmp.getSequenceNumber()<rcvPacket.getSequenceNumber()) tmpQueue.add(packetTmp);
						else if(packetTmp.getSequenceNumber()>rcvPacket.getSequenceNumber()){
							//If we don't insert the packet we already received.
							if(!insertedFlag){
								tmpQueue.add(rcvPacket);
								tmpQueue.add(packetTmp);
								insertedFlag = true;
							}else{
								tmpQueue.add(packetTmp);
							}
						}else{
							//We already have this packet.
							//Do nothing.
						}
					}
				}
				//If the packet is still not inserted, insert it at the end of queue.
				if(!insertedFlag){
					tmpQueue.add(rcvPacket);
					insertedFlag = true;
				}
				
				rcvPacketQueue = tmpQueue;
				sendAck(expectedSequenceNum);
				continue;
			}
			
			//If the sequence number of rcvPacket is larger than window border, just ignore.
			if(rcvPacket.getSequenceNumber()>=this.expectedSequenceNum+this.windowSize){
				if(delayACK) cancelDelayACK();
				continue;
			}
			
			//If the sequence number equal to expected number.
			if(rcvPacket.getSequenceNumber()==this.expectedSequenceNum){	
				if(rcvPacketQueue.isEmpty()){
					//We don't want to delay the FIN ack.
					if(rcvPacket.getFIN()!=1){
						if(!delayACK) setDelayACK();
						else{
							cancelDelayACK();
							setDelayACK();
						}
					}else{
						sendAck(expectedSequenceNum);
					}
					//else sendAck(expectedSequenceNum);
					data = rcvPacket.getDataInByte();
					expectedSequenceNum++;
				}else{
					if(delayACK) cancelDelayACK();
					//Counter for how many bytes should be returned.
					int ackCounter = rcvPacket.getDataInByte().length;
					//Check for queue, find continuous numbers.
					long reSeq = this.expectedSequenceNum;

					Iterator<TCPPacket> it = rcvPacketQueue.iterator();
					while(it.hasNext()){
						TCPPacket packetTmp =it.next();
						//If it's the continuous packet.
						if(packetTmp.getSequenceNumber()==reSeq+1){
							ackCounter+=packetTmp.getDataInByte().length;
							reSeq++;
						}else{
							break;
						}
					}
					
					//Total packets to be returned;
					int totalPacketsToReturn = (int) (reSeq - this.expectedSequenceNum);
					
					//Total data of packet that should be return is ackCounter;
					data = new byte[ackCounter];
					
					//Save the data from rcvPacket.
					int dataPointer = 0;
					System.arraycopy(rcvPacket.getDataInByte(), 0, data, 0, rcvPacket.getDataInByte().length);
					dataPointer+=rcvPacket.getDataInByte().length;
					
					//Save the data from packets already in queue.
					for(int i=0;i<totalPacketsToReturn;i++){
						TCPPacket packetInQueue = rcvPacketQueue.poll();
						System.arraycopy(packetInQueue.getDataInByte(), 0, data, dataPointer, packetInQueue.getDataInByte().length);
						dataPointer+=packetInQueue.getDataInByte().length;
					}
					expectedSequenceNum = reSeq + 1;
					sendAck(expectedSequenceNum);
				}
			}
			
			
			
			//If it is a FIN, send a FIN.
			if(rcvPacket.getFIN()==1){
				try {
					//Sleep for 10ms, and send FIN.
					Thread.sleep(10);
				} catch (InterruptedException e) {
					//e.printStackTrace();
				}
				TCPPacket FINPacket = new TCPPacket(listeningPort,remotePort,0,0,"FIN",null, windowSize);
				sendMessage(FINPacket.generatePacket());
				writeLog("Src=localhost:"+ackSocket.getLocalPort()+", Des="+ackSocket.getInetAddress().getHostAddress()+":"+ackSocket.getPort()+", Seq#="+
						FINPacket.getSequenceNumber()+", Ack#="+FINPacket.getAckNum()+", FIN="+FINPacket.getFIN()+
						", ACK=0, SYN=0\n");
				endFlag = true;
				return null;
			}		
			break;
		}
		return data;
		
	}
	
	private void setUpAckConnection(){
		try {
			ackSocket = new Socket(remoteIP, remotePort);
			ackSender=new DataOutputStream(ackSocket.getOutputStream());	
		} catch (UnknownHostException e) {
			System.out.println("Can't connect to "+remoteIP+":"+remotePort+"." );
			//e.printStackTrace();
		} catch (IOException e) {
			System.out.println("I/O error in setting up ACK connection.");
			//e.printStackTrace();
		}
	}
	
	private void sendAck(long ackNum){
		long newAckNum = 0;
		if(ackNum<0) newAckNum = 0;
		else newAckNum = ackNum;
		TCPPacket ackPacket = new TCPPacket(listeningPort,remotePort,0,newAckNum,"ACK",null, windowSize);
		writeLog("Src=localhost:"+ackSocket.getLocalPort()+", Des="+ackSocket.getInetAddress().getHostAddress()+":"+ackPacket.getDestinationPort()+", Seq#="+
				ackPacket.getSequenceNumber()+", Ack#="+ackPacket.getAckNum()+", FIN="+ackPacket.getFIN()+
				", ACK=1, SYN=0\n");
		byte[] ackToSend = ackPacket.generatePacket();
		try {
			ackSender.write(ackToSend);
			ackSender.flush();
		} catch(SocketException a){
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		
	}
	
	private void sendMessage(byte[] message){
		try {
			ackSender.write(message);
			ackSender.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		
	}	
	
	public void setLogFile(String logFile){
		if(!logFile.equals("stdout")){
			outputFlag = false;
			try {
				logFileHandle = new FileWriter(logFile);
			} catch (IOException e) {
				System.out.println("Can't create file: "+logFile);
				//e.printStackTrace();
			}
		}else{
			outputFlag = true;
		}
	}
	
	//Write a log with timestamp.
	private void writeLog(String log){
		Calendar time = Calendar.getInstance();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		writeLogToFile(formatter.format(time.getTime())+", "+log);
	}
	
	//Write a new line to log file.
	private void writeLogToFile(String log){
		if(!outputFlag){
			try {
				logFileHandle.write(log);
			} catch (IOException e) {
				System.out.println("Can't write to log file.");
				//e.printStackTrace();
			}
		}else{
			System.out.print(log);
		}
	}
	
	public void close(){
		while(!endFlag);
		//cancelDelayACK();
		ACKDelayTimer.cancel();
		try {
			UDPSocket.close();
			ackSocket.close();
			logFileHandle.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}catch (NullPointerException a){
			
		}
		
	}
	
	//Task for delay ack.
	private ACKDelayTask ackDelayTask = new ACKDelayTask();
	
	//If timeout, send the ACK.
	private class ACKDelayTask extends TimerTask{
		@Override
		public void run() {
			sendAck(expectedSequenceNum);
		}	
	}
	
	private void cancelDelayACK(){
		ackDelayTask.cancel();
		delayACK = false;
	}
	
	private void setDelayACK(){
		delayACK = true;
		ackDelayTask = new ACKDelayTask();
		ACKDelayTimer.schedule(ackDelayTask, TCPACKDelayTime);
	}
	
}
