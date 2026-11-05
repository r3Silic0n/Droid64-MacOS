package droid64.gui;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Talks to a VICE emulator that is already running, through its binary monitor, so that it can be told
 * to reset and start another disk image or program instead of starting a second emulator.
 */
final class ViceRemote {

	private static final int CMD_AUTOSTART = 0xdd;
	private static final int HEADER_LENGTH = 12;

	private ViceRemote() {
	}

	/** @return the TCP port of the binary monitor for an emulator executable name, or 0 if it is not a VICE emulator */
	static int portFor(String exeName) {
		switch (exeName) {
		case "x64sc":
		case "x64":
			return 6502;
		case "x128":
			return 6503;
		case "xplus4":
			return 6504;
		case "xvic":
			return 6505;
		default:
			return 0;
		}
	}

	/** The command line options that make a new emulator listen on its binary monitor port. */
	static List<String> monitorOptions(int port) {
		return java.util.Arrays.asList("-binarymonitor", "-binarymonitoraddress", "ip4://127.0.0.1:" + port);
	}

	/**
	 * The file that the command line starts: the last argument of "emulator -drive8type TYPE file".
	 * @return the image or "image:program" argument, or null if the command line has another form
	 */
	static String startFile(List<String> args) {
		if (args.size() >= 4 && "-drive8type".equals(args.get(1))) {
			return args.get(args.size() - 1);
		}
		return null;
	}

	/**
	 * Ask a running emulator to reset and start the image or program.
	 * @return true if the running emulator accepted it, false if none is running there or it refused
	 */
	static boolean autostart(int port, String file, Consumer<String> log) {
		try (var socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
			socket.setSoTimeout(10000);
			var name = file.getBytes(StandardCharsets.UTF_8);
			var body = ByteBuffer.allocate(4 + name.length).order(ByteOrder.LITTLE_ENDIAN);
			body.put((byte) 1);		// run after loading
			body.putShort((short) 0);	// file index
			body.put((byte) name.length);
			body.put(name);
			var request = ByteBuffer.allocate(11 + body.capacity()).order(ByteOrder.LITTLE_ENDIAN);
			request.put((byte) 2);		// start of command
			request.put((byte) 2);		// API version
			request.putInt(body.capacity());
			request.putInt(1);		// request id
			request.put((byte) CMD_AUTOSTART);
			request.put(body.array());
			OutputStream out = socket.getOutputStream();
			out.write(request.array());
			out.flush();
			var in = new DataInputStream(socket.getInputStream());
			for (int i = 0; i < 20; i++) {
				var header = new byte[HEADER_LENGTH];
				in.readFully(header);
				var h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
				h.get();
				h.get();
				int length = h.getInt();
				int type = h.get() & 0xff;
				int error = h.get() & 0xff;
				if (length < 0 || length > 1_000_000) {
					return false;
				}
				in.readFully(new byte[length]);
				if (type == CMD_AUTOSTART) {
					if (error != 0) {
						log.accept("The running emulator refused the command (error " + error + ").");
					}
					return error == 0;
				}
			}
			return false;
		} catch (IOException e) {
			return false;
		}
	}
}
