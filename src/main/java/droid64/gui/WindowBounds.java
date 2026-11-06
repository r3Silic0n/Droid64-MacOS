package droid64.gui;

import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Remembers the position and size of a window in a setting ("x,y,w,h") and restores it the next time. */
final class WindowBounds {

	private WindowBounds() {
	}

	/** Restore the saved bounds (only when a good part of the window is on the screen) and save them when the window is closed. */
	static void remember(Window window, Setting setting) {
		restore(window, setting);
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				save(window, setting);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				// DISPOSE_ON_CLOSE and Close buttons call dispose() directly, the bounds are still valid then
				save(window, setting);
			}
		});
	}

	static void restore(Window window, Setting setting) {
		try {
			var p = setting.getString().split(",");
			if (p.length != 4) {
				return;
			}
			int x = Integer.parseInt(p[0].trim());
			int y = Integer.parseInt(p[1].trim());
			int w = Integer.parseInt(p[2].trim());
			int h = Integer.parseInt(p[3].trim());
			var screen = Toolkit.getDefaultToolkit().getScreenSize();
			if (w >= 300 && h >= 200 && x + w > 100 && y + h > 100 && x < screen.width - 100 && y < screen.height - 100) {
				window.setBounds(x, y, w, h);
			}
		} catch (RuntimeException e) {
			// no saved bounds: keep the default
		}
	}

	static void save(Window window, Setting setting) {
		if (!window.isShowing() && window.getWidth() <= 0) {
			return;
		}
		var b = window.getBounds();
		var text = b.x + "," + b.y + "," + b.width + "," + b.height;
		if (text.equals(setting.getString())) {
			return;
		}
		setting.set(text);
		try {
			Setting.save();
		} catch (java.io.IOException e) {
			// saved at exit with the other settings
		}
	}
}
