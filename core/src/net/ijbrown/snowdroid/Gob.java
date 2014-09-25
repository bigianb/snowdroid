package net.ijbrown.snowdroid;

import java.util.HashMap;
import java.util.Map;

/**
 * A GOB file which is a collection of Lump files.
 */
public class Gob
{
    public Gob(byte[] data)
    {
        this.data = data;
        buildDirectory();
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
        int offset=0;
        String lmpName = DataUtil.collectString(data, offset);
        while (!lmpName.isEmpty()){
            int lmpDataOffset = DataUtil.getLEInt(data, offset + 0x20);

            directory.put(lmpName, new ByteBuffer(data, lmpDataOffset, data.length - lmpDataOffset));

            offset += 0x28;
            lmpName = DataUtil.collectString(data, offset);
        }
    }

    private Map<String, ByteBuffer> directory;
    private byte[] data;
}
