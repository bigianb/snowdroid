package net.ijbrown.snowdroid;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates a lump file.
 */
public class Lump
{
    public Lump(byte[] data, int startOffset)
    {
        this.data = data;
        this.startOffset = startOffset;
        buildDirectory();
    }

    public Lump(ByteBuffer buffer)
    {
        this.data = buffer.data;
        this.startOffset = buffer.startOffset;
    }

    public ByteBuffer findEntry(String name)
    {
        return directory.get(name);
    }

    public byte[] getData()
    {
        return data;
    }

    public void buildDirectory()
    {
        directory = new HashMap<String, ByteBuffer>();
        int numFiles = DataUtil.getLEInt(data, startOffset);

        for (int fileNo=0; fileNo < numFiles; ++fileNo){
            int headerOffset = startOffset + 4 + fileNo * 0x40;
            String subfileName = DataUtil.collectString(data, headerOffset);

            int subOffset = DataUtil.getLEInt(data, headerOffset + 0x38);
            int subLen = DataUtil.getLEInt(data, headerOffset + 0x3C);

            directory.put(subfileName, new ByteBuffer(data, subOffset, subLen));
        }
    }

    private Map<String, ByteBuffer> directory;
    private int startOffset;
    private byte[] data;
}
