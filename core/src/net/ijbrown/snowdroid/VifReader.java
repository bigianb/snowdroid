package net.ijbrown.snowdroid;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a .vif file, converts it to a libgdx model.
 */
public class VifReader
{
    private static final int NOP_CMD = 0;
    private static final int STCYCL_CMD = 1;
    private static final int ITOP_CMD = 4;
    private static final int STMOD_CMD = 5;
    private static final int FLUSH_CMD = 0x11;
    private static final int MSCAL_CMD = 0x14;
    private static final int STMASK_CMD = 0x20;
    private static final int DIRECT_CMD = 0x50;

    public Model readVif(ByteBuffer vifData, Material material, float uscale, float vscale)
    {
        int numMeshes = vifData.getUnsignedByte(0x12);
        int offset1 = vifData.getLEInt(0x24);

        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();
        for (int meshNum = 0; meshNum < numMeshes; ++meshNum) {
            int offsetVerts = vifData.getLEInt(0x28 + meshNum * 4);
            int offsetEndVerts = vifData.getLEInt(0x2C + meshNum * 4);
            List<Chunk> chunks = readChunks(vifData, offsetVerts, offsetEndVerts);
            processChunks(modelBuilder, "mesh" + meshNum, material, chunks, uscale, vscale);
        }
        return modelBuilder.end();
    }

