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

    public byte getByte(int offset)
    {
        return data[startOffset + offset];
    }

    public int getUnsignedByte(int offset)
    {
        return data[startOffset + offset] & 0xFF;
    }

    public int getLEInt(int offset)
    {
        return DataUtil.getLEInt(data, startOffset + offset);
    }

    public short getLEShort(int offset){ return DataUtil.getLEShort(data, startOffset+offset);}

    public int getLEUShort(int offset){ return DataUtil.getLEUShort(data, startOffset+offset);}


    public byte[] data;
    public int startOffset;
    public int len;
}
