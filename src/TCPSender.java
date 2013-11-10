import java.io.DataInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.net.InetAddress;


public class TCPSender {
	//There is no need for sender to send a acknumber, so set it to 0.
	private final long ackNumber=0;

	private final double RTTalpha = 0.125;
	private final double RTTbeta = 0.25;
	private final double DefaultEstimiatedRTT = 100;
	private final double DefaultDevRTT = 25;
	
	//Default max segment size is 576.
	private final int MaxSegmentSize = 576;
	
	private final int DefaultHeadLength = 20;
	
	private String remoteIP;
	private InetAddress remoteInetAddress;
	private int remotePort;
	private int ackPort;
	private int windowSize;
	private volatile long base;
	private volatile long nextSequenceNum;
	
	private Timer timer;
	
	private double estimatedRTT;
	private double devRTT;
	private long timeOutForTCP;
	
	private long totalBytesSent;
	private int totalSegmentsSent;
	private int totalSegmentsRetransmitted;
	
	private DatagramSocket UDPSocket;
	
	//Indicate whether close process is finished.
	private volatile boolean closeFinishFlag;
	
	//The reason why I use volatile here: http://www.cnblogs.com/aigongsi/archive/2012/04/01/2429166.html
	private volatile LinkedBlockingQueue<TCPPacket> sndPacket;
	
	//0 means not in a FIN state, 1 means in FIN_WAIT_1, 2 means in FIN_WAIT_2.
	private int FINWaitState;
	
	private FileWriter logFileHandle;
	
	//If output flag == true, then output to stdout.
	private boolean outputFlag;
	
	public TCPSender(String remoteIP, int remotePort, int ackPort, int windowSize){
		this.remoteIP = remoteIP;
		try {
			this.remoteInetAddress = InetAddress.getByName(this.remoteIP);
		} catch (UnknownHostException e1) {
			System.out.println("No IP address for the host could be found.");
			//e1.printStackTrace();
		}
		
		this.remotePort = remotePort;
		this.ackPort = ackPort;
		this.windowSize = windowSize;
		this.base = 0;
		this.nextSequenceNum = 0;
		try {
			UDPSocket = new DatagramSocket(ackPort);
		} catch (SocketException e) {
			System.out.println("Unable to create a UDP socket.");
			//e.printStackTrace();
		}

		estimatedRTT = DefaultEstimiatedRTT;
		devRTT = DefaultDevRTT;
		timeOutForTCP = (long)(estimatedRTT + 4*devRTT);
		
		FINWaitState = 0;
		
		totalBytesSent = 0;
		totalSegmentsSent = 0;
		totalSegmentsRetransmitted = 0;
		
		timer = new Timer();
		
		sndPacket = new LinkedBlockingQueue<TCPPacket>();
		
		new ackSocket().start();
		
	}
	
	//Send
	public void send(byte[] data){
		for(int i=0;i<data.length;i=i+(MaxSegmentSize-DefaultHeadLength)){
			byte[] dataToSend;
			if(i+MaxSegmentSize-DefaultHeadLength<data.length){
				dataToSend = new byte[MaxSegmentSize-DefaultHeadLength];
				System.arraycopy(data, i, dataToSend, 0, MaxSegmentSize-DefaultHeadLength);
			}else{
				dataToSend = new byte[data.length-i];
				System.arraycopy(data, i, dataToSend, 0, data.length-i);
			}
			while(true){
				//If a segment transmit successfully.
				if(rdt_send(dataToSend)){
					this.totalSegmentsSent++;
					break;
				}
			}
		}
		while(base!=nextSequenceNum);
		
		
	}
	
	//Reliable send
	private boolean rdt_send(byte[] data, String type){
		if(nextSequenceNum<base+windowSize){
			TCPPacket tmpPacket = null;
			if(type.equals("DATA")){
				tmpPacket = new TCPPacket(ackPort,remotePort, nextSequenceNum, ackNumber, "DATA", data, windowSize);
			}else if(type.equals("FIN")){
				tmpPacket = new TCPPacket(ackPort,remotePort, nextSequenceNum, ackNumber, "FIN", data, windowSize);
			}
			sndPacket.add(tmpPacket);

			udt_send(tmpPacket);
			if(base == nextSequenceNum){
				resetTimer();
			}
			nextSequenceNum++;
			return true;
		}
		else return false;	
	}
	
	private boolean rdt_send(byte[] data){
		return rdt_send(data,"DATA");
	}
	
