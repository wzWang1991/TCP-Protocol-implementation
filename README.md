# Programming Assignment 2 --- TCP Protocol implementation based on UDP

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

If log_filename is stdout, it will show the log on screen.

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

- Support transmitting any type of file. (Read and write in bytes.)

- Support big size file. (A picture of 4.8MB was tested successfully.)

- Receiver can buffer the packets which are not in order(Accumulate ACK).
	
	Test cases:
		Transmitting a file with proxy. The size of file should not be too small(50-100KB is fine).
		Set the proxy with -O 20 or -L 50. Maybe setting the out of order rate could shows it better.
		Transmit the file, and check the log file of receiver.
		You can see that the ACK number will changed but it does not increase by one if it has buffers and just received a packet it lacks.


-------------------------------------------------------------------------

## Implemention Details

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


