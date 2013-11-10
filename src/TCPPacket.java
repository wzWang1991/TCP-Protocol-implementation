import java.util.Date;


public class TCPPacket{
	private final int PacketHeadSize = 20;

	//Packet in bytes.
	private byte[] packetInByte;
	//Head of the packet.
	private byte[] packetHead;
	//Data of the packet.
	private String data;
	//Source port
	private int sourcePort;
	//Destination port
	private int destinationPort;

	//In java, int is a 4-byte long number. But it can't be a unsigned number java. Using long will be more convenient
	private long sequenceNumber;
	private long ackNumber;

	private int windowSize;

	private int ACK;
	private int SYN;
	private int FIN;

	//Data in byte.
	private byte[] dataInByte;
	//Source port in byte
	private byte[] sourcePortInByte;
	//Destination port in byte
	private byte[] destinationPortInByte;

	private byte[] sequenceNumberInByte;
	private byte[] ackNumberInByte;

	private byte[] windowSizeInByte;

	private byte[] checksum;

	private byte[] urgentPointerInByte;
	//Data offset(0:3) and part of reserved bit(4:7)
	private byte dataOffset_reservedBits;
	//16 Bit long. In fact, the first 2 bits are reserverd bits, should be set to zero.
	private byte controlBits;
	
	//For sender, save the time when the packet is sent.
	private long sendTime;
	
	//For sender, save how many times a packet has been sent.
	private int transmitTimes;


	//Initial a packet with some parameter.
	public TCPPacket(int sourcePort, int destinationPort, long sequenceNumber, long ackNumber, String packetType, byte[] data, int windowSize){
		packetHead = new byte[20];
		this.sourcePort = sourcePort;
		this.destinationPort = destinationPort;
		this.sequenceNumber = sequenceNumber;
		this.ackNumber = ackNumber;
		this.windowSize = windowSize;
		if(packetType.equals("ACK")) this.ACK=1;
		else this.ACK=0;
		if(packetType.equals("SYN")) this.SYN=1;
		else this.SYN=0;
		if(packetType.equals("FIN")) this.FIN=1;
		else this.FIN=0;
		this.dataInByte = data;
		generateByteData();
		
		//Initial transmit times is 0.
		this.transmitTimes=0;
	}

	//Initial object with a packet in bytes.
	public TCPPacket(byte[] packetInByte){
		this.packetInByte = packetInByte;
		initialBytes();
	}

	//Set the data of object.
	public void setData(String data){
		this.data = data;
		this.dataInByte = data.getBytes();
	}

	private void initialBytes(){
		this.sourcePortInByte = new byte[2];
		this.destinationPortInByte = new byte[2];
		this.sequenceNumberInByte = new byte[4];
		this.ackNumberInByte = new byte[4];
		this.windowSizeInByte = new byte[2];
		this.checksum = new byte[2];
		this.urgentPointerInByte = new byte[2];

	}
	
	//Convert source port, destination port and other information into byte.
	private void generateByteData(){
		//if(data.equals("")) this.dataInByte = null;
		//else this.dataInByte = data.getBytes();

		sourcePortInByte = convertEightBitIntToByte(sourcePort);
		destinationPortInByte = convertEightBitIntToByte(destinationPort);

		sequenceNumberInByte = convertNumberLongToByte(sequenceNumber);
		ackNumberInByte = convertNumberLongToByte(ackNumber);

		//Data offset
		dataOffset_reservedBits = intToSignedByte(PacketHeadSize);

		int controlBitsInt = ACK * 16 + SYN * 2 + FIN;
		controlBits = intToSignedByte(controlBitsInt);
		
		windowSizeInByte = convertEightBitIntToByte(windowSize);

		urgentPointerInByte = new byte[2];
		urgentPointerInByte[0] = 0;
		urgentPointerInByte[1] = 0;
	}

	//Analyse the packet to get the informations.
	public boolean analysePacket(){
		packetHead = new byte[PacketHeadSize];
		try{
			System.arraycopy(packetInByte, 0, packetHead, 0, PacketHeadSize);
		}catch(ArrayIndexOutOfBoundsException e){
			return false;
		}
		checksum = new byte[2];
		System.arraycopy(packetHead, 16, checksum, 0, 2);
		//If the checksum is wrong.
		if(!checkPacket()) return false;
		
		System.arraycopy(packetHead, 0, sourcePortInByte, 0, 2);
		sourcePort = convertTwoBytesToInt(sourcePortInByte);
		System.arraycopy(packetHead, 2, destinationPortInByte, 0, 2);
		destinationPort = convertTwoBytesToInt(destinationPortInByte);
		
		
		System.arraycopy(packetHead, 4, sequenceNumberInByte, 0, 4);
		sequenceNumber = convertFourBytesToInt(sequenceNumberInByte);
		System.arraycopy(packetHead, 8, ackNumberInByte, 0, 4);
		ackNumber = convertFourBytesToInt(ackNumberInByte);
		dataOffset_reservedBits = packetHead[12];
		controlBits = packetHead[13];
		byte getFIN = (byte) (controlBits & 0x01);
		//System.out.println("controlBits:"+(int)controlBits);
		FIN = getFIN;
		
		System.arraycopy(packetHead, 14, windowSizeInByte, 0, 2);
		windowSize = convertTwoBytesToInt(windowSizeInByte);
		
		System.arraycopy(packetHead, 18, urgentPointerInByte, 0, 2);
		dataInByte = new byte[packetInByte.length-20];
		System.arraycopy(packetInByte, 20, dataInByte, 0, packetInByte.length-20);
		return true;
	}

