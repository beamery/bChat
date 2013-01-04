import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.*;

import javax.swing.*;

public class Client implements Runnable, ActionListener {

	private String ip;
	private int port;
	private final int BUFFER_SIZE = 255;

	private String username;
	private boolean connected = false;
	private Selector selector;
	private SocketChannel sChan;
	private ByteBuffer readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private ByteBuffer writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);


	private static Point location = new Point(100, 100);

	private JFrame window;
	private JButton send;
	private JTextField textField;
	private JTextArea msgViewer;
	private JScrollPane msgPane;
	private JTextArea memberViewer;
	private JScrollPane memberPane;
	private JPanel sendPanel;
	private JPanel content;

	private JFrame nameWindow;
	private JButton nameOK;
	private JTextField nameField;
	private JLabel nameLabel;
	private JTextField ipField;
	private JLabel ipLabel;
	private JTextField portField;
	private JLabel portLabel;

	public Client(String ip, int port) {
		this.ip = ip;
		this.port = port;

		window = new JFrame("bChat Client");
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		send = new JButton("Send");
		send.setPreferredSize(new Dimension(100, send.getWidth()));

		textField = new JTextField();
		textField.setPreferredSize(new Dimension(textField.getWidth(), 25));

		msgViewer = new JTextArea();
		msgViewer.setEditable(false);
		msgPane = new JScrollPane(msgViewer);

		memberViewer = new JTextArea();
		memberViewer.setEditable(false);
		memberPane = new JScrollPane(memberViewer);
		memberPane.setPreferredSize(new Dimension(150, memberPane.getHeight()));

		sendPanel = new JPanel(new BorderLayout(5, 5));
		content = new JPanel(new BorderLayout(5, 5));

		nameWindow = new JFrame();
		nameField = new JTextField(null, 15);
		nameOK = new JButton("OK");
		nameLabel = new JLabel("username (min 3 chars):");
		ipField = new JTextField(ip, 15);
		ipLabel = new JLabel("IP Address");
		
		if (port != 0)
			portField = new JTextField(String.valueOf(port), 15);
		else
			portField = new JTextField(15);
		
		portLabel = new JLabel("Port Number");

		send.addActionListener(this);
		textField.addActionListener(this);
		nameField.addActionListener(this);
		nameOK.addActionListener(this);

	}

	public static void main(String[] args) {

		if (args.length == 2) {
			new Client(args[0], Integer.parseInt(args[1])).run();
		}
		else {
			new Client("", 0).run();
		}
	}

	public void run() {

		/////////// Init GUI /////////////

		// init window;
		window = new JFrame("bChat Client");
		window.setLocation(location);
		window.setSize(800, 600);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setContentPane(content);

		sendPanel.add(send, BorderLayout.EAST);
		sendPanel.add(textField, BorderLayout.CENTER);

		content.add(sendPanel, BorderLayout.SOUTH);
		content.add(msgPane, BorderLayout.CENTER);
		content.add(memberPane, BorderLayout.EAST);
		content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		nameWindow = new JFrame();
		nameWindow.setSize(new Dimension(350, 200));
		nameWindow.setLocation(window.getLocation().x + 200, 
				window.getLocation().y + 300);

		// init name window
		nameWindow.setResizable(false);
		JPanel nameContent = (JPanel)nameWindow.getContentPane();
		nameContent.setLayout(new GridLayout(4, 1));
		nameContent.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		Container nameContainer = new JPanel(new GridLayout(1, 2));
		nameContainer.add(nameLabel);
		nameContainer.add(nameField);
		nameContent.add(nameContainer);

		Container ipContainer = new JPanel(new GridLayout(1, 2));
		ipContainer.add(ipLabel);
		ipContainer.add(ipField);
		nameContent.add(ipContainer);

		Container portContainer = new JPanel(new GridLayout(1, 2));
		portContainer.add(portLabel);
		portContainer.add(portField);
		nameContent.add(portContainer);

		nameContent.add(nameOK);
		nameWindow.requestFocus();

		nameWindow.pack();
		window.setVisible(true);
		nameWindow.setVisible(true);

		/////////// End GUI /////////////

		while (!connected) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) { }
		}

		while (true) {

			getMessages();

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) { }
		}

	}

	public void actionPerformed(ActionEvent e) {

		Object src = e.getSource();
		if (src.equals(nameOK) || src.equals(nameField)) {

			// process username and connect to server
			if (nameField.getText().length() > 2) {
				username = nameField.getText();
				ip = ipField.getText();
				port = Integer.parseInt(portField.getText());
				nameWindow.setVisible(false);
				connectToServer();
				textField.requestFocus();
				connected = true;
			}
		}
		else if (connected && (src.equals(textField) || src.equals(send))) {

			sendMessage(textField.getText());	
			if (textField.getText().startsWith("/q")) {
				System.exit(0);
			}
			msgViewer.append(username);
			msgViewer.append(":  " + textField.getText() + "\n");
			msgPane.getVerticalScrollBar().setValue(msgPane.getVerticalScrollBar().getMaximum());

			textField.setText(null);
		}

	}

	private void connectToServer() {
		msgViewer.append("Connecting to server at " + ip + "...\n");
		try {
			selector = Selector.open();
			InetSocketAddress addr = new InetSocketAddress(ip, port);
			sChan = SocketChannel.open(addr);
			sChan.configureBlocking(false);
			sendMessage(username);
			sChan.register(selector, SelectionKey.OP_READ);
			msgViewer.append("Connected!");

		} catch (IOException e) {
			msgViewer.append("Error connecting to server.  Restart bChat and try again.");
		}
	}

	private void getMessages() {

		try {
			selector.selectNow();
			for (SelectionKey key : selector.selectedKeys()) {

				SocketChannel server = (SocketChannel)key.channel();
				selector.selectedKeys().remove(key);
				readBuffer.clear();
				server.read(readBuffer);
				readBuffer.flip();
				String msg = Charset.defaultCharset().decode(readBuffer).toString();

				if (msg.startsWith("<member>")) {

					memberViewer.setText("Members Online: \n-----------------\n");

					String members = msg.substring(8).trim();
					String[] words = members.split(",");
					for (int i = 0; i < words.length; i++) {
						memberViewer.append(words[i] + "\n");
					}
				}
				else if (msg.contains("<member>")) {

					memberViewer.setText("Members Online: \n-------------------------\n");
					String[] memberWords = msg.split("<member>");
					if (memberWords.length > 1) {
						String members = memberWords[1];
						String[] words = members.split(",");
						for (int i = 0; i < words.length; i++) {
							memberViewer.append(words[i] + "\n");
						}
						msgViewer.append(memberWords[0]);
						msgPane.getVerticalScrollBar().setValue(msgPane.getVerticalScrollBar().getMaximum());
					}
				}
				else {
					msgViewer.append(msg);
					msgPane.getVerticalScrollBar().setValue(msgPane.getVerticalScrollBar().getMaximum());
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendMessage(String text) {
		prepareForSending(writeBuffer, text);
		send(writeBuffer);

	}

	private void prepareForSending(ByteBuffer writeBuffer, String text) {
		writeBuffer.clear();
		writeBuffer.put(text.getBytes());
		writeBuffer.flip();
	}


	private void send(ByteBuffer writeBuffer) {

		int bytesToWrite = writeBuffer.remaining();
		int numWritten = 0;

		while (numWritten < bytesToWrite) {
			try {
				numWritten += sChan.write(writeBuffer);
			} catch (IOException e) {
				msgViewer.append("Unable to send message.\n");
			}
		}
	}

}
