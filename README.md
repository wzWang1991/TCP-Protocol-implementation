# Programming Assignment 2 --- Simple TCP-like transport-layer protocol

	Weizhen Wang	ww2339
-------------------------------------------------------------------------

## Programming Environment:

OS: Mac OS X 10.9
Java version: 1.7.0_40

-------------------------------------------------------------------------

## Compile

- Makefile is included. So you can just use "make" to compile the program.

-------------------------------------------------------------------------

## Server set up and Client connection

You can use test_script to test this program. It will automatically open 3 terminals: proxy, server, and client. After you login, you can type GET to fetch the file. You can change the command in script.

Or you can:

- Start the Server [Sender] as
	java Server TCP_PORT filename remote_IP remote_port ack_port_number window_size log_filename

- Start the Client [Receiver] as
	java Client SERVER_IP SERVER_TCP_PORT filename listening_port remote_IP remote_port log_filename


Example:
	You can set the proxy as
		./newudpl -i 192.168.36.1/20002 -o 192.168.36.1/20000 -p 5000:6000 -L 20 -B 10 -O 20

	The proxy is running on 192.168.36.135. And my host computer is 192.168.36.1.

	So you can set the Server like
		java Server 20137 senddata.txt 192.168.36.135 5000 20002 10 stdout

	Set the Client like
		java Client 127.0.0.1 20137 rcvdata.txt 20000 127.0.0.1 20002 stdout

If log_filename is stdout, it will show the log on screen.

If you don't want to use the Server and Client, you can also use the sender and receiver directly as:
	receiver filename, listening_port remote_IP, remote_port, log_filename
	sender filename, remote_IP, remote_port, ack_port_number, window_size, log_filename

-------------------------------------------------------------------------

## Command 

It support all commands in Programming Assigment 1:

- whoelse

	You can input whoelse to see who else is online now. If multiple users login with a same user name, it will show all of them with their IP and port.

- wholasthr

	See who is active on this server in the last hour. If multiple users login with a same user name, it will only show the user with latest login status.

	Only after a user log out, or disconnected for 1 hours, will it be removed from the table.


- broadcast message

	You can broadcast your message to all users who are connecting to this server. All users will receive this broadcast except clients who login with the same username as the broadcaster.

- logout

	You can use this command to log out the server. The socket will be closed.

- PM IP PORT message

	You can send a personal message to an IP and port. The message will only send to one destination.

- PMuser username message

	You can send a personal message to a user. If there is multiple client using the same user name. All of them will receive this message.


Now here is the new command: GET.

- GET

	Get the file from server and save it to disk. You can use proxy between sender and receiver. Before every time using GET, change the parameter of proxy can make the transmission next "GET" experiencing different network environment.

	You should not close the server or client when file is being transmitted. 
	If you press ctrl+c on server side when file is transmitting, the client side will automatically exit. 
	If you press ctrl+c on client side, the sender will stop sending file, and the server will continue running, and new users can use GET to fetch the file. 
	Note: In my program, the TCP socket only established after receiver receives a packet, so only after it is established, ctrl+c at client side will not affect server.

	Only one client can use GET at the same time. But after a client finishing receiving file, other client can use GET to fetch file.

-------------------------------------------------------------------------

## Features(Only about Programming Assigment 2)

- Combined with Programming Assigment 1.
	
	It works just like an addon. So if there are some fatal errors in sending and receiving, the server or client will automatically exit.

- Support transmitting any type of file. (Read and write in bytes.)

- Support big size file. (A picture of 4.8MB and a .exe file of size 5MB in windows were tested successfully.)

- Support for cumulative ACK

	The state machine of GBN on Page 211 of text book also use cumulative acknowledgment, indicating that all packets with a sequence number up to and including n have been correctly received at the receiver. 

- Receiver can buffer the packets which are not in order.
	
	Test case:
		Transmit a file with proxy. The size of file should not be too small(50-100KB is fine).
		You may choose a proper window size, which should not be too small(Larger than 10 is good). 
		Set the proxy with -O 50 or -L 50. Maybe setting the out of order rate could shows it better.
		Transmit the file, and check the log file of receiver.
		You can see that the ACK number of adjacent packet will changed but it does not increase by one if it has buffers some packets and just received a packet it lacks.

