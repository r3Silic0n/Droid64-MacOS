package droid64.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import droid64.d64.DiskImage;
import droid64.d64.DiskImageType;
import droid64.d64.Utility;

/**<pre style='font-family:Sans,Arial,Helvetica'>
 * Created on 27.07.2004
 *
 *   droiD64 - A graphical filemanager for D64 files
 *   Copyright (C) 2004 Wolfram Heyer
 *
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program; if not, write to the Free Software
 *   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *   eMail: the@BigBadWolF.de
 *   </pre>
 * @author wolf
 */
public class ExternalProgram {
	
	/** The disk image file name including path */
    private static final String IMAGE      = "{Image}";
    /** The selected files */
    private static final String FILES      = "{Files}";
    /** The selected files prefixed by disk image file */
    private static final String IMAGEFILES = "{ImageFiles}";
    /** The path to the target */
    private static final String TARGET     = "{Target}";
    /** Ask user for details to create a new disk image */
    private static final String NEWFILE    = "{NewFile}";
    /** Disk image type name */
    private static final String IMAGETYPE  = "{ImageType}";
    /** Name of the disk drive used to handle the disk image */
    private static final String DRIVETYPE  = "{DriveType}";
    /** Extra arguments */
    private static final String EXT_ARGUMENTS  = "{ExtArguments}";

	private String command;
	private String arguments;
	private String description;
	private String label;
	private boolean forkThread;
	private String extArguments;

	/**
	 * @param command command to run
	 * @param arguments arguments
	 * @param description description
	 * @param label label
	 * @param forkThread spawn a new thread when running the program
	 */
	public ExternalProgram(String command, String arguments, String description, String label, boolean forkThread) {
		this.command = command;
		this.arguments = arguments;
		this.description = description;
		this.label = label;
		this.forkThread = forkThread;
	}

	/**
	 * Get array of strings to execute as external command
	 * @param sourceImage path the disk image, or null if no image.
	 * @param sourceFiles list of files
	 * @param target folder
	 * @param directory current folder
	 * @param imageType type of disk image
	 * @return array of strings, with command first. Return empty array if command is null or empty string.
	 */
	public List<String> getExecute(File sourceImage, List<String> sourceFiles, File target, File directory, DiskImageType imageType) {
		if (command == null || command.isEmpty()) {
			return new ArrayList<>();
		}
		var files = new ArrayList<String>();
		var imagefiles = new ArrayList<String>();
		if (sourceFiles != null && !sourceFiles.isEmpty()) {
			sourceFiles.stream().filter(s -> s!= null && !s.isEmpty()).forEach(fName -> {
				files.add(fName);
				if (sourceImage != null && sourceImage.exists() && imageType != DiskImageType.UNDEFINED) {
						imagefiles.add(sourceImage.getPath() + ":" + fName);	
				}
			});
		}
		return buildArguments(sourceImage, target, imagefiles, files, directory, imageType);
	}

	/**
	 * VICE emulators are started from the paths set on the VICE settings tab.
	 * A macOS application bundle (.app) is resolved to the executable inside it.
	 */
	static String resolveCommand(String cmd) {
		Setting setting = null;
		String exe = cmd;
		switch (cmd) {
		case "x64sc":
		case "x64":
			setting = Setting.VICE_X64;
			exe = "x64sc";
			break;
		case "x128":
			setting = Setting.VICE_X128;
			break;
		case "xplus4":
			setting = Setting.VICE_XPLUS4;
			break;
		case "xvic":
			setting = Setting.VICE_XVIC;
			break;
		case "cbmctrl":
		case "d64copy":
			var bundled = bundledOpenCbm();
			return bundled != null ? new File(bundled, cmd).getPath() : cmd;
		default:
			return cmd;
		}
		var found = findVice(setting.getString(), exe);
		if (found == null && "x64sc".equals(exe)) {
			found = findVice(setting.getString(), "x64");
		}
		if (found == null && setting.getString() != null && new File(setting.getString().trim()).isDirectory()) {
			return cmd;
		}
		return found != null ? found.getPath() : (setting.getString() == null || setting.getString().trim().isEmpty() ? cmd : setting.getString().trim());
	}

