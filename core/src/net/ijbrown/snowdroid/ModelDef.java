package net.ijbrown.snowdroid;

/**
 * Defines the model parts as stored in a lmp.
 */
public class ModelDef
{
    public String lmpName;
    public String vifName;
    public String texName;

    public ModelDef(String lmpName, String vifName, String texName)
    {
        this.lmpName = lmpName;
        this.vifName = vifName;
        this.texName = texName;
    }
}
