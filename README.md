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

- Start the Server [Sender] as
	java Server TCP_PORT filename remote_IP remote_port ack_port_number window_size log_filename

- Start the Client [Receiver] as
	java Client SERVER_IP SERVER_TCP_PORT filename listening_port remote_IP remote_port log_filename


Example:
	You can set the proxy as
		./newudpl -i 192.168.36.1/20002 -o 192.168.36.1/20000 -p 5000:6000 -L 50

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
		You may choose a good window size, which should not be too small(Larger than 10 is good). 
		Set the proxy with -O 50 or -L 50. Maybe setting the out of order rate could shows it better.
		Transmit the file, and check the log file of receiver.
		You can see that the ACK number will changed but it does not increase by one if it has buffers some packets and just received a packet it lacks.

- Fast retransmission on sender.

	Test case:
		Transmit a file with proxy. The size of file should not be too small(50-100KB is fine).
		Set a proper window size.
		Set the proxy with -L 50.
		Transmit the file, and check the log on sender.
		You can find when it receives 3 duplicate ACK, it will directly send a packet with sequence number equal to duplicate ACK number+1.
		However, sending a packet will cost some time. So sometimes you will see this packet after 4 or 5 duplicate ACK numbers. It is because when you are sending a packet, new ack arrives and it's shown in log.

 - Delayed ACK

 	Page 247 of text book. Wait up to 20ms for arrival of another in-order segment. The default value of timeout is 500ms. But in this case, I set it to 20ms.
 	Test cases:
 		You should choose a proper window size(10 is fine).
 		You can transmit a big file and observe the log of receiver.
 		You can find that the receiver will not send an ACK for the arrival of in-order segment with expected sequence number. It will only send an ACK when it doesn't receive another in-order segment in 20ms or when other cases(on page 247) happen. 

-------------------------------------------------------------------------

## Implemention Details

- ACK number

	In my program, ack number is closer to TCP protocol but not GBN. When a receiver received a packet, it will send its expected sequence number as its ack number. So the sender should set the base to expected number, but not expected number - 1.

- Calculation of RTT and Timeout

	The default value of estimated RTT and devRTT is 100ms and 25ms.

	Timeout = estimatedRTT + 4*devRTT;

	Alpha of estimated RTT is 0.125.

	Beta of devRTT is 0.25.

	If you don't use proxy, the estimated RTT may become 0, which make timeout becomes 0. It's not good to transmit packet with timeout=0. So I set the minimum timeout to 10ms.


- Head:
	
	Alough the window size is used for solving congestion, I used it here to transmit the window size from sender to receiver. By doing this, we don't need to pass a window size parameter to receiver to implement accumulate ACK.

- FIN:

	FIN packet is transmited after the end of file transmission. Sender will send a FIN to receiver. When receiver received a FIN, it will send an ack of it. After 10ms, it will send a FIN packet to sender. When sender receive this packet, it will send a ack to this packet.After that, the socket will be closed.

- Estimated RTT
	
	Like TCP, only calculate the packet which did not be resent.

- Ending output information on sender

	Total bytes sent: Calculate size of all packets, including FIN packet.
	Segment sent: Calculate how many packets should be sent, including FIN packet.
	Segments retransmitted: Calculate how many packets were retransmited, including FIN packet. If we transmit a packet 3 times before we received a ACK, it will be counted as 2 times retransmission.

- Excpetion of proxy

	If you transmit a lot of packets through proxy in a short time, there may be an Floating point exception (core dumped). At that time, you can restart it and the transmission will continue.
