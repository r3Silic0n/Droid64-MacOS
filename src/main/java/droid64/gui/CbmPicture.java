package droid64.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * <pre style='font-family:sans-serif;'>
 * Created on 2015-Oct-17
 *
 *   droiD64 - A graphical file manager for D64 files
 *   Copyright (C) 2015 Henrik Wetterström
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
 *   http://droid64.sourceforge.net
 *
 * &#64;author Henrik
 * </pre>
 */
public class CbmPicture {

	/** Image data */
	private final byte[] data;
	private final String name;
	private CommodoreGraphics.Format format = CommodoreGraphics.Format.AUTO;
	private String description = "";
	private CommodoreGraphics.Result result;

	/**
	 * Constructor.
	 * Supports the common C64 and Plus/4 picture formats, JPEG, GIF and PNG.
	 * @param data image data
	 * @param name name of picture
	 */
	public CbmPicture(byte[] data, String name) {
		this.name = name;
		this.data = data == null ? new byte[0] : data;
	}

	/** @param format format to use instead of detecting it */
	public void setFormat(CommodoreGraphics.Format format) {
		this.format = format == null ? CommodoreGraphics.Format.AUTO : format;
	}

	/** @return the latest decoded Commodore picture, or null */
	public CommodoreGraphics.Result getResult() {
		return result;
	}

	/** @return description of what was shown by the latest getImage() */
	public String getDescription() {
		return description;
	}

	/**
	 * @return BufferedImage of the image data.
	 * @throws IOException when error
	 */
	public BufferedImage getImage() throws IOException {
		result = null;
		if (isGif(data) || isJpeg(data) || isPng(data)) {
			try (InputStream in = new ByteArrayInputStream(data)) {
				var image = ImageIO.read(in);
				description = "PC picture, " + image.getWidth() + " x " + image.getHeight();
				return image;
			}
		}
		var result = CommodoreGraphics.decode(data, format);
		if (result == null) {
			description = "Not a known picture format";
			return CommodoreGraphics.message("Not a known picture format (" + data.length + " bytes)");
		}
		this.result = result;
		description = result.description;
		return result.image;
	}

	private boolean byteArrayStartsWith(byte[] data, byte[] compare) {
		if (data.length < compare.length) {
			return false;
		}
		for (int i=0; i< compare.length; i++) {
			if (data[i] != compare[i]) {
				return false;
			}
		}
		return true;
	}

	private boolean isGif(byte[] data) {
		final byte[] gifMatch1 = new byte[] { 0x47, 0x49, 0x46, 0x38, 0x37, 0x61 };
		final byte[] gifMatch2 = new byte[] { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 };
		return byteArrayStartsWith(data, gifMatch1) || byteArrayStartsWith(data, gifMatch2);
	}

	private boolean isJpeg(byte[] data) {
		if (data.length < 4) {
			return false;
		}
		return (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8
				&& (data[data.length - 2] & 0xff) == 0xff && (data[data.length - 1] & 0xff) == 0xd9;
	}

	private boolean isPng(byte[] data) {
		final byte[] pngMatch = new byte[] { (byte)(0x89 & 0xff), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a };
		return byteArrayStartsWith(data, pngMatch);
	}

	public String getName() {
		return name;
	}

}
