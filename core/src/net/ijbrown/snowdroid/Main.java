package net.ijbrown.snowdroid;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.io.File;
import java.io.IOException;

public class Main extends ApplicationAdapter {

    Model model;
    ModelInstance modelInstance;
    ModelBatch modelBatch;
    Camera camera;

	@Override
	public void create () {

        try {
            String dataDir = "/emu/bgda/BG/DATA/";

            File file = new File(dataDir, "CELLAR1.GOB");
            byte[] gobData = FileUtil.read(file);
            Gob gob = new Gob(gobData);

            ByteBuffer mainLumpData = gob.findEntry("barrel.lmp");
            Lump mainLump = new Lump(mainLumpData);

            Material material = new Material();
            Color red = Color.GREEN;
            material.set(new ColorAttribute(ColorAttribute.Ambient, red));
            material.set(new ColorAttribute(ColorAttribute.Diffuse, red));
            material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));

            ByteBuffer vifData = mainLump.findEntry("barrel.vif");
            model = new VifReader().readVif(vifData, material);
            modelInstance = new ModelInstance(model);
        } catch (IOException e) {
            e.printStackTrace();
        }

        BoundingBox bb = new BoundingBox();
        modelInstance.calculateBoundingBox(bb);

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(bb.getDimensions().x * 2.0f, 0.0f, 0.0f);
        camera.lookAt(0,0,0);
        camera.near = 1f;
        camera.far = 300f;
        camera.update();

        modelBatch = new ModelBatch();
	}

	@Override
	public void render () {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClearColor(0.6f, 0.6f, 0.6f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        modelBatch.begin(camera);
        modelBatch.render(modelInstance);
        modelBatch.end();
	}
}