    private void processChunks(ModelBuilder modelBuilder, String id, Material material, List<Chunk> chunks,
                               float uscale, float vscale)
    {
        uscale /= 16.0f;
        vscale /= 16.0f;

        int numVertices = 0;
        int numWeights = 0;
        for (Chunk chunk : chunks) {
            numVertices += chunk.vertices.size();
            numWeights += chunk.vertexWeights.size();
        }

        VertexAttributes vertexAttributes;
        if (numWeights > 0) {
            vertexAttributes = new VertexAttributes(VertexAttribute.Position(),
                                                    VertexAttribute.Normal(),
                                                    VertexAttribute.TexCoords(0),
                                                    VertexAttribute.BoneWeight(0));
        } else {
            vertexAttributes = new VertexAttributes(VertexAttribute.Position(),
                                                    VertexAttribute.Normal(),
                                                    VertexAttribute.TexCoords(0));
        }

        // tri-strip would be more efficient, but we would need to figure out the winding rule
        // as it is not consistent in the vif files.
        MeshPartBuilder meshBuilder = modelBuilder.part(id, GL20.GL_TRIANGLES, vertexAttributes, material);

        // The vif format can specify multiple uvs per vertex, so we need to pre-process and
        // duplicate where necessary.
        List<Vector3> positions = new ArrayList<Vector3>(numVertices * 2);
        List<Vector3> normals = new ArrayList<Vector3>(numVertices * 2);
        List<Vector2> uvs = new ArrayList<Vector2>(numVertices * 2);
        List<VertexWeight> vertexWeights = new ArrayList<VertexWeight>(numWeights);
        final int vindexStart = meshBuilder.lastIndex() + 1;
        int vstart = 0;
        for (Chunk chunk : chunks) {
            if ((chunk.gifTag0.prim & 0x07) != 4) {
                throw new RuntimeException("Can only deal with tri-strips");
            }
            int numChunkVertices = chunk.vertices.size();
            for (int vertexNum = 0; vertexNum < numChunkVertices; ++vertexNum) {
                Vertex v = chunk.vertices.get(vertexNum);
                positions.add(new Vector3(v.x / 16.0f, v.y / 16.0f, v.z / 16.0f));
                ByteVector n = chunk.normals.get(vertexNum);
                normals.add(new Vector3(n.x / 127.0f, n.y / 127.0f, n.z / 127.0f));
                uvs.add(null);
            }
            for (final VertexWeight vw : chunk.vertexWeights) {
                if (vw.startVertex <= (numChunkVertices - 1)) {
                    VertexWeight vwAdjusted = new VertexWeight(vw);
                    vwAdjusted.startVertex += vstart;
                    if (vwAdjusted.endVertex >= numChunkVertices) {
                        vwAdjusted.endVertex = numChunkVertices - 1;
                    }
                    vwAdjusted.endVertex += vstart;
                    vertexWeights.add(vwAdjusted);
                }
            }
            final int vstripLen = chunk.gifTag0.nloop;
            int[] vstrip = new int[vstripLen];
            final int regsPerVertex = chunk.gifTag0.nreg;
            final int numVlocs = chunk.vlocs.size();
            for (int vlocIndx = 2; vlocIndx < numVlocs; ++vlocIndx) {
                int v = vlocIndx - 2;
                VLoc vloc = chunk.vlocs.get(vlocIndx);
                int stripIdx2 = (vloc.v2 & 0x1FF) / regsPerVertex;
                int stripIdx3 = (vloc.v3 & 0x1FF) / regsPerVertex;
                if (stripIdx3 < vstripLen && stripIdx2 < vstripLen) {
                    vstrip[stripIdx3] = vstrip[stripIdx2] & 0x1FF;

                    boolean skip2 = (vloc.v3 & 0x8000) == 0x8000;
                    if (skip2) {
                        vstrip[stripIdx3] |= 0x8000;
                    }
                }
                int stripIdx = (vloc.v1 & 0x1FF) / regsPerVertex;
                boolean skip = (vloc.v1 & 0x8000) == 0x8000;

                if (v < numChunkVertices && stripIdx < vstripLen) {
                    vstrip[stripIdx] = skip ? (v | 0x8000) : v;
                }
            }
            final int numExtraVlocs = chunk.extraVlocs == null ? 0 : chunk.extraVlocs[0];
            for (int extraVloc = 0; extraVloc < numExtraVlocs; ++extraVloc) {
                int idx = extraVloc * 4 + 4;
                int stripIndxSrc = (chunk.extraVlocs[idx] & 0x1FF) / regsPerVertex;
                int stripIndxDest = (chunk.extraVlocs[idx + 1] & 0x1FF) / regsPerVertex;
                vstrip[stripIndxDest] = (chunk.extraVlocs[idx + 1] & 0x8000) | (vstrip[stripIndxSrc] & 0x1FF);

                stripIndxSrc = (chunk.extraVlocs[idx + 2] & 0x1FF) / regsPerVertex;
                stripIndxDest = (chunk.extraVlocs[idx + 3] & 0x1FF) / regsPerVertex;
                vstrip[stripIndxDest] = (chunk.extraVlocs[idx + 3] & 0x8000) | (vstrip[stripIndxSrc] & 0x1FF);
            }

            for (int i = 2; i < vstripLen; ++i) {
                int vidx1 = vstart + (vstrip[i - 2] & 0xFF);
                int vidx2 = vstart + (vstrip[i - 1] & 0xFF);
                int vidx3 = vstart + (vstrip[i] & 0xFF);

                int uv1 = i - 2;
                int uv2 = i - 1;
                int uv3 = i;

                if ((vstrip[i] & 0x8000) == 0) {
                    Vector2 vuv1 = new Vector2(chunk.uvs.get(uv1).u * uscale, chunk.uvs.get(uv1).v * vscale);
                    Vector2 vuv2 = new Vector2(chunk.uvs.get(uv2).u * uscale, chunk.uvs.get(uv2).v * vscale);
                    Vector2 vuv3 = new Vector2(chunk.uvs.get(uv3).u * uscale, chunk.uvs.get(uv3).v * vscale);

                    if (uvs.get(vidx1) != null && !uvs.get(vidx1).equals(vuv1)) {
                        // There is more than one uv assignment to this vertex, so we need to duplicate it
                        int originalVIdx = vidx1;
                        vidx1 = positions.size();
                        positions.add(positions.get(originalVIdx));
                        normals.add(normals.get(originalVIdx));
                        uvs.add(null);
                        ++numChunkVertices;
                        VertexWeight weight = FindVertexWeight(vertexWeights, originalVIdx - vstart);
                        if (weight.boneWeight1 > 0) {
                            VertexWeight vw = new VertexWeight(weight);
                            vw.startVertex = vidx1;
                            vw.endVertex = vidx1;
                            vertexWeights.add(vw);
                        }
                    }
                    if (uvs.get(vidx2) != null && !uvs.get(vidx2).equals(vuv2)) {
                        // There is more than one uv assignment to this vertex, so we need to duplicate it
                        int originalVIdx = vidx2;
                        vidx2 = positions.size();
                        positions.add(positions.get(originalVIdx));
                        normals.add(normals.get(originalVIdx));
                        uvs.add(null);
                        ++numChunkVertices;
                        VertexWeight weight = FindVertexWeight(vertexWeights, originalVIdx - vstart);
                        if (weight.boneWeight1 > 0) {
                            VertexWeight vw = new VertexWeight(weight);
                            vw.startVertex = vidx2;
                            vw.endVertex = vidx2;
                            vertexWeights.add(vw);
                        }
                    }
                    if (uvs.get(vidx3) != null && !uvs.get(vidx3).equals(vuv3)) {
                        // There is more than one uv assignment to this vertex, so we need to duplicate it
                        int originalVIdx = vidx3;
                        vidx3 = positions.size();
                        positions.add(positions.get(originalVIdx));
                        normals.add(normals.get(originalVIdx));
                        uvs.add(null);
                        ++numChunkVertices;
                        VertexWeight weight = FindVertexWeight(vertexWeights, originalVIdx - vstart);
                        if (weight.boneWeight1 > 0) {
                            VertexWeight vw = new VertexWeight(weight);
                            vw.startVertex = vidx3;
                            vw.endVertex = vidx3;
                            vertexWeights.add(vw);
                        }
                    }

                    uvs.set(vidx1, vuv1);
                    uvs.set(vidx2, vuv2);
                    uvs.set(vidx3, vuv3);

                    meshBuilder.triangle((short) (vidx1 + vindexStart), (short) (vidx2 + vindexStart),
                                         (short) (vidx3 + vindexStart));
                }
            }
            vstart += numChunkVertices;
        }

        Color colour = Color.WHITE;
        for (int i = 0; i < positions.size(); ++i) {
            meshBuilder.vertex(positions.get(i), normals.get(i), colour, uvs.get(i));
        }
    }

