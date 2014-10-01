package net.ijbrown.snowdroid;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

import java.io.*;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ian on 29/09/2014.
 */
public class VifReader
{
    public static void main(String[] args) throws IOException
    {
        String dataDir = "/emu/bgda/BG/DATA/";

        File file = new File(dataDir, "TAVERN.GOB");
        byte[] gobData = FileUtil.read(file);
        Gob gob = new Gob(gobData);

        ByteBuffer mainLumpData = gob.findEntry("bartley.lmp");
        Lump mainLump = new Lump(mainLumpData);

        ByteBuffer vifData = mainLump.findEntry("shopkeep1.vif");
        new VifReader().readVif(vifData);
    }

    public Model readVif(ByteBuffer vifData)
    {
        int numMeshes = vifData.getUnsignedByte(0x12);
        int offset1 = vifData.getLEInt(0x24);

        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();
        for (int meshNum = 0; meshNum < numMeshes; ++meshNum) {
            int offsetVerts = vifData.getLEInt(0x28 + meshNum * 4);
            int offsetEndVerts = vifData.getLEInt(0x2C + meshNum * 4);
            List<Chunk> chunks = readChunks(vifData, offsetVerts, offsetEndVerts);
            processChunks(modelBuilder, chunks);
        }
        return modelBuilder.end();
    }

    private void processChunks(ModelBuilder modelBuilder, List<Chunk> chunks)
    {

    }

    private class Vertex
    {
        public short x;
        public short y;
        public short z;
    }

    private class ByteVector
    {
        public byte x;
        public byte y;
        public byte z;
    }

    private class VLoc
    {
        public int v1;
        public int v2;
        public int v3;

        @Override
        public String toString()
        {
            return HexUtil.formatHexUShort(v1) + ", " + HexUtil.formatHexUShort(v2) + ", " + HexUtil.formatHexUShort(v3);
        }
    }

    private class UV
    {
        public UV(short u, short v)
        {
            this.u = u;
            this.v = v;
        }

        public short u;
        public short v;
    }

    private class VertexWeight
    {
        public int startVertex;
        public int endVertex;
        public int bone1;
        public int bone2;
        public int bone3;
        public int bone4;
        public int boneWeight1;
        public int boneWeight2;
        public int boneWeight3;
        public int boneWeight4;
    }

    private class Chunk
    {
        public int mscalID = 0;
        public GIFTag gifTag0 = null;
        public GIFTag gifTag1 = null;
        public List<Vertex> vertices = new ArrayList<Vertex>();
        public List<ByteVector> normals = new ArrayList<ByteVector>();
        public List<VLoc> vlocs = new ArrayList<VLoc>();
        public List<UV> uvs = new ArrayList<UV>();
        public List<GIFTag> directGifTags = new ArrayList<GIFTag>(1);
        public List<VertexWeight> vertexWeights = new ArrayList<VertexWeight>();
        public int[] extraVlocs;
    }

    private static final int NOP_CMD = 0;
    private static final int STCYCL_CMD = 1;
    private static final int ITOP_CMD = 4;
    private static final int STMOD_CMD = 5;
    private static final int FLUSH_CMD = 0x11;
    private static final int MSCAL_CMD = 0x14;
    private static final int STMASK_CMD = 0x20;
    private static final int DIRECT_CMD = 0x50;

