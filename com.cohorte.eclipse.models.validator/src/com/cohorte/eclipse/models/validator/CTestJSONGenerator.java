package com.cohorte.eclipse.models.validator;

import io.apptik.json.JsonElement;
import io.apptik.json.JsonObject;
import io.apptik.json.generator.JsonGenerator;
import io.apptik.json.generator.JsonGeneratorConfig;
import io.apptik.json.schema.SchemaV4;

import java.io.File;
import java.util.ArrayList;

import org.psem2m.utilities.files.CXFileText;

public class CTestJSONGenerator {

	public static String file = System.getProperty("user.dir")
			+ File.separatorChar + "files" + File.separatorChar
			+ "testSchema.js";

	public static void main(final String[] aArgs) {
		CXFileText wFile = new CXFileText(file);
		try {
			String schema = wFile.readAll();
			SchemaV4 wSchema = new SchemaV4().wrap(JsonElement.readFrom(schema)
					.asJsonObject());

			JsonGeneratorConfig gConf = new JsonGeneratorConfig();
			ArrayList<String> images = new ArrayList<String>();
			images.add("/photos/image.jpg");
			images.add("/photos/image.jpg");

			gConf.uriPaths.put("seven", images);
			gConf.globalUriPaths = images;
			JsonObject job = new JsonGenerator(wSchema, gConf).generate()
					.asJsonObject();

			System.out.println(job.toString());

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