	/**
	 * Find the executable of a VICE emulator.
	 * @param configured the path from the VICE settings (an executable, an .app, or the folder with them), may be empty
	 * @param exe name of the emulator: x64sc, x128, xplus4, xvic
	 * @return the executable, or null if it was not found
	 */
	static File findVice(String configured, String exe) {
		var places = new ArrayList<File>();
		if (configured != null && !configured.trim().isEmpty()) {
			places.add(new File(configured.trim()));
		}
		var home = System.getProperty("user.home");
		for (var dir : new String[] { "/Applications", "/Applications/VICE", "/Applications/vice", home + "/Applications",
				home + "/Applications/VICE", "/opt/homebrew/bin", "/usr/local/bin", "/opt/local/bin" }) {
			places.add(new File(dir));
		}
		for (var place : places) {
			var hit = findViceIn(place, exe);
			if (hit != null) {
				return hit;
			}
		}
		return null;
	}

	private static File findViceIn(File place, String exe) {
		if (place.isFile()) {
			if (place.getName().equals("droplet") && place.getParentFile() != null && place.getParentFile().getParentFile() != null
					&& place.getParentFile().getParentFile().getParentFile() != null) {
				var app = place.getParentFile().getParentFile().getParentFile();
				var real = findViceIn(app, exe);
				if (real != null) {
					return real;
				}
			}
			return place;
		}
		if (!place.isDirectory()) {
			return null;
		}
		var candidates = new ArrayList<File>();
		if (place.getName().endsWith(".app") && place.getParentFile() != null) {
			// VICE 3.x distribution folder: xplus4.app (droplet) next to bin/xplus4
			candidates.add(new File(place.getParentFile(), "bin/" + exe));
		}
		if (place.getName().endsWith(".app")) {
			// VICE 3.x GTK bundles: Contents/MacOS holds only a "droplet" launcher which drops the command line,
			// the emulator itself is in Contents/Resources/bin
			candidates.add(new File(place, "Contents/Resources/bin/" + exe));
			candidates.add(new File(place, "Contents/MacOS/" + exe));
		}
		candidates.add(new File(place, "bin/" + exe));
		candidates.add(new File(place, exe + ".app/Contents/Resources/bin/" + exe));
		candidates.add(new File(place, exe + ".app/Contents/MacOS/" + exe));
		candidates.add(new File(place, exe));
		candidates.add(new File(place, "bin/" + exe));
		candidates.add(new File(place, "VICE.app/Contents/Resources/bin/" + exe));
		candidates.add(new File(place, "VICE.app/Contents/MacOS/" + exe));
		candidates.add(new File(place, "VICE/" + exe + ".app/Contents/Resources/bin/" + exe));
		candidates.add(new File(place, "VICE/" + exe + ".app/Contents/MacOS/" + exe));
		for (var c : candidates) {
			if (c.isFile()) {
				return c;
			}
		}
		if (place.getName().endsWith(".app")) {
			var macos = new File(place, "Contents/MacOS").listFiles();
			if (macos != null) {
				for (var m : macos) {
					if (!m.getName().equals("droplet")) {
						return m;
					}
				}
			}
		}
		return null;
	}

	/**
	 * On macOS a program inside an .app bundle is started with the "open" command, so that it is started
	 * as a proper application (with its window) and not as a plain child process.
	 */
	static List<String> viaOpen(List<String> args, boolean mac) {
		if (!mac || args == null || args.isEmpty()) {
			return args;
		}
		var m = java.util.regex.Pattern.compile("^(.*\\.app)/Contents/MacOS/[^/]+$").matcher(args.get(0));
		if (!m.matches()) {
			return args;
		}
		var result = new ArrayList<String>();
		result.add("/usr/bin/open");
		result.add("-n");
		result.add("-a");
		result.add(m.group(1));
		if (args.size() > 1) {
			result.add("--args");
			result.addAll(args.subList(1, args.size()));
		}
		return result;
	}