	private boolean rdt_send(String type){
		return rdt_send(null,type);
	}
	
	
	private void udt_send(TCPPacket tcpPacket){
		byte[] sendData = tcpPacket.generatePacket();
		DatagramPacket packet =  new DatagramPacket(sendData, sendData.length, this.remoteInetAddress, this.remotePort);
		try {
			tcpPacket.setSendTime();
			tcpPacket.increaseTransmitTimes();
			writeLog("Src=localhost"+":"+tcpPacket.getSourcePort()+", Des="+remoteIP+":"+tcpPacket.getDestinationPort()+", Seq#="+
					tcpPacket.getSequenceNumber()+", Ack#="+tcpPacket.getAckNum()+", FIN="+tcpPacket.getFIN()+
					", ACK=0, SYN=0, EstimatedRTT="+(long)estimatedRTT+"\n");
			UDPSocket.send(packet);
			this.totalBytesSent = this.totalBytesSent + packet.getLength();
			//System.out.println(new Date().getTime()+" transmit a packet"+tcpPacket.getSequenceNumber());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public long getTotalBytesSent(){
		return this.totalBytesSent;
	}
	
	public int getTotalSegementsSent(){
		return this.totalSegmentsSent;
	}
	
	public int getTotalSegmentsRetransmitted(){
		return this.totalSegmentsRetransmitted;
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
		closeFinishFlag = false;
		while(!sndPacket.isEmpty() ||base != nextSequenceNum);//Wait until all data has been sent.
		FINWaitState = 1;
		rdt_send("FIN");

		while(!closeFinishFlag);
		try {
			UDPSocket.close();
			logFileHandle.close();
		} catch (IOException e) {
			//e.printStackTrace();
		} catch (NullPointerException a){
			
		}
	}
	


	//If timeout, resend all packet from base to nextSequenceNum-1.
	private class PacketTimerTask extends TimerTask{

		@Override
		public void run() {
			
			//If the packet to sent is empty, cancel this job.
			if(sndPacket.isEmpty()) return;
			
			//Reset timer.
			resetTimer();
			
			//Transmit packets which are not received.
			Iterator<TCPPacket> it = sndPacket.iterator();
			while(it.hasNext()){
				TCPPacket packet =it.next();
				udt_send(packet);
				//Increase the number of retransmitted packets.
				totalSegmentsRetransmitted++;
			}
			
			
		}
		
		
	}
	
	//Task for timer. If time out, the task will be executed.
	private PacketTimerTask pktTask = new PacketTimerTask();
	
	
	private void stopTimer(){
		pktTask.cancel();
	}
	
	//Reset the timer, with the newest time out value.
	private void resetTimer(){
		try{
			pktTask.cancel();
			pktTask = new PacketTimerTask();
			
			timer.schedule(pktTask, timeOutForTCP);
		}catch(java.lang.IllegalStateException e){
			//e.printStackTrace();
		}
	}
	
	
	//TCP Socket for ACK.
	private class ackSocket extends Thread{
		Socket socket;
		ServerSocket server;
		
		public ackSocket(){
			server = null;
			try {
				server = new ServerSocket(ackPort);
			} catch (IOException e) {
				System.out.println("Cannot listen to port " + ackPort + ". Please choose another port and try again.");
				System.exit(-1);
				// e.printStackTrace();
			}
			
		}
		
		public void run(){
			try {
				this.socket = server.accept();
			} catch (IOException e) {
				System.out.println("ERROR in accepting a connection.");
				//e.printStackTrace();
			}
			DataInputStream in = null;
			try {
				in = new DataInputStream(socket.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			while(true){
				Long ackReceivedNum = null;
				try {
					byte[] rcvBuf = new byte[576];
					int rcvLength = in.read(rcvBuf);
					if(rcvLength==-1) break;
					byte[] rcvDataInByte = new byte[rcvLength];
					System.arraycopy(rcvBuf, 0, rcvDataInByte, 0, rcvLength);
					TCPPacket ackRcvPacket = new TCPPacket(rcvDataInByte);
					if(!ackRcvPacket.analysePacket()) {
						continue;
					}
					ackReceivedNum = ackRcvPacket.getAckNum();
					writeLog("Src="+socket.getInetAddress().getHostAddress()+":"+socket.getPort()+", Des=localhost"+":"+socket.getLocalPort()+", Seq#="+
							ackRcvPacket.getSequenceNumber()+", Ack#="+ackRcvPacket.getAckNum()+", FIN="+ackRcvPacket.getFIN()+
							", ACK=1, SYN=0, EstimatedRTT="+(int)estimatedRTT+"\n");
				} catch (IOException e) {
					System.out.println("ERROR in receving a ACK packet.");
					//e.printStackTrace();
				}
				
				
				if(FINWaitState==1){
					//Since we received the ack, we can poll the FIN packet from sndPacket.
					if(!sndPacket.isEmpty()) sndPacket.poll();
					FINWaitState=2;
					continue;
				}
				
				if(FINWaitState==2){
					try {
						//Send FIN ack.
						PrintWriter a = new PrintWriter(this.socket.getOutputStream());
						a.println("ACK_FIN");
						a.flush();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						//e.printStackTrace();
					}
					try {
						this.socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						//e.printStackTrace();
					}
					//Now we can cancel the timer.
					timer.cancel();
					//All works on closing connection is done.
					closeFinishFlag = true;
					try {
						server.close();
						this.socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						//e.printStackTrace();
					}
					return;
				}
				 
				
				//Estimate the RTT
				//Check all packets find the packet with the same sequence number with ACK number.
				Iterator<TCPPacket> it = sndPacket.iterator();
				while(it.hasNext()){
					TCPPacket packet =it.next();
					if(packet.getSequenceNumber()==ackReceivedNum){
						//Only if the transmit times of packet is 1 can we calculate the estimated RTT.
						if(packet.getTransmitTimes()==1){
							long sampleRTT = new Date().getTime() - packet.getSendTime();
							estimatedRTT = (1-RTTalpha) * estimatedRTT + RTTalpha * sampleRTT;
							double diffRTT = sampleRTT - estimatedRTT;
							if(diffRTT<0) diffRTT = -diffRTT;
							devRTT = (1-RTTbeta) * devRTT + RTTbeta * diffRTT;
							timeOutForTCP = (long) (estimatedRTT + 4*devRTT);
							if(timeOutForTCP < 10) timeOutForTCP = 10;
						}
					}
				}
				
				
				base = ackReceivedNum + 1;
				//Delete the packet which is acked.
				if(!sndPacket.isEmpty()){
					TCPPacket firstPacket = sndPacket.peek();
					while(firstPacket.getSequenceNumber()<base){
						sndPacket.poll();
						if(sndPacket.isEmpty()) {
							break;
						}
						firstPacket = sndPacket.peek();
					}
				}
				
				if(base == nextSequenceNum){
					//Stop timer
					stopTimer();
				}else{
					//Start timer
					resetTimer();
				}
			}
		}
	}

}