	//Use checksum check whether the packet is corrupt or not.
	//If return is true, the packet is correct.
	private boolean checkPacket(){
		byte[] sumTmp = new byte[2];
		sumTmp[1] = 0;
		sumTmp[0] = 0;

		byte[] twoBytes = new byte[2];

		for(int i=0;i<packetInByte.length;i=i+2){
			//if(i==16) continue;
			twoBytes[0] = packetInByte[i];
			//If length of dataInByte is an odd number, we should fill another byte with 0.
			if(i == packetInByte.length-1){
				twoBytes[1] = 0;
			}else{
				twoBytes[1] = packetInByte[i+1];
			}
			//System.out.println("checksum"+twoBytes[0]+" "+twoBytes[1]);
			sumTmp = checksumAdder(sumTmp,twoBytes);
		}

		if(sumTmp[0]==-1 && sumTmp[1]==-1) return true;
		else return false;
	}

	//Convert a port number to a 2 bytes data.
	private byte[] convertEightBitIntToByte(int number){
		byte[] numberByte = new byte[2];
		int numberHigh = number / 256;
		int numberLow = number % 256;
		numberByte[0] = intToSignedByte(numberHigh);
		numberByte[1] = intToSignedByte(numberLow);
		return numberByte;
	}
	
	//Convert a 2 bytes long bytes to int.
	private int convertTwoBytesToInt(byte[] bytes){
		int number = 0;
		int numberHigh = unsignedByteToInt(bytes[0]);
		int numberLow = unsignedByteToInt(bytes[1]);
		number = numberHigh * 256 + numberLow;
		return number;
	}

	//Convert an int sequence or ack number to a 4 bytes data.
	private byte[] convertNumberLongToByte(long number){
		byte[] numberByte = new byte[4];

		int[] numbers = new int[4];
		numbers[0] = (int)(number / 256 / 256 / 256);
		number = number - (long)numbers[3]*256*256*256;
		numbers[1] = (int)(number / 256 / 256);
		number = number - (long)numbers[2]*256*256;
		numbers[2] = (int)(number / 256);
		numbers[3] = (int)(number - (long)numbers[1]*256);

		for(int i=0;i<4;i++){
			numberByte[i] = intToSignedByte(numbers[i]);
		}
		return numberByte;
	}

	//Convert a 4 bytes data to an int.
	private long convertFourBytesToInt(byte[] bytes){
		long number = 0;
		number = unsignedByteToInt(bytes[0])*256*256*256+unsignedByteToInt(bytes[1])*256*256+unsignedByteToInt(bytes[2])*256+unsignedByteToInt(bytes[3]);
		return number;
	}

	//Generate the whole packet in bytes.
	public byte[] generatePacket(){
		generateHead();
		int packetLength = packetHead.length;
		if(dataInByte!=null) packetLength = packetLength + dataInByte.length;
		packetInByte = new byte[packetLength];
		System.arraycopy(packetHead, 0, packetInByte, 0, packetHead.length);
		if(dataInByte!=null) System.arraycopy(dataInByte, 0, packetInByte, packetHead.length, dataInByte.length);
		return packetInByte;
	}

	//Generate the head of this packet, and save it to packetHead.
	//Total: 20 Byte.
	// 4 Byte - Source Port and Destination Port
	// 4 Byte - Sequence Number
	// 4 Byte - Acknowledgment Number
	// 4 Byte -
	//    Bit 0-3: Data Offset
	//    Bit 4-9: Reserved
	//    Bit 10: URG; Bit 11: ACK; Bit 12: PSH; Bit 13: RST; Bit 14: SYN; Bit 15: FIN
	//    Bit 16-31: Window
	// 4 Byte - 
	//    Bit 0-15: Checksum
	//    Bit 16-31: Urgent Pointer
	private void generateHead(){
		System.arraycopy(sourcePortInByte, 0, packetHead, 0, 2);
		System.arraycopy(destinationPortInByte, 0, packetHead, 2, 2);
		System.arraycopy(sequenceNumberInByte, 0, packetHead, 4, 4);
		System.arraycopy(ackNumberInByte, 0, packetHead, 8, 4);
		packetHead[12] = dataOffset_reservedBits;
		packetHead[13] = controlBits;
		System.arraycopy(windowSizeInByte, 0, packetHead, 14, 2);
		byte[] checksum = generateChecksum();
		System.arraycopy(checksum, 0, packetHead, 16, 2);
		System.arraycopy(urgentPointerInByte, 0, packetHead, 18, 2);
	}