    private VertexWeight FindVertexWeight(List<VertexWeight> weights, int vertexNum)
    {
        for (VertexWeight weight : weights) {
            if (vertexNum >= weight.startVertex && vertexNum <= weight.endVertex) {
                return weight;
            }
        }
        if (!weights.isEmpty()) {
            //       Logger::getLogger()->log("Failed to find vertex weight\n");
        }
        return new VertexWeight();
    }

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
                    for (int i = 0; i < immCommand; i++) {
                        GIFTag gifTag = new GIFTag();
                        gifTag.parse(data, offset + 4 + i * 16);
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
                                    currentChunk.extraVlocs[i * 4] = data.getLEUShort(offset + i * 8);
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
                            int curVertex = 0;
                            for (int i = 0; i < numCommand; ++i) {
                                VertexWeight vw = new VertexWeight();
                                vw.startVertex = curVertex;
                                vw.bone1 = data.getUnsignedByte(offset++) / 4;
                                vw.boneWeight1 = data.getUnsignedByte(offset++);
                                vw.bone2 = data.getUnsignedByte(offset++);
                                if (vw.bone2 == 0xFF) {
                                    // Single bone
                                    vw.boneWeight2 = 0;
                                    int count = data.getUnsignedByte(offset++);
                                    curVertex += count;
                                } else {
                                    vw.bone2 /= 4;
                                    vw.boneWeight2 = data.getUnsignedByte(offset++);
                                    ++curVertex;

                                    if (vw.boneWeight1 + vw.boneWeight2 < 0xFF) {
                                        ++i;
                                        vw.bone3 = data.getUnsignedByte(offset++) / 4;
                                        vw.boneWeight3 = data.getUnsignedByte(offset++);
                                        vw.bone4 = data.getUnsignedByte(offset++);
                                        int bw4 = data.getUnsignedByte(offset++);
                                        if (vw.bone4 != 0xFF) {
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
            return HexUtil.formatHexUShort(v1) + ", " + HexUtil.formatHexUShort(v2) + ", " + HexUtil.formatHexUShort(
                    v3);
        }
    }

    private class UV
    {
        public short u;
        public short v;

        public UV(short u, short v)
        {
            this.u = u;
            this.v = v;
        }
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

        public VertexWeight()
        {
        }

        public VertexWeight(final VertexWeight vw)
        {
            startVertex = vw.startVertex;
            endVertex = vw.endVertex;
            bone1 = vw.bone1;
            bone2 = vw.bone2;
            bone3 = vw.bone3;
            bone4 = vw.bone4;
            boneWeight1 = vw.boneWeight1;
            boneWeight2 = vw.boneWeight2;
            boneWeight3 = vw.boneWeight3;
            boneWeight4 = vw.boneWeight4;
        }
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
}