	/** @return the folder with the OpenCBM tools bundled into the application, or null. */
	static File bundledOpenCbm() {
		try {
			var jar = new File(ExternalProgram.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			var dir = new File(jar.getParentFile().getParentFile(), "Resources/opencbm");
			return new File(dir, "cbmctrl").isFile() ? dir : null;
		} catch (Exception e) {	//NOSONAR
			return null;
		}
	}

	private List<String> buildArguments(File sourceImage, File target, List<String> imagefiles, List<String> files, File directory, DiskImageType imageType) {
		var args = new ArrayList<String>();
		if (Utility.isEmpty(command)) {
			return args;
		}
		args.add(resolveCommand(command));
		for (String s : arguments.split("\\s+")) {
			switch (s) {
			case IMAGE:
				if (sourceImage != null && sourceImage.exists()) {
					args.add(sourceImage.getPath());
				}
				break;
			case FILES:
				if (!files.isEmpty()) {
					args.addAll(files);
				}
				break;
			case IMAGEFILES:
				if (!imagefiles.isEmpty()) {
					args.addAll(imagefiles);
				} else if (sourceImage != null && sourceImage.exists() && sourceImage.isFile()) {
					args.add(sourceImage.getPath());
				} else if (!files.isEmpty() && new File(files.get(0)).isFile()) {
					args.add(files.get(0));
				}
				break;
			case TARGET:
				if (target != null) {
					args.add(target.getPath());
				}
				break;
			case NEWFILE:
				var newFile = FileDialogHelper.openImageFileDialog(directory, null, true);
				if (newFile == null) {
					return new ArrayList<>();
				}
				args.add(newFile.getPath());
				break;
			case IMAGETYPE:
				args.add(DiskImage.getImageTypeName(imageType));
				break;
			case DRIVETYPE:
				args.add(imageType.driveName);
				break;
			case EXT_ARGUMENTS:
				if (!Utility.isEmpty(extArguments)) {
					args.add(extArguments);
				}
				break;
			default:
				if (!Utility.isEmpty(s)) {
					args.add(s);
				}
			}
		}
		return args;
	}

	/**
	 * Run the external program.
	 * @param imageFile the image file
	 * @param execArgs arguments as parsed by {@link #getExecute(File, List, File, File, DiskImageType)}
	 * @param mainPanel used to log output from command
	 */
	public void runProgram(final File imageFile, final List<String> givenArgs, final MainPanel mainPanel) {
		final var execArgs = givenArgs;
		if (execArgs == null || execArgs.isEmpty()) {
			mainPanel.appendConsole("No command to execute!");
		}
		try {
			mainPanel.appendConsole("Executing: " + execArgs);
			if (forkThread) {
				var runner = new Thread() {
					@Override
					public void run() {
						runProgramThread(imageFile, execArgs, mainPanel);
					}
				};
				runner.start();
			} else {
				runProgramThread(imageFile, execArgs, mainPanel);
				mainPanel.getLeftDiskPanel().reloadDiskImage(true);
				mainPanel.getRightDiskPanel().reloadDiskImage(true);
			}
		} catch (Exception e) {	//NOSONAR
			mainPanel.appendConsole('\n'+e.getMessage());
		}
	}

	/**
	 * A VICE that is already running (even with its window closed) takes the new image from the command line
	 * and ignores it. So a running emulator is closed before the next one is started.
	 * @return true if a running emulator was closed
	 */
	static boolean stopRunningEmulator(List<String> execArgs, boolean mac, Consumer<String> log) {
		if (!mac || execArgs == null || execArgs.isEmpty()) {
			return false;
		}
		var name = new File(execArgs.get(0)).getName();
		if (!java.util.Arrays.asList("x64sc", "x64", "x128", "xplus4", "xvic").contains(name)) {
			return false;
		}
		try {
			if (new ProcessBuilder("/usr/bin/pgrep", "-x", name).start().waitFor() != 0) {
				return false;
			}
			log.accept("Closing the running " + name + " first.");
			new ProcessBuilder("/usr/bin/pkill", "-x", name).start().waitFor();
			for (int i = 0; i < 30; i++) {
				Thread.sleep(100);
				if (new ProcessBuilder("/usr/bin/pgrep", "-x", name).start().waitFor() != 0) {
					return true;
				}
			}
			new ProcessBuilder("/usr/bin/pkill", "-9", "-x", name).start().waitFor();
			Thread.sleep(300);
			return true;
		} catch (Exception e) {	//NOSONAR
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static int runProgramThread(File folder, List<String> givenArgs, MainPanel mainPanel) {
		List<String> execArgs = givenArgs;
		int vicePort = ViceRemote.portFor(execArgs.isEmpty() ? "" : new File(execArgs.get(0)).getName());
		if (vicePort > 0) {
			var file = ViceRemote.startFile(execArgs);
			if (file != null && ViceRemote.autostart(vicePort, file, mainPanel::appendConsole)) {
				mainPanel.appendConsole("The running emulator was reset and started " + file);
				return 0;
			}
			// no emulator listens on the monitor port: close one that was started in another way, then start a new one
			stopRunningEmulator(execArgs, System.getProperty("os.name", "").startsWith("Mac"), mainPanel::appendConsole);
			var withMonitor = new ArrayList<String>();
			withMonitor.add(execArgs.get(0));
			withMonitor.addAll(ViceRemote.monitorOptions(vicePort));
			withMonitor.addAll(execArgs.subList(1, execArgs.size()));
			execArgs = withMonitor;
		}
		var output = new ArrayList<String>();
		var errors = new ArrayList<String>();
		long started = System.currentTimeMillis();
		int rc = runProgramThread(folder, execArgs, line -> {
			output.add(line);
			mainPanel.appendConsole(line);
		}, err -> {
			errors.add(err.trim());
			mainPanel.appendConsole(err);
		});
		var bundled = bundledOpenCbm();
		boolean openCbm = bundled != null && !execArgs.isEmpty() && execArgs.get(0).startsWith(bundled.getPath());
		long seconds = (System.currentTimeMillis() - started) / 1000;
		mainPanel.appendConsole("Finished: " + execArgs.get(0) + ", exit code " + rc + ", " + seconds + " s");
		boolean quick = rc == 0 && seconds < 2 && !execArgs.isEmpty() && new File(execArgs.get(0)).getName().startsWith("x");
		if ((rc != 0 || quick) && !execArgs.isEmpty()) {
			var name = new File(execArgs.get(0)).getName();
			var text = output.isEmpty() ? (quick ? "The program ended after " + seconds + " s without any output, exit code 0.\n" : "The command failed (exit code " + rc + "): ") + execArgs : String.join("\n", output);
			if (!openCbm && output.isEmpty() && !quick) {
				text = "Could not run " + execArgs.get(0) + ".\n" + (errors.isEmpty() ? text : String.join("\n", errors))
						+ (name.startsWith("x") ? "\n\nCheck the path of the VICE emulator in Settings, on the VICE tab." : "");
			}
			final var message = text;
			javax.swing.SwingUtilities.invokeLater(() ->
					GuiHelper.showErrorMessage(mainPanel.getParent(), openCbm ? "OpenCBM" : name, "%s", message));
		}
		return rc;
	}

	/**
	 * @param folder the current folder for the command
	 * @param execArgs the command and its arguments
	 * @param outputConsumer consumer of the output
	 * @param errorConsumer the receiver of error printouts
	 * @return return code. Normally 0 means success.
	 */
	public static int runProgramThread(File folder, List<String> execArgs, Consumer<String> outputConsumer, Consumer<String> errorConsumer) {
		int returnCode=1;
		try {
			var builder = new ProcessBuilder()
					.directory(folder != null ? folder : new File("."))
					.redirectOutput(Redirect.PIPE)
					.redirectErrorStream(true)
					.command(execArgs);
			var bundled = bundledOpenCbm();
			if (bundled != null && !execArgs.isEmpty() && execArgs.get(0).startsWith(bundled.getPath())) {
				// the bundled OpenCBM finds its configuration and plugin here
				builder.environment().put("OPENCBM_HOME", bundled.getPath());
			}
			var process = builder.start();
			try (var br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = br.readLine()) != null) {
					outputConsumer.accept(line);
				}
				returnCode = process.waitFor();
			}
		} catch (Exception e) {	//NOSONAR
			errorConsumer.accept('\n'+e.getMessage());
		}
		return returnCode;
	}

	@Override
	public String toString() {
		return label;
	}

	/**
	 * @param command program to run
	 * @param arguments arguments to program
	 * @param description description
	 * @param label label
	 * @param forkThread forkThread
	 */
	public void setValues(String command, String arguments, String description, String label, boolean forkThread) {
		this.command = command;
		this.arguments = arguments;
		this.description = description;
		this.label = label;
		this.forkThread = forkThread;
	}

	/**
	 * @return arguments
	 */
	public String getArguments() {
		return arguments;
	}

	/**
	 * @return command
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * @return description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @return label
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * @param string arguments
	 */
	public void setArguments(String string) {
		arguments = string;
	}

	/**
	 * @param string command
	 */
	public void setCommand(String string) {
		command = string;
	}

	/**
	 * @param string description
	 */
	public void setDescription(String string) {
		description = string;
	}

	/**
	 * @param string label
	 */
	public void setLabel(String string) {
		label = string;
	}

	public boolean isForkThread() {
		return forkThread;
	}

	public void setForkThread(boolean forkThread) {
		this.forkThread = forkThread;
	}

	public String getExtArguments() {
		return extArguments;
	}

	public void setExtArguments(String extArguments) {
		this.extArguments = extArguments;
	}
}