	//Generate the checksum which is based on the data in the packet now.
	private byte[] generateChecksum(){
		checksum = new byte[2];
		//Temporarily save the sums.
		byte[] checkTmp = new byte[2];
		checkTmp[0] = 0;
		checkTmp[1] = 0;

		byte[] twoBytes = new byte[2];

		//Generate checksum for port.
		checkTmp = checksumAdder(checkTmp, sourcePortInByte);

		checkTmp = checksumAdder(checkTmp, destinationPortInByte);

		//Generate checksum for sequence number and ack number.
		System.arraycopy(sequenceNumberInByte, 0, twoBytes, 0, 2);
		checkTmp = checksumAdder(checkTmp, twoBytes);
		System.arraycopy(sequenceNumberInByte, 2, twoBytes, 0, 2);
		checkTmp = checksumAdder(checkTmp, twoBytes);

		System.arraycopy(ackNumberInByte, 0, twoBytes, 0, 2);
		checkTmp = checksumAdder(checkTmp, twoBytes);
		System.arraycopy(ackNumberInByte, 2, twoBytes, 0, 2);
		checkTmp = checksumAdder(checkTmp, twoBytes);


		//Generate checksum for dataoffset, reserved bits and control bits.
		twoBytes[0] = dataOffset_reservedBits;
		twoBytes[1] = controlBits;
		checkTmp = checksumAdder(checkTmp,twoBytes);


		//Generate checksum for window size.
		twoBytes = windowSizeInByte;
		checkTmp = checksumAdder(checkTmp,twoBytes);



		//Generate checksum for urgent pointer.
		twoBytes = urgentPointerInByte;
		checkTmp = checksumAdder(checkTmp,twoBytes);

		//Generate checksum for data.
		twoBytes = new byte[2];
		if(dataInByte!=null){
			for(int i=0;i<dataInByte.length;i=i+2){
				twoBytes[0] = dataInByte[i];
				//If length of dataInByte is an odd number, we should fill another byte with 0.
				if(i == dataInByte.length-1){
					twoBytes[1] = 0;
				}else{
					twoBytes[1] = dataInByte[i+1];
				}
				checkTmp = checksumAdder(checkTmp,twoBytes);
			}
		}

		//Convet bytes to 1's compliment.
		checksum[1] = (byte)~checkTmp[1];
		checksum[0] = (byte)~checkTmp[0];		
		
		return checksum;

	}


	//Return the 1's compliment add result of two 16 bit numbers.
	private byte[] checksumAdder(byte[] a, byte[] b){
		byte[] result = new byte[2];
		int resultHigh = unsignedByteToInt(a[0]) + unsignedByteToInt(b[0]);
		int resultLow = unsignedByteToInt(a[1]) + unsignedByteToInt(b[1]);
		

		int resultInt = resultHigh * 256 + resultLow;
		//If overflow
		if(resultInt>=65536){
			resultInt = resultInt % 65536;
			resultInt = resultInt + 1;
		}

		resultHigh = resultInt / 256;
		resultLow = resultInt % 256;

		//Convert int to signed byte and save it to result.
		result[0] = intToSignedByte(resultHigh);
		result[1] = intToSignedByte(resultLow);
		

		return result;
	}
	
	public void setSendTime(){
		sendTime = new Date().getTime();
	}
	
	public long getSendTime(){
		return sendTime;
	}
	
	public long getSequenceNumber(){
		return this.sequenceNumber;
	}

	public void increaseTransmitTimes(){
		this.transmitTimes++;
	}

	public int getTransmitTimes(){
		return this.transmitTimes;
	}
	
	public byte[] getDataInByte(){
		return this.dataInByte;
	}
	
	public int getFIN(){
		return FIN;
	}
	
	public long getAckNum(){
		return this.ackNumber;
	}
	
	public int getACK(){
		return this.ACK;
	}
	
	public int getSYN(){
		return this.SYN;
	}
	
	public int getSourcePort(){
		return this.sourcePort;
	}
	
	public int getDestinationPort(){
		return this.destinationPort;
	}
	
	public int getWindowSize(){
		return this.windowSize;
	}

	//Convert a byte to an unsigned int. The range of return int is 0 to 255.
	private int unsignedByteToInt(byte s){
		return (int)s & 0xFF;
	}

	//Convert a int number(0-255) to byte.
	private byte intToSignedByte(int s){
		//if( s<0 || s>255 ) throw new IOException();
		if(s > 127) s = s - 256;
		return (byte)s;
	}
}