/*  Copyright (C) 2011-2014 Ian Brown

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.ijbrown.snowdroid;

import com.badlogic.gdx.graphics.Pixmap;

/**
 * Decodes a Texture.
 */
public class TexReader
{
    // Texture format is something like as follows:
    // 16 byte header.
    //    short width
    //    short height

    // Then starting at address 0x80
    // GS Packet

    // Currently it is assumed that all the image data is in one GIFTag segment.
    // This is not the case for some textures (e.g. chest_large), so the code will
    // need extending to read the GIFTags properly.

    public Pixmap read(ByteBuffer fileDataBuffer)
    {
        Pixmap pixmap = null;
        int finalw = fileDataBuffer.getLEShort(0);
        int finalh = fileDataBuffer.getLEShort(2);
        int sourcew = finalw;
        int sourceh = finalh;
        PalEntry[] pixels = null;

        int curIdx = 0x80;
        GIFTag gifTag = new GIFTag();
        gifTag.parse(fileDataBuffer, curIdx);

        // This is basically heuristics
        if (gifTag.nloop == 4) {

            int palw = fileDataBuffer.getLEShort(curIdx + 0x30);
            int palh = fileDataBuffer.getLEShort(curIdx + 0x34);

            curIdx += 0x50;
            GIFTag gifTag2 = new GIFTag();
            gifTag2.parse(fileDataBuffer, curIdx);

            // 8 bit palletised
            PalEntry[] palette = PalEntry.readPalette(fileDataBuffer, curIdx + 0x10, palw, palh);

            palette = PalEntry.unswizzlePalette(palette);

            int palLen = palw * palh * 4;
            curIdx += (palLen + 0x10);

            GIFTag gifTag50 = new GIFTag();
            gifTag50.parse(fileDataBuffer, curIdx);
            curIdx += 0x20;

            int dbw = (sourcew / 2 + 0x07) & ~0x07;
            int dbh = (sourceh / 2 + 0x07) & ~0x07;

            int endIndex = fileDataBuffer.len;

            // The following should be a loop, there are repeating sections
            while (curIdx < endIndex - 0x10) {
                GIFTag gifTag3 = new GIFTag();
                gifTag3.parse(fileDataBuffer, curIdx);

                int dimOffset = 0x10;

                int thisRrw = fileDataBuffer.getLEShort(curIdx + dimOffset);
                int thisRrh = fileDataBuffer.getLEShort(curIdx + dimOffset + 4);

                int startx = fileDataBuffer.getLEShort(curIdx + dimOffset + 20);
                int starty = fileDataBuffer.getLEShort(curIdx + dimOffset + 22);

                curIdx += gifTag.nloop * 0x10 + 0x10;
                pixels = readPixels32(pixels, fileDataBuffer, palette, curIdx, startx, starty, thisRrw, thisRrh, dbw,
                                      dbh);
                curIdx += thisRrw * thisRrh * 4;
            }

            if (palLen != 64) {
                pixels = unswizzle8bpp(pixels, dbw * 2, dbh * 2);
                sourcew = dbw * 2;
                sourceh = dbh * 2;
            } else {
                sourcew = dbw;
                sourceh = dbh;
            }

        } else if (gifTag.nloop == 3) {
            GIFTag gifTag2 = new GIFTag();
            gifTag2.parse(fileDataBuffer, 0xC0);
            System.out.println(gifTag2.toString());

            if (gifTag2.flg == 2) {
                // image mode
                pixels = readPixels32(pixels, fileDataBuffer, 0xD0, finalw, finalh);
            }
        }
        if (finalw != 0 && pixels != null) {
            pixmap = new Pixmap(finalw, finalh, Pixmap.Format.RGBA8888);

            for (int y = 0; y < sourceh; ++y) {
                for (int x = 0; x < sourcew; ++x) {
                    PalEntry pixel = pixels[y * sourcew + x];
                    if (pixel != null) {
                        pixmap.drawPixel(x, y, pixel.rgba());
                    }
                }
            }
        }
        return pixmap;
    }

    private PalEntry[] unswizzle8bpp(PalEntry[] pixels, int w, int h)
    {
        PalEntry[] unswizzled = new PalEntry[pixels.length];

        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {

                int block_location = (y & (~0xf)) * w + (x & (~0xf)) * 2;
                int swap_selector = (((y + 2) >> 2) & 0x1) * 4;
                int posY = (((y & (~3)) >> 1) + (y & 1)) & 0x7;
                int column_location = posY * w * 2 + ((x + swap_selector) & 0x7) * 4;

                int byte_num = ((y >> 1) & 1) + ((x >> 2) & 2);     // 0,1,2,3

                int idx = block_location + column_location + byte_num;
                if (idx >= pixels.length) {
                    System.out.println("x");
                } else {
                    unswizzled[(y * w) + x] = pixels[idx];
                }
            }
        }

        return unswizzled;
    }


    private PalEntry[] readPixels32(PalEntry[] pixels, ByteBuffer fileDataBuffer,
                                    PalEntry[] palette, int startOffset,
                                    int startx, int starty,
                                    int rrw, int rrh, int dbw, int dbh)
    {
        byte[] fileData = fileDataBuffer.data;
        int idx = startOffset + fileDataBuffer.startOffset;
        if (palette.length == 256) {
            int numDestBytes = dbh * dbw * 4;
            int widthBytes = dbw * 4;
            if (pixels == null) {
                pixels = new PalEntry[numDestBytes];
            }
            for (int y = 0; y < rrh && (y + starty) < dbh; ++y) {
                for (int x = 0; x < rrw; ++x) {
                    int destIdx = (y + starty) * widthBytes + (x + startx) * 4;
                    pixels[destIdx++] = palette[fileData[idx++] & 0xFF];
                    pixels[destIdx++] = palette[fileData[idx++] & 0xFF];
                    pixels[destIdx++] = palette[fileData[idx++] & 0xFF];
                    pixels[destIdx] = palette[fileData[idx++] & 0xFF];
                }
            }
            return pixels;
        } else {
            int numDestBytes = rrh * dbw;
            if (pixels == null) {
                pixels = new PalEntry[numDestBytes];
            }
            boolean lowbit = false;
            for (int y = 0; y < rrh; ++y) {
                for (int x = 0; x < rrw; ++x) {
                    int destIdx = (y + starty) * dbw + x + startx;
                    if (lowbit) {
                        pixels[destIdx] = palette[fileData[idx] >> 4 & 0x0F];
                        idx++;
                    } else {
                        pixels[destIdx] = palette[fileData[idx] & 0x0F];
                    }
                    lowbit = !lowbit;
                }
            }
            return pixels;
        }
    }

    private PalEntry[] readPixels32(PalEntry[] pixels, ByteBuffer fileDataBuffer, int startOffset, int w, int h)
    {
        byte[] fileData = fileDataBuffer.data;
        int numPixels = w * h;
        if (null == pixels) {
            pixels = new PalEntry[numPixels];
        }
        int destIdx = 0;
        int endOffset = startOffset + numPixels * 4;
        for (int idx = startOffset + fileDataBuffer.startOffset; idx < endOffset; ) {
            PalEntry pe = new PalEntry();
            pe.r = fileData[idx++];
            pe.g = fileData[idx++];
            pe.b = fileData[idx++];
            pe.a = fileData[idx++];

            pixels[destIdx++] = pe;
        }

        return pixels;
    }


}
