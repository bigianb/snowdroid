package net.ijbrown.snowdroid;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main extends ApplicationAdapter
{
    public Map<String, ModelDef> modelDefs = new HashMap<String, ModelDef>();
    String rootPath;
    Model model;
    ModelInstance modelInstance;
    ModelBatch modelBatch;
    Camera camera;
    CameraInputController camController;
    Environment environment;

    public Main(String rootPath)
    {
        if (rootPath.endsWith("\\") || rootPath.endsWith("/")) {
            this.rootPath = rootPath;
        } else {
            this.rootPath = rootPath + "/";
        }
        modelDefs.put("barrel", new ModelDef("barrel.lmp", "barrel.vif", "barrel.tex"));
        modelDefs.put("chest", new ModelDef("chest.lmp", "chest_large.vif", "chest_large.tex"));
        modelDefs.put("cratea", new ModelDef("cratea.lmp", "cratea.vif", "cratea.tex"));
        modelDefs.put("crystal", new ModelDef("crystal.lmp", "book.vif", "book.tex"));
        modelDefs.put("icespider", new ModelDef("spider.lmp", "icespider.vif", "icespider.tex"));
        modelDefs.put("kobold", new ModelDef("kobold.lmp", "kobold.vif", "kobold.tex", new String[]{"kobold_idle1.anm"}));
        modelDefs.put("ratgiant", new ModelDef("ratgiant.lmp", "giant_rat_brown.vif", "giant_rat_brown.tex"));
    }

    @Override
    public void create()
    {

        try {
            String dataDir = rootPath + "/BG/DATA/";

            File file = new File(dataDir, "CELLAR1.GOB");
            byte[] gobData = FileUtil.read(file);
            Gob gob = new Gob(gobData);

            ModelDef modelDef = modelDefs.get("kobold");

            ByteBuffer mainLumpData = gob.findEntry(modelDef.lmpName);
            Lump mainLump = new Lump(mainLumpData);

            ByteBuffer texData = mainLump.findEntry(modelDef.texName);
            Pixmap pixmap = new TexReader().read(texData);
            Texture texture = new Texture(pixmap);
            Material material = new Material(TextureAttribute.createDiffuse(texture));

            material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));

            float uscale = 1.0f / pixmap.getWidth();
            float vscale = 1.0f / pixmap.getHeight();

            ByteBuffer vifData = mainLump.findEntry(modelDef.vifName);
            model = new VifReader().readVif(vifData, material, uscale, vscale);

            if (modelDef.animations.size() > 0){
                String anmName = modelDef.animations.get(0);
                AnimData animData = AnmReader.Decode(mainLump.findEntry(anmName));
            }

        } catch (IOException e) {
            e.printStackTrace();
            model = new Model();
        }

        modelInstance = new ModelInstance(model);
        BoundingBox bb = new BoundingBox();
        modelInstance.calculateBoundingBox(bb);

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        //       camera.up.set(0,0,1);       // +ve z is up
        camera.position.set(100, 0, bb.getDimensions().z * 2.0f);
        camera.lookAt(0, 0, bb.getDimensions().z / 2.0f);
        camera.near = 1f;
        camera.far = 3000f;
        camera.update();

        camController = new CameraInputController(camera);
        Gdx.input.setInputProcessor(camController);

        modelBatch = new ModelBatch();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, 1f, -0.8f, -0.2f));
    }

    @Override
    public void render()
    {
        camController.update();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.6f, 0.6f, 0.6f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        modelBatch.begin(camera);
        modelBatch.render(modelInstance, environment);
        modelBatch.end();
    }

    @Override
    public void dispose()
    {
        modelBatch.dispose();
        model.dispose();
    }
}
