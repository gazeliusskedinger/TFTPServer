import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;

public class TFTPServer
{
    public static final int TFTPPORT = 4970;
    public static final int BUFSIZE = 516;
    public static final String READDIR = "read/"; //custom address at your PC
    public static final String WRITEDIR = "write/"; //custom address at your PC


    // OP codes
    public static final int OP_RRQ = 1;
    public static final int OP_WRQ = 2;
    public static final int OP_DAT = 3;
    public static final int OP_ACK = 4;
    public static final int OP_ERR = 5;

    public static void main(String[] args) {
        if (args.length > 0)
        {
            System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
            System.exit(1);
        }
        //Starting the server
        try
        {
            TFTPServer server= new TFTPServer();
            server.start();
        }
        catch (IOException e)
        {e.printStackTrace();}
    }

    private void start() throws IOException
    {
        byte[] buf= new byte[BUFSIZE];

        // Create socket
        DatagramSocket socket= new DatagramSocket(null);

        // Create local bind point
        SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
        socket.bind(localBindPoint);

        System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

        // Loop to handle client requests
        while (true)
        {

            final InetSocketAddress clientAddress = receiveFrom(socket, buf);

            // If clientAddress is null, an error occurred in receiveFrom()
            if (clientAddress == null)
                continue;

            final StringBuffer requestedFile= new StringBuffer();
            final int reqtype = ParseRQ(buf, requestedFile);

            new Thread()
            {
                public void run()
                {
                    try
                    {
                        DatagramSocket sendSocket= new DatagramSocket(0);

                        // Connect to client
                        sendSocket.connect(clientAddress);

                        String[] ip = clientAddress.getAddress().toString().split("/");

                        System.out.printf("%s request for %s , IP: %s, port: %s \n",
                                (reqtype == OP_RRQ)?"Read":"Write",
                                clientAddress.getHostName(), ip[1], clientAddress.getPort());

                        // Read request
                        if (reqtype == OP_RRQ)
                        {
                            requestedFile.insert(0, READDIR);
                            HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
                        }
                        // Write request
                        else
                        {
                            requestedFile.insert(0, WRITEDIR);
                            HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);
                        }
                        sendSocket.close();
                    }
                    catch (IOException e)
                    {e.printStackTrace();}
                }
            }.start();
        }
    }

    /**
     * Reads the first block of data, i.e., the request for an action (read or write).
     * @param socket (socket to read from)
     * @param buf (where to store the read data)
     * @return socketAddress (the socket address of the client)
     * @throws IOException
     */
    private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws IOException
    {
        // Create datagram packet
        DatagramPacket rcv_pack = new DatagramPacket(buf, buf.length);
        // Receive packet
        socket.receive(rcv_pack);
        // Get client address and port from the packet
        //System.out.println("Client's address: "+rcv_pack.getAddress()+"| Client's port: "+rcv_pack.getPort());

        byte[] parser = rcv_pack.getData();
		
		/*if(parser[1] == 0x01) {
			System.out.println("Client requests read-op");
		}
		if(parser[1] == 0x02) {
			System.out.println("Client requests write-op");
		}*/

        return (InetSocketAddress) rcv_pack.getSocketAddress();
    }

    /**
     * Parses the request in buf to retrieve the type of request and requestedFile
     *
     * @param buf (received request)
     * @param requestedFile (name of file to read/write)
     * @return opcode (request type: RRQ or WRQ)
     */
    private int ParseRQ(byte[] buf, StringBuffer requestedFile)
    {

        // See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
        if(buf[1] == 0x01 || buf[1] == 0x02) {
            int index = 2;
            int modestart = 0;
            while(buf[index] != 0){
                requestedFile.append((char)buf[index]);
                index++;
                modestart = index;
            }
            while(buf[modestart] != 0) {
                requestedFile.append((char) buf[modestart]);
                modestart++;
            }

            System.out.println("Client requests read-op "+ requestedFile.toString());
        }
        else if(buf[0] == 0x02) {
            System.out.println("Client requests write-op");
        }
        return 1;
    }

    /**
     * Handles RRQ and WRQ requests
     *
     * @param sendSocket (socket used to send/receive packets)
     * @param requestedFile (name of file to read/write)
     * @param opcode (RRQ or WRQ)
     * @throws IOException
     */
    private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) throws IOException
    {
        if(opcode == OP_RRQ)
        {
            // See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
            boolean result = send_DATA_receive_ACK(sendSocket, requestedFile, opcode);
        }
        else if (opcode == OP_WRQ)
        {
            boolean result = receive_DATA_send_ACK(sendSocket, requestedFile, opcode);
        }
        else
        {
            System.err.println("Invalid request. Sending an error packet.");
            // See "TFTP Formats" in TFTP specification for the ERROR packet contents
            send_ERR(sendSocket, requestedFile);
            return;
        }
    }

    /**
     To be implemented
     * @throws IOException
     */
    private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, String requested_file, int opcode) throws IOException {
        // Defining file
        File file = new File(requested_file);

        // Defining the size of the file in bytes
        int file_size = (int) file.length();

        // Defining the amount of blocks
        int blocks = file_size / 512;

        // Remaining block's bytes
        int block_mod = file_size % 512;

        // If file is less than 512 bytes, we assume that it will take 1 block to transfer
        if(blocks == 0) {
            blocks = 1;
        }

        // Opening stream for a file
        BufferedInputStream  fis = new BufferedInputStream(new FileInputStream(file));

        if(blocks == 1) {
            // Creating byte-array for storing header of the packet
            byte[] data_header = new byte[4];

            // Defining DATA-bytes identifiers
            data_header[0] = (byte) 0; data_header[1] = (byte) 3;
            // Defining block's number
            data_header[2] = (byte) 0; data_header[3] = (byte) blocks;

            // Creating buffer for reading the file
            byte[] buffer = new byte[(int) file.length()];

            // Reading content of the file into the array with  fixed size
            fis.read(buffer);

            // Closing stream (no use)
            fis.close();

            // Concat two byte-arrays into one packet for delivery!
            byte[] send_packet = new byte[data_header.length + buffer.length];

            // Copying content of the DATA-header to packet-array
            System.arraycopy(data_header, 0, send_packet, 0, data_header.length);

            // Copying content of the file's bytes to packet-array
            System.arraycopy(buffer, 0, send_packet, data_header.length, buffer.length);

            // Creating UDP-datagram and sending to destination point
            DatagramPacket output = new DatagramPacket(send_packet, send_packet.length);
            sendSocket.send(output);

            // Receiving ACK-message
            byte[] ack_packet = new byte[4];
            DatagramPacket ack = new DatagramPacket(ack_packet, ack_packet.length);
            sendSocket.receive(ack);

            // Checking content of ACK-message
            if(ack_packet[0] != 0 || ack_packet[1] != 4) {
                System.out.println("Message header is not equal to 0x04! Content: "+ack_packet[0]+"|"+ack_packet[1]);
            }
            if(ack_packet[2] != 0 || ack_packet[3] != (byte) blocks) {
                System.out.println("Wrong block's value! Content: "+ack_packet[2]+"|"+ack_packet[3]);
            }

        }
        else {
            // Creating byte-array for storing header of the packet
            byte[] data_header = new byte[4];

            // Defining DATA-bytes identifiers
            data_header[0] = (byte) 0; data_header[1] = (byte) 3;
            // Defining block's number
            data_header[2] = (byte) 0;

            // Buffer for input-stream of the file
            byte[] buffer = new byte[512];

            int i = 1;
            // Check for stream end
            //int read = 0;

            boolean status = true;

            int operations;

            if(block_mod == 0) {
                operations = 0;
            }
            else {
                operations = 1;
            }

            System.out.println("Full package's count: "+blocks);

            while(i <= blocks && operations == 1) {

                if(i <= blocks) {
                    fis.read(buffer, 0, buffer.length);
                }

                if(i > blocks) {
                    buffer = new byte[block_mod];
                    fis.read(buffer, 0, block_mod);
                }

                System.out.println(buffer.length);

                // Creating byte-packet
                byte[] send_packet = new byte[data_header.length + buffer.length];

                // Copying content of the DATA-header to packet-array
                System.arraycopy(data_header, 0, send_packet, 0, data_header.length);

                // Copying content of the file's bytes to packet-array
                System.arraycopy(buffer, 0, send_packet, data_header.length, buffer.length);

                data_header[3] = (byte) (i);

                // Creating UDP-datagram and sending to destination point
                DatagramPacket output = new DatagramPacket(send_packet, send_packet.length);
                sendSocket.send(output);

                // Receiving ACK-message
                byte[] ack_packet = new byte[4];
                DatagramPacket ack = new DatagramPacket(ack_packet, ack_packet.length);
                sendSocket.receive(ack);
                i++;
            }

            fis.close();
        }

        return true;
    }

    private boolean receive_DATA_send_ACK(DatagramSocket sender, String path, int opcode) {
        return true;
    }

    private void send_ERR(DatagramSocket sender, String path) {

    }
}