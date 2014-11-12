package net.ijbrown.snowdroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines the model parts as stored in a lmp.
 */
public class ModelDef
{
    public String lmpName;
    public String vifName;
    public String texName;
    public List<String> animations;

    public ModelDef(String lmpName, String vifName, String texName)
    {
        this.lmpName = lmpName;
        this.vifName = vifName;
        this.texName = texName;
        animations = new ArrayList<String>();
    }

    public ModelDef(String lmpName, String vifName, String texName, String[] animations)
    {
        this.lmpName = lmpName;
        this.vifName = vifName;
        this.texName = texName;
        this.animations = new ArrayList<String>(animations.length);
        Collections.addAll(this.animations, animations);
    }

}
