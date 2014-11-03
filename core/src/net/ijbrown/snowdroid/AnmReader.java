package net.ijbrown.snowdroid;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Class for reading an animation file.
 */
public class AnmReader
{
    public static AnimData Decode(byte[] data, int startOffset, int length)
    {
        int endIndex = startOffset + length;
        AnimData animData = new AnimData();
        animData.numBones = DataUtil.getLEInt(data, startOffset);
        animData.offset4Val = DataUtil.getLEInt(data, startOffset + 4);
        animData.offset14Val = DataUtil.getLEInt(data, startOffset + 0x14);
        animData.offset18Val = DataUtil.getLEInt(data, startOffset + 0x18);
        int offset8Val = startOffset + DataUtil.getLEInt(data, startOffset + 8);

        int bindingPoseOffset = startOffset + DataUtil.getLEInt(data, startOffset + 0x0C);
        animData.bindingPose = new Vector3[animData.numBones];
        for (int i = 0; i < animData.numBones; ++i) {
            animData.bindingPose[i] = new Vector3(
                    -DataUtil.getLEShort(data, bindingPoseOffset + i * 8 + 0) / 64.0f,
                    -DataUtil.getLEShort(data, bindingPoseOffset + i * 8 + 2) / 64.0f,
                    -DataUtil.getLEShort(data, bindingPoseOffset + i * 8 + 4) / 64.0f
            );
        }

        // Skeleton structure
        int offset10Val = startOffset + DataUtil.getLEInt(data, startOffset + 0x10);
        animData.skeletonDef = new int[animData.numBones];
        for (int i = 0; i < animData.numBones; ++i) {
            animData.skeletonDef[i] = data[offset10Val + i];
        }

        AnimData.AnimMeshPose[] curPose = new AnimData.AnimMeshPose[animData.numBones];

        AnimData.AnimMeshPose pose = null;
        for (int boneNum = 0; boneNum < animData.numBones; ++boneNum) {
            pose = new AnimData.AnimMeshPose();
            pose.boneNum = boneNum;
            pose.frameNum = 0;
            int frameOff = offset8Val + boneNum * 0x0e;

            pose.position = new Vector3(
                    DataUtil.getLEShort(data, frameOff) / 64.0f,
                    DataUtil.getLEShort(data, frameOff + 2) / 64.0f,
                    DataUtil.getLEShort(data, frameOff + 4) / 64.0f);

            float a = DataUtil.getLEShort(data, frameOff + 6) / 4096.0f;
            float b = DataUtil.getLEShort(data, frameOff + 8) / 4096.0f;
            float c = DataUtil.getLEShort(data, frameOff + 0x0A) / 4096.0f;
            float d = DataUtil.getLEShort(data, frameOff + 0x0C) / 4096.0f;

            pose.rotation = new Quaternion(b, c, d, a);

            pose.velocity = new Vector3(0, 0, 0);
            pose.angularVelocity = new Quaternion(0, 0, 0, 0);

            // This may give us duplicate frame zero poses, but that's ok.
            animData.meshPoses.add(pose);
            curPose[boneNum] = new AnimData.AnimMeshPose(pose);
        }
        int[] curAngVelFrame = new int[animData.numBones];
        int[] curVelFrame = new int[animData.numBones];

        animData.numFrames = 1;

        int totalFrame = 0;
        int otherOff = offset8Val + animData.numBones * 0x0e;

        pose = null;
        while (otherOff < endIndex) {
            int count = data[otherOff++];
            byte byte2 = data[otherOff++];
            int boneNum = byte2 & 0x3f;
            if (boneNum == 0x3f) break;

            totalFrame += count;

            if (pose == null || pose.frameNum != totalFrame || pose.boneNum != boneNum) {
                if (pose != null) {
                    animData.meshPoses.add(pose);
                }
                pose = new AnimData.AnimMeshPose();
                pose.frameNum = totalFrame;
                pose.boneNum = boneNum;
                pose.position = curPose[boneNum].position;
                pose.rotation = curPose[boneNum].rotation;
                pose.angularVelocity = curPose[boneNum].angularVelocity;
                pose.velocity = curPose[boneNum].velocity;
            }

            // bit 7 specifies whether to read 4 (set) or 3 elements following
            // bit 6 specifies whether they are shorts or bytes (set).
            if ((byte2 & 0x80) == 0x80) {
                int a, b, c, d;
                if ((byte2 & 0x40) == 0x40) {
                    a = data[otherOff++];
                    b = data[otherOff++];
                    c = data[otherOff++];
                    d = data[otherOff++];
                } else {
                    a = DataUtil.getLEShort(data, otherOff);
                    b = DataUtil.getLEShort(data, otherOff + 2);
                    c = DataUtil.getLEShort(data, otherOff + 4);
                    d = DataUtil.getLEShort(data, otherOff + 6);
                    otherOff += 8;
                }
                Quaternion angVel = new Quaternion(b, c, d, a);

                Quaternion prevAngVel = pose.angularVelocity;
                float coeff = (totalFrame - curAngVelFrame[boneNum]) / 131072.0f;
                Quaternion angDelta = new Quaternion(prevAngVel.x * coeff, prevAngVel.y * coeff, prevAngVel.z * coeff,
                                                     prevAngVel.w * coeff);
                pose.rotation = new Quaternion(pose.rotation.x + angDelta.x, pose.rotation.y + angDelta.y,
                                               pose.rotation.z + angDelta.z, pose.rotation.w + angDelta.w);

                pose.frameNum = totalFrame;
                pose.angularVelocity = angVel;

                curPose[boneNum].rotation = pose.rotation;
                curPose[boneNum].angularVelocity = pose.angularVelocity;
                curAngVelFrame[boneNum] = totalFrame;
            } else {
                int x, y, z;
                if ((byte2 & 0x40) == 0x40) {
                    x = data[otherOff++];
                    y = data[otherOff++];
                    z = data[otherOff++];
                } else {
                    x = DataUtil.getLEShort(data, otherOff);
                    y = DataUtil.getLEShort(data, otherOff + 2);
                    z = DataUtil.getLEShort(data, otherOff + 4);
                    otherOff += 6;
                }
                Vector3 vel = new Vector3(x, y, z);
                Vector3 prevVel = pose.velocity;
                float coeff = (totalFrame - curVelFrame[boneNum]) / 512.0f;
                Vector3 posDelta = new Vector3(prevVel.x * coeff, prevVel.y * coeff, prevVel.z * coeff);
                pose.position = new Vector3(pose.position.x + posDelta.x, pose.position.y + posDelta.y,
                                            pose.position.z + posDelta.z);
                pose.frameNum = totalFrame;
                pose.velocity = vel;

                curPose[boneNum].position = pose.position;
                curPose[boneNum].velocity = pose.velocity;
                curVelFrame[boneNum] = totalFrame;
            }
        }
        animData.meshPoses.add(pose);
        animData.numFrames = totalFrame + 1;
        animData.BuildPerFramePoses();
        animData.BuildPerFrameFKPoses();
        return animData;
    }
}

