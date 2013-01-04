import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.ArrayList;
import java.io.*;
import java.nio.charset.*;


public class Server extends Thread {

	public final int BUFFER_SIZE = 255;
	public final int PORT;

	ByteBuffer readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	ByteBuffer writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	ArrayList<SocketChannel> clients;
	ArrayList<String> names;
	ServerSocketChannel ssChan;
	Selector selector;
	private boolean membersChanged;

	public Server(int port) {
		this.PORT = port;
		clients = new ArrayList<SocketChannel>();
		names = new ArrayList<String>();
		membersChanged = false;
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("usage: Java Server [port]");                                                                                              
			return;
		}

		try {
			Server server = new Server(Integer.parseInt(args[0]));
			new Thread(server).start();
		} catch (NumberFormatException e) {
			System.out.println("Must input an integer for the port.  Quitting.");
			return;
		}
	}

	public void run() {

		initServer();
		System.out.println("\nlaunching bChat server from IP " + 
				ssChan.socket().getInetAddress().toString().substring(1));
		System.out.println("Press C-c to quit.\n");

		while (true) {

			if (membersChanged) {
				sendNames();
				membersChanged = false;
			}
			getIncomingConnections();
			handleInput();

			try {
				Thread.sleep(50);
			} catch (InterruptedException e) { }
		}
	}

	private void initServer() {
		try {
			ssChan = ServerSocketChannel.open();
			ssChan.configureBlocking(false);
			InetSocketAddress addr = new InetSocketAddress(InetAddress.getLocalHost(), PORT);
			ssChan.socket().bind(addr);
			selector = Selector.open();

		} catch (IOException e) {
			System.out.println("Error opening server.");
			System.exit(1);
		}
	}

	private void getIncomingConnections() {

		SocketChannel client;

		try {
			while ((client = ssChan.accept()) != null) {
				client.configureBlocking(false);

				// get name information
				while (client.read(readBuffer) == -1) {
					try {
						readBuffer.clear();
						Thread.sleep(1);
					} catch (InterruptedException e) { }
				}
				readBuffer.flip();
				String name = Charset.defaultCharset().decode(readBuffer).toString();
				readBuffer.clear();

				client.register(selector, SelectionKey.OP_READ, name);
				clients.add(client);
				names.add(name);

				System.out.println("connected: " + name);

				broadcastMessage(client, name + " has joined the server.");

				sendMessage(client, "\n\n Welcome to bChat! \n\n" + 
						"There are " + clients.size() + " people online.\n");
				sendMessage(client, "Type '/q' to disconnect.\n");
				sendMessage(client, "-----------------------------------\n");
				
				membersChanged = true;

				try {
					Thread.sleep(100);
				} catch (InterruptedException e) { }
			}
		} catch (IOException e) {
			System.out.println("Error connecting client.");
		} 

	}

	private String sendNames() {
		String nameString = "<member>";
		for (String word : names) {
			nameString += "," + word;
		}

		broadcastMessage(null, nameString);
		return nameString;
	}

	private void handleInput() {

		try {
			selector.selectNow();

			for (SelectionKey key : selector.selectedKeys()) {

				SelectionKey keyTmp = key;
				SocketChannel client = (SocketChannel)key.channel();
				selector.selectedKeys().remove(key);
				int msgSize = client.read(readBuffer);
				readBuffer.flip();
				String msg = Charset.defaultCharset().decode(readBuffer).toString();
				msg = msg.trim();

				readBuffer.clear();

				if (msg.equals("/q") || msgSize == -1) {

					System.out.println("disconnected: " + keyTmp.attachment());

					broadcastMessage(client, keyTmp.attachment() + " has left the server.");
					names.remove(keyTmp.attachment());
					
					clients.remove(client);
					client.close();
					membersChanged = true;
				}
				else {
					System.out.println(keyTmp.attachment() + ":  " + msg);
					broadcastMessage(client, keyTmp.attachment() + ":  " + msg);
				}
			}

		} catch (IOException e) {
			//System.out.println("Error with selector");
		}
	}

	public void broadcastMessage(SocketChannel sender, String message) {

		for (SocketChannel client : clients) {
			if (client != sender) {
				sendMessage(client, message);
			}
		}
	}

	public void sendMessage(SocketChannel recipient, String message) {
		prepareMessage(message);
		transmitMessage(recipient, writeBuffer);
	}

	private void prepareMessage(String message) {
		writeBuffer.clear();
		writeBuffer.put(message.getBytes());
		writeBuffer.putChar('\n');
		writeBuffer.flip();
	}

	private void transmitMessage(SocketChannel recipient, ByteBuffer writeBuffer) {

		int messageSize = writeBuffer.remaining();
		int sentBytes = 0;

		while (sentBytes != messageSize) {
			try {

				sentBytes += recipient.write(writeBuffer);

			} catch (IOException e) {
				System.out.println("Error sending message");
			}
		}
		writeBuffer.rewind();
	}
}
