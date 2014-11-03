package net.ijbrown.snowdroid;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores the decoded animation data.
 */
public class AnimData
{
    public int numBones;
    public int numFrames;
    public int offset4Val;
    public int offset14Val;
    public int offset18Val;     // These are 4 bytes which are all ored together

    public int[] skeletonDef;

    public Vector3[] bindingPose;

    public List<AnimMeshPose> meshPoses = new ArrayList<AnimMeshPose>();

    public List<List<AnimMeshPose>> perFramePoses;

    // With forward kinematics applied
    public List<List<AnimMeshPose>> perFrameFKPoses;

    public void BuildPerFrameFKPoses()
    {
        perFrameFKPoses = new ArrayList<List<AnimMeshPose>>(numFrames);
        for (int frame = 0; frame < numFrames; ++frame) {
            List<AnimMeshPose> list = new ArrayList<AnimMeshPose>(skeletonDef.length);
            for (int aSkeletonDef : skeletonDef) {
                list.add(null);
            }
            perFrameFKPoses.add(list);
        }
        Vector3[] parentPoints = new Vector3[64];
        Quaternion[] parentRotations = new Quaternion[64];
        parentPoints[0] = new Vector3(0, 0, 0);
        parentRotations[0] = new Quaternion(0, 0, 0, 1);
        for (int frame = 0; frame < numFrames; ++frame) {
            for (int jointNum = 0; jointNum < skeletonDef.length; ++jointNum) {

                int parentIndex = skeletonDef[jointNum];
                Vector3 parentPos = parentPoints[parentIndex];
                Quaternion parentRot = parentRotations[parentIndex];

                // The world position of the child joint is the local position of the child joint rotated by the
                // world rotation of the parent and then offset by the world position of the parent.
                AnimMeshPose pose = perFramePoses.get(frame).get(jointNum);

                Vector3 thisPos = new Vector3(pose.position);
                parentRot.transform(thisPos);
                thisPos.add(parentPos);

                // The world rotation of the child joint is the world rotation of the parent rotated by the local rotation of the child.
                Quaternion poseRot = pose.rotation;
                poseRot.nor();
                Quaternion thisRot = new Quaternion(parentRot);
                thisRot.mul(poseRot).nor();

                AnimMeshPose fkPose = new AnimMeshPose();
                fkPose.position = thisPos;
                fkPose.rotation = thisRot;
                perFrameFKPoses.get(frame).set(jointNum, fkPose);

                parentPoints[parentIndex + 1] = fkPose.position;
                parentRotations[parentIndex + 1] = fkPose.rotation;
            }
        }
    }

    public void BuildPerFramePoses()
    {
        perFramePoses = new ArrayList<List<AnimMeshPose>>(numFrames);
        for (int frame = 0; frame < numFrames; ++frame) {
            List<AnimMeshPose> list = new ArrayList<AnimMeshPose>(numBones);
            for (int bone = 0; bone < numBones; ++bone) {
                list.add(null);
            }
            perFramePoses.add(list);
        }
        for (AnimMeshPose pose : meshPoses) {
            if (pose != null) {
                perFramePoses.get(pose.frameNum).set(pose.boneNum, pose);
            }
        }
        for (int bone = 0; bone < numBones; ++bone) {
            AnimMeshPose prevPose = null;
            for (int frame = 0; frame < numFrames; ++frame) {
                if (prevPose != null && perFramePoses.get(frame).get(bone) == null) {
                    int frameDiff = frame - prevPose.frameNum;
                    float avCoeff = frameDiff / 131072.0f;
                    Quaternion rotDelta = new Quaternion(prevPose.angularVelocity.x * avCoeff,
                                                         prevPose.angularVelocity.y * avCoeff,
                                                         prevPose.angularVelocity.z * avCoeff,
                                                         prevPose.angularVelocity.w * avCoeff);

                    float velCoeff = frameDiff / 512.0f;
                    Vector3 posDelta = new Vector3(prevPose.velocity.x * velCoeff, prevPose.velocity.y * velCoeff,
                                                   prevPose.velocity.z * velCoeff);

                    AnimMeshPose pose = new AnimMeshPose();
                    pose.boneNum = bone;
                    pose.frameNum = frame;
                    pose.position = new Vector3(prevPose.position.x + posDelta.x, prevPose.position.y + posDelta.y,
                                                prevPose.position.z + posDelta.z);
                    pose.rotation = new Quaternion(prevPose.rotation.x + rotDelta.x, prevPose.rotation.y + rotDelta.y,
                                                   prevPose.rotation.z + rotDelta.z, prevPose.rotation.w + rotDelta.w);
                    pose.angularVelocity = prevPose.angularVelocity;
                    pose.velocity = prevPose.velocity;
                    perFramePoses.get(frame).set(bone, pose);
                }
                prevPose = perFramePoses.get(frame).get(bone);
            }
        }
    }

    public static class AnimMeshPose
    {
        public Vector3 position;
        public Quaternion rotation;

        public Quaternion angularVelocity;
        public Vector3 velocity;

        public int boneNum;
        public int frameNum;

        public AnimMeshPose()
        {
        }

        public AnimMeshPose(AnimMeshPose copyFrom)
        {
            position = copyFrom.position;
            rotation = copyFrom.rotation;
            angularVelocity = copyFrom.angularVelocity;
            velocity = copyFrom.velocity;
            boneNum = copyFrom.boneNum;
            frameNum = copyFrom.frameNum;
        }
    }
}