- Fast retransmission on sender.

	Test case:
		Transmit a file with proxy. The size of file should not be too small(50-100KB is fine).
		Set a proper window size(10 is fine). 
		Set the proxy with -L 50.
		Transmit the file, and check the log file of sender.
		You can find when it receives 3 duplicate ACK, it will immediately send a packet with sequence number equal to duplicate ACK number.

- Implementation of TCP ACK Generation Recommendation(RFC 5681)

	- Delayed ACK(Only when window size>1)

 		Wait up to 20ms for arrival of another in-order segment. The default value of timeout is 500ms. But in this case, I set it to 10ms.

	 	Test case:
	 		You should choose a proper window size(10 is fine). 
	 		You can set loss rate, out-of-order rate, bit-error rate to zero in this case.
	 		You can transmit a big file and check the log of receiver.
	 		You will find that the receiver will not send an ACK for the arrival of in-order segment with expected sequence number. It will send an ACK when it receives another in-order packet.

	- Duplicate ACK

		Arrival of out-of-order segment with higher-than-expected sequence leads to sending send duplicate ACK immediately, indicating sequence number of next number. 

		Test case:
			You should choose a proper window size(10 is fine). 
	 		Set the proxy with -L 20 -O 20.
	 		You can transmit a big file and check the log of receiver. There will be two lines transmitting duplicate ACK nearly at the same time.
	 		When receiver receives a packet with a sequence number which makes a gap in its buffer, it will send duplicate ACK. 
	 		For example, if receiver is expecting sequence number 2, and its buffer includes packet with sequence number 3,4, when it receives a packet with sequence number 6, there is a gap in the buffer. So at this time, it will send duplicate ACK to sender.

-------------------------------------------------------------------------

## Implemention Details

- Calculation of RTT and Timeout

	The default value of estimated RTT and devRTT is 100ms and 25ms.

	Timeout = estimatedRTT + 4*devRTT;

	Alpha of estimated RTT is 0.125.

	Beta of devRTT is 0.25.

	If you don't use proxy, the estimated RTT may become 0, which make timeout becomes 0. It's not good to transmit packet with timeout=0. So I set the minimum timeout to 10ms.

	Like TCP, only calculate the packet which did not be resent.

- ACK 
	
	- The receiver sends ACK packet with 20-bytes length head.

	- ACK number

		In my program, ack number is closer to TCP protocol but not GBN. When a receiver received a packet, it will send its expected sequence number as its ack number. So the sender should set the base to expected number, but not expected number - 1.

	- ACK strategy

		In GBN, receiver should not send an ack for a packet whose sequence number is smaller than expected number. I found some times, although ack is transmitted on TCP, especially when transmitting rate is very high, an ack may not be processed correctly at sender side. In this case, if window size is 1, the transmitting process may stop. Because sender can't receive any ack for its packet.

	So I make receiver send an proper ack, which ack number is expected number, for any packet, including corrupt packet, except delayed ACK happens.

- Head:
	
	Alough the window size is used for solving congestion, I used it here to transmit the window size from sender to receiver. By doing this, we don't need to pass a window size parameter to receiver to implement buffer packet.

- FIN packet:

	FIN packet is transmited after the end of file transmission. Sender will send a FIN to receiver. When receiver received a FIN, it will send an ack of it. After 10ms, it will send a FIN packet to sender. When sender receive this packet, it will send a ack to this packet.After that, the socket will be closed.
	
- Ending output information on sender

	Total bytes sent: Calculate size of all packets, including FIN packet.
	Segment sent: Calculate how many packets should be sent, including FIN packet.
	Segments retransmitted: Calculate how many packets were retransmited, including FIN packet. If we transmit a packet 3 times before we received a ACK, it will be counted as 2 times retransmission.

- File receiving and writing
	
	The receiver will verify if there is a file with a name which it want to use as its receive file name. So you may need to delete the file if there exists.

- Establish of TCP connection

	Only when receiver receives first packet from sender will it try to connect to sender.

- Excpetion of proxy

	If you transmit a lot of packets through proxy in a short time, there may be an Floating point exception (core dumped). At that time, you can restart it and the transmission will continue.

-------------------------------------------------------------------------

## Bugs I can't solve

- When loss rate is high, file size is large, and you choose to output log to stdout, sometimes sender can't receive any ACK packet from receiver. I use wireshark trying to find this problem. But I found that receiver did send the ACK packet using TCP. And the socket in sender does not throw any exception. But it blocked at in.read(rcvBuf). So sender can not get any ACK when this case happens. 