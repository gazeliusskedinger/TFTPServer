import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
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

//                        System.out.printf("%s request for %s , IP: %s, port: %s \n",
//                                (reqtype == OP_RRQ)?"Read":"Write",
//                                clientAddress.getHostName(), ip[1], clientAddress.getPort());

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

        // byte[] parser = rcv_pack.getData();
		
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
//        if(buf[1] == 0x01 || buf[1] == 0x02) {
            int index = 2;
            int modestart = 0;
            while(buf[index] != 0){
                requestedFile.append((char)buf[index]);
                index++;
                modestart = index;
            }
            /*while(buf[modestart] != 0) {
                requestedFile.append((char) buf[modestart]);
                modestart++;
            }*/
        /*}
        else if(buf[0] == 0x02) {
            System.out.println("Client requests write-op");
        }*/
        return fromBytes(buf[0], buf[1]);
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
            System.out.println("read request");
            boolean result = send_DATA_receive_ACK(sendSocket, requestedFile, opcode);
        }
        else if (opcode == OP_WRQ)
        {
            System.out.println("write request");
            boolean result = receive_DATA_send_ACK(sendSocket, requestedFile, opcode);
        }
        else
        {
            System.err.println("Invalid request. Sending an error packet.");
            // See "TFTP Formats" in TFTP specification for the ERROR packet contents
            send_ERR(sendSocket, "Not defined", 0);
            return;
        }
    }

    private byte[] toBytes(int value){
        ByteBuffer wrap = ByteBuffer.allocate(2);
        wrap.putShort((short)value);
        return wrap.array();
    }
    private int fromBytes(byte a, byte b){
        ByteBuffer wrap = ByteBuffer.allocate(2);
        wrap.put(a);
        wrap.put(b);
        short m = wrap.getShort(0);
        return m;
    }
    /**
     To be implemented
     * @throws IOException
     */
    private boolean send_DATA_receive_ACK(DatagramSocket sendSocket, String requested_file, int opcode) throws IOException {
        // Defining file
        File file = new File(requested_file);
        if(!file.exists() && !file.isFile()) {
            // send error
            send_ERR(sendSocket, "File not found", 1);
            return false;
        }
        if(requested_file.contains("secret/")){
            send_ERR(sendSocket, "Access violation", 2);
            return false;
        }

        // Defining the size of the file in bytes
        int file_size = (int) file.length();

        // Defining the amount of blocks
        int blocks = file_size / 512;

        // Remaining block's bytes
        int block_mod = file_size % 512;

        // Current block number
        int block = 1;

        // If file is less than 512 bytes, we assume that it will take 1 block to transfer
        if(blocks == 0) {
            blocks = 1;
        }

        // Opening stream for a file
        BufferedInputStream  fis = new BufferedInputStream(new FileInputStream(file));

//        if(blocks == 1) {
            // Creating byte-array for storing header of the packet
            byte[] data_header = new byte[4];

            // Defining DATA-bytes identifiers
            data_header[0] = (byte) 0; data_header[1] = (byte) 3;

            // Creating buffer for reading the file
            byte[] buffer = new byte[(int) file.length()];

            // Reading content of the file into the array with  fixed size
            fis.read(buffer);

            // Closing stream (no use)
            fis.close();

            // Error catcher
            int errorcount = 0;

            for(block = 1; block < blocks+1; block++){
                // Defining block's number
                if(errorcount > 3) return false; // no more tries!
                byte[] blockArr = toBytes(block);
                data_header[2] = blockArr[0]; data_header[3] = blockArr[1];

                // our packet length
                int packetLength = (block < blocks ? 512 : block_mod);


                // Concat two byte-arrays into one packet for delivery!
                byte[] send_packet = new byte[data_header.length + packetLength];

                // Copying content of the DATA-header to packet-array
                System.arraycopy(data_header, 0, send_packet, 0, data_header.length);

                // Copying content of the file's bytes to packet-array
                System.arraycopy(buffer, (block-1)*512, send_packet, data_header.length, packetLength);

                // Creating UDP-datagram and sending to destination point
                DatagramPacket output = new DatagramPacket(send_packet, send_packet.length);
                sendSocket.send(output);

                // Receiving ACK-message
                byte[] ack = new byte[4];
                DatagramPacket ack_packet = new DatagramPacket(ack, ack.length);
                sendSocket.setSoTimeout(1000);
                try {
                    sendSocket.receive(ack_packet);
                }
                catch (Exception e){
                    // it expired, retry
                    block--;
                    errorcount++;
                    continue;
                }

                // Checking content of ACK-message
                if(fromBytes(ack[0], ack[1]) != OP_ACK) {
                    // no ack received
                    return false;
                }
                else if(fromBytes(ack[2], ack[3]) != block) {
                    // wrong block number received, resend!
                    errorcount++;
                    block--;
                }
                else errorcount = 0; // it went trough
            }

            return true;
    }

    private boolean receive_DATA_send_ACK(DatagramSocket sender, String path, int opcode) throws IOException {
        // check path
        File file = new File(path);
        if(file.exists() && file.isFile()){
            send_ERR(sender, "File already exists", 6);
            return false;
        }
        if(path.contains("secret/")){
            send_ERR(sender, "Access violation", 2);
            return false;
        }

        FileOutputStream fileWriter = new FileOutputStream(file);

        // initial block
        int block = 0, errorcount = 0;

        while(true){
            // check errorcount
            if (errorcount > 3) return false;
            // create ACK
            ByteBuffer ack = ByteBuffer.allocate(4);
            ack.putShort((short) OP_ACK);
            ack.putShort((short) block);

            // send ACK
            sender.send(new DatagramPacket(ack.array(), 4));

            // get data
            byte[] recieved = new byte[BUFSIZE];
            DatagramPacket packet = new DatagramPacket(recieved, recieved.length);
            sender.setSoTimeout(1000);
            try {
                sender.receive(packet);
            }
            catch (Exception e){
                // time expired
                errorcount++;
                continue;
            }

            // check data
            byte[] data = packet.getData();

            // no (valid) data
            if(data.length < 4) return false;
            int recievedBlock = fromBytes(data[2], data[3]);
            if(recievedBlock == block+1) block++;
            else {
                // did not match our block number
                errorcount++;
                continue;
            }


            // write data
            fileWriter.write(data, 4, packet.getLength()-4);
            fileWriter.flush();

            // reached end-of-file (?)
            if(packet.getLength() < BUFSIZE) {
                // create last ACK from previous ACK
                ack.position(2);
                ack.putShort((short) block);

                // send last ack
                sender.send(new DatagramPacket(ack.array(), 4));
                break;
            }
        }

        fileWriter.close();
        return true;
    }

    private void send_ERR(DatagramSocket sender, String message, int errorCode) throws IOException {
        byte[] msgData = message.getBytes();
        ByteBuffer sendPacket = ByteBuffer.allocate(5+msgData.length);

        sendPacket.putShort((short) OP_ERR);
        sendPacket.putShort((short) errorCode);
        sendPacket.put(msgData);
        sendPacket.put((byte) 0);
        sender.send(new DatagramPacket(sendPacket.array(), 5+msgData.length));
    }
}