    private List<Chunk> readChunks(ByteBuffer data, int offset, int endOffset)
    {
        List<Chunk> chunks = new ArrayList<Chunk>();
        Chunk currentChunk = new Chunk();
        Chunk previousChunk = null;
        while (offset < endOffset) {
            int vifCommand = data.getUnsignedByte(offset + 3) & 0x7f;
            int numCommand = data.getUnsignedByte(offset + 2);
            int immCommand = data.getLEShort(offset);
            switch (vifCommand) {
                case NOP_CMD:
                case STCYCL_CMD:
                case ITOP_CMD:
                case STMOD_CMD:
                case FLUSH_CMD:
                    offset += 4;
                    break;
                case MSCAL_CMD:
                    if (immCommand != 66 && immCommand != 68 && immCommand != 70) {
                        System.out.println("**** Microcode " + immCommand + " not supported");
                    }
                    currentChunk.mscalID = immCommand;
                    chunks.add(currentChunk);
                    previousChunk = currentChunk;
                    currentChunk = new Chunk();

                    offset += 4;
                    break;
                case STMASK_CMD:
                    offset += 8;
                    break;

                case DIRECT_CMD:
                    for (int i = 0; i < immCommand; i++)
                    {
                        GIFTag gifTag = new GIFTag();
                        gifTag.parse(data, offset + 4 + i*16);
                        currentChunk.directGifTags.add(gifTag);
                    }

                    offset += 4;
                    offset += immCommand * 16;
                    break;

                default:
                    if ((vifCommand & 0x60) == 0x60) {
                        // unpack command
                        boolean mask = ((vifCommand & 0x10) == 0x10);
                        int vn = (vifCommand >> 2) & 3;
                        int vl = vifCommand & 3;
                        int addr = immCommand & 0x1ff;
                        boolean flag = (immCommand & 0x8000) == 0x8000;
                        boolean usn = (immCommand & 0x4000) == 0x4000;

                        offset += 4;
                        if (vn == 1 && vl == 1) {
                            // v2-16
                            // The UVs come after the MSCAL instruction because the MSCAL is delayed
                            if (previousChunk != null) {
                                for (int uvnum = 0; uvnum < numCommand; ++uvnum) {
                                    short u = data.getLEShort(offset);
                                    short v = data.getLEShort(offset + 2);
                                    previousChunk.uvs.add(new UV(u, v));
                                    offset += 4;
                                }
                            } else {
                                int numBytes = numCommand * 4;
                                offset += numBytes;
                            }
                        } else if (vn == 2 && vl == 1) {
                            // v3-16
                            // each vertex is 128 bits, so num is the number of vertices
                            for (int vnum = 0; vnum < numCommand; ++vnum) {
                                if (!usn) {
                                    short x = data.getLEShort(offset);
                                    short y = data.getLEShort(offset + 2);
                                    short z = data.getLEShort(offset + 4);
                                    offset += 6;

                                    Vertex vertex = new Vertex();
                                    vertex.x = x;
                                    vertex.y = y;
                                    vertex.z = z;
                                    currentChunk.vertices.add(vertex);
                                } else {
                                    int x = data.getLEUShort(offset);
                                    int y = data.getLEUShort(offset + 2);
                                    int z = data.getLEUShort(offset + 4);
                                    offset += 6;

                                    VLoc vloc = new VLoc();
                                    vloc.v1 = x;
                                    vloc.v2 = y;
                                    vloc.v3 = z;
                                    currentChunk.vlocs.add(vloc);
                                }
                            }
                            offset = (offset + 3) & ~3;
                        } else if (vn == 2 && vl == 2) {
                            // v3-8
                            int idx = offset;
                            for (int vnum = 0; vnum < numCommand; ++vnum) {
                                ByteVector vec = new ByteVector();
                                vec.x = data.getByte(idx++);
                                vec.y = data.getByte(idx++);
                                vec.z = data.getByte(idx++);
                                currentChunk.normals.add(vec);
                            }
                            int numBytes = ((numCommand * 3) + 3) & ~3;
                            offset += numBytes;
                        } else if (vn == 3 && vl == 0) {
                            // v4-32
                            if (1 == numCommand) {
                                currentChunk.gifTag0 = new GIFTag();
                                currentChunk.gifTag0.parse(data, offset);
                            } else if (2 == numCommand) {
                                currentChunk.gifTag0 = new GIFTag();
                                currentChunk.gifTag0.parse(data, offset);
                                currentChunk.gifTag1 = new GIFTag();
                                currentChunk.gifTag1.parse(data, offset + 16);
                            }
                            int numBytes = numCommand * 16;
                            offset += numBytes;
                        } else if (vn == 3 && vl == 1) {
                            // v4-16
                            int numShorts = numCommand * 4;
                            if (usn) {
                                currentChunk.extraVlocs = new int[numShorts];
                                for (int i = 0; i < numCommand; ++i) {
                                    currentChunk.extraVlocs[i*4] = data.getLEUShort(offset + i * 8);
                                    currentChunk.extraVlocs[i * 4 + 1] = data.getLEUShort(offset + i * 8 + 2);
                                    currentChunk.extraVlocs[i * 4 + 2] = data.getLEUShort(offset + i * 8 + 4);
                                    currentChunk.extraVlocs[i * 4 + 3] = data.getLEUShort(offset + i * 8 + 6);
                                }
                            } else {
//                                Logger::getLogger()->log("Unsupported tag\n");
                            }
                            offset += numShorts * 2;

                        } else if (vn == 3 && vl == 2) {
                            // v4-8
                            int numBytes = numCommand * 4;
                            int curVertex=0;
                            for (int i = 0; i < numCommand; ++i) {
                                VertexWeight vw = new VertexWeight();
                                vw.startVertex = curVertex;
                                vw.bone1 = data.getByte(offset++) / 4;
                                vw.boneWeight1 = data.getByte(offset++);
                                vw.bone2 = data.getByte(offset++);
                                if (vw.bone2 == 0xFF) {
                                    // Single bone
                                    vw.boneWeight2 = 0;
                                    int count = data.getByte(offset++);
                                    curVertex += count;
                                } else {
                                    vw.bone2 /= 4;
                                    vw.boneWeight2 = data.getByte(offset++);
                                    ++curVertex;

                                    if (vw.boneWeight1 + vw.boneWeight2 < 0xFF)
                                    {
                                        ++i;
                                        vw.bone3 = data.getByte(offset++) / 4;
                                        vw.boneWeight3 = data.getByte(offset++);
                                        vw.bone4 = data.getByte(offset++);
                                        int bw4 = data.getByte(offset++);
                                        if (vw.bone4 != 0xFF)
                                        {
                                            vw.bone4 /= 4;
                                            vw.boneWeight4 = bw4;
                                        }
                                    } else {
                                        vw.bone3 = 0xFF;
                                        vw.boneWeight3 = 0;
                                        vw.bone4 = 0xFF;
                                        vw.boneWeight4 = 0;
                                    }

                                }
                                vw.endVertex = curVertex - 1;
                                currentChunk.vertexWeights.add(vw);
                            }
                        } else {
                            System.out.println("Unknown vnvl combination: vn=" + vn + ", vl=" + vl);
                            offset = endOffset;
                        }
                    } else {
                        System.out.println("Unknown command: " + vifCommand);
                        offset = endOffset;
                    }
                    break;
            }
        }
        return chunks;
    }
}

