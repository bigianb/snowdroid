package net.ijbrown.snowdroid;

/**
 * A range of bytes within a larger array of bytes.
 */
public class ByteBuffer
{
    public ByteBuffer(byte[] data, int startOffset, int len){
        this.data= data;
        this.startOffset = startOffset;
        this.len = len;
    }

    public byte[] data;
    public int startOffset;
    public int len;
}
