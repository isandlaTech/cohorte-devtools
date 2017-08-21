package main.java.com.cohorte.models.validator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.cohorte.iot.json.validator.api.CJsonGeneratorFactory;
import org.cohorte.iot.json.validator.api.CJsonSchema;
import org.cohorte.iot.json.validator.api.CJsonValidatorFactory;
import org.cohorte.iot.json.validator.api.IJsonGenerator;
import org.cohorte.iot.json.validator.api.IValidator;
import org.cohorte.utilities.json.provider.CHandlerMemoryCacheSchema;
import org.cohorte.utilities.json.provider.CJsonProvider;
import org.cohorte.utilities.json.provider.CJsonRsrcResolver;
import org.cohorte.utilities.json.provider.IJsonProvider;
import org.psem2m.utilities.CXBytesUtils;
import org.psem2m.utilities.files.CXFileDir;
import org.psem2m.utilities.files.CXFileText;
import org.psem2m.utilities.json.JSONArray;
import org.psem2m.utilities.json.JSONException;
import org.psem2m.utilities.json.JSONObject;
import org.psem2m.utilities.logging.IActivityLogger;
import org.psem2m.utilities.rsrc.CXRsrcProviderFile;
import org.psem2m.utilities.rsrc.CXRsrcProviderMemory;

public class CValidator {

	private static final String DEFINITIONS = "definitions";

	public static String EXTENSION = ".js";
	private static final String ITEMS = "items";

	private static final String SCHEMA = "schema";
	private final String pCohorteBase;
	private final String pCohorteData;
	private final String pIncludeTag;
	private final IJsonGenerator pJsonGenerator;

	private IJsonProvider pJsonProvider;

	private Set<String> pListModule;

	private final IActivityLogger pLogger;
	private CXFileDir pModelBase;

	private CXFileDir pModelData;

	private final String pPathTarget;

	private final String pPrefixFakeJson;

	private final String pPrefixModule;
	private final IValidator pValidator;

	public CValidator(final IActivityLogger aLogger) throws Exception {
		this(aLogger, System.getenv("cohorte.data"), System
				.getenv("cohorte.base"), System.getenv("tag"), System
				.getenv("module.prefix"), System.getenv("target.dir"), System
				.getenv("json.prefix"));
	}

	public CValidator(final IActivityLogger aLogger, final String aCohorteData,
			final String aCohorteBase, final String aIncludeTag,
			final String aPrefixModule, final String aPathTarget,
			final String aPrefixJson) throws Exception {
		if (aCohorteData == null || aCohorteBase == null) {
			throw new Exception("cohorte.base or cohorte.data is not setted!");
		}
		pLogger = aLogger;
		pCohorteData = aCohorteData;
		pCohorteBase = aCohorteBase;
		pIncludeTag = aIncludeTag != null ? aIncludeTag : "$include";
		pPrefixModule = aPrefixModule != null ? aPrefixModule : "module_";
		pPathTarget = aPathTarget != null ? aPathTarget : pCohorteBase
				+ File.separatorChar + "generate";
		pPrefixFakeJson = aPrefixJson != null ? aPrefixJson : "fake_json_";
		pJsonGenerator = CJsonGeneratorFactory.getSingleton();
		pValidator = CJsonValidatorFactory.getSingleton();

		CXFileText wTarget = new CXFileText(pPathTarget);
		wTarget.mkdirs();
		initProvider();
		initModuleList();

	}

	private void initModuleList() {

		Set<String> wModuleNamesData = pModelData.exists() ? Arrays
				.asList(pModelData.listFiles()).stream()
				.filter(wFile -> wFile.isDirectory())
				.map(wFile -> wFile.getName()).collect(Collectors.toSet())
				: null;
		Set<String> wModuleNamesBase = pModelBase.exists() ? Arrays
				.asList(pModelBase.listFiles()).stream()
				.filter(wFile -> wFile.isDirectory())
				.map(wFile -> wFile.getName()).collect(Collectors.toSet())
				: null;

		if (wModuleNamesBase != null) {
			pListModule = wModuleNamesBase;
		}
		if (wModuleNamesData != null) {
			if (pListModule != null) {
				pListModule.addAll(wModuleNamesData);
			} else {
				pListModule = wModuleNamesData;
			}
		}
	}

	private void initProvider() throws Exception {

		CJsonRsrcResolver wResolver = new CJsonRsrcResolver();
		CXRsrcProviderFile wFileDataProv;
		pModelData = new CXFileDir(pCohorteData + File.separatorChar + "models");
		pModelBase = new CXFileDir(pCohorteBase + File.separatorChar + "models");

		if (pModelData.exists() && pModelBase.exists()) {
			pLogger.logSevere(null, "main", "directory %s or %s dosn't exists",
					pModelData.getName(), pModelBase.getName());
			return;
		}

		if (pModelData.isDirectory()) {
			wFileDataProv = new CXRsrcProviderFile(pModelData,
					Charset.forName(CXBytesUtils.ENCODING_UTF_8));
			wResolver.addRsrcProvider(pIncludeTag, wFileDataProv, 1);

		}

		if (pModelBase.exists() && pModelBase.isDirectory()) {
			CXRsrcProviderFile wFileBaseProv = new CXRsrcProviderFile(
					pModelBase, Charset.forName(CXBytesUtils.ENCODING_UTF_8));
			wResolver.addRsrcProvider(pIncludeTag, wFileBaseProv, 2);
		}

		CXRsrcProviderMemory wMemProv = new CXRsrcProviderMemory();

		wResolver.addRsrcProvider(pIncludeTag, wMemProv);

		pJsonProvider = new CJsonProvider(wResolver, pLogger);
		pJsonProvider
				.setInitMemoryCache(new CHandlerMemoryCacheSchema(pLogger));
		pJsonProvider.setIgnoreMissingContent(true);
	}

	public void traceContext() {
		StringBuilder wSB = new StringBuilder();
		wSB.append(String.format("cohorte.data=[%s]\n", pCohorteData));
		wSB.append(String.format("cohorte.base=[%s]\n", pCohorteBase));
		wSB.append(String.format("model.data=[%s]\n", pModelData));
		wSB.append(String.format("model.base=[%s]\n", pModelBase));
		wSB.append("-----------------------\n");
		wSB.append(String.format("tag=%s\n", pIncludeTag));
		wSB.append(String.format("module.prefix=[%s]\n", pPrefixModule));
		wSB.append("-----------------------\n\n");

		wSB.append(String.format("module.list=[%s]\n", pListModule));
		wSB.append("-----------------------\n");
		wSB.append("-----------------------\n\n");

		pLogger.logInfo(this, "traceContext", wSB.toString());
	}

	public void validate() {
		for (String wModuleName : pListModule) {
			pLogger.logInfo(this, "validate", "module %s include validating",
					wModuleName);
			try {
				JSONObject wObj = pJsonProvider.getJSONObject(pIncludeTag,
						wModuleName, pPrefixModule + wModuleName + EXTENSION);
				pLogger.logInfo(this, "validate",
						"===>module %s include validated", wModuleName);

				JSONArray wArr = wObj.optJSONArray(ITEMS);
				for (int i = 0; wArr != null && i < wArr.length(); i++) {
					JSONObject wItem = wArr.getJSONObject(i);
					validateSchema(wModuleName, wItem.optString("id"),
							wItem.getJSONObject(SCHEMA));
				}
			} catch (Exception e) {
				pLogger.logSevere(this, "validate",
						"===>module %s include validation failed ", wModuleName);
			}
		}
	}

	private void validateSchema(final String aModuleName, final String aItemId,
			final JSONObject aSchema) throws Exception {
		pLogger.logInfo(this, "validateSchema", "module %s schema validating",
				aItemId);
		try {
			// remove definition content
			aSchema.remove(DEFINITIONS);
			// get schema
			CJsonSchema wSchema = pValidator.getSchema(pLogger, aSchema);

			// generate fake json with data
			JSONObject wFakeJson = pJsonGenerator.generateFakeJson(pLogger,
					wSchema.getJsonSchema(), false);

			// validation fake json with schema
			pValidator.valdate(pLogger, wSchema,
					new JSONObject(wFakeJson.toString()));

			// write it to the target folder
			writeFakeJson(wFakeJson, aModuleName, aItemId);

		} catch (Exception e) {
			pLogger.logSevere(
					this,
					"validateSchema",
					"ERRO; module  %s, item %s :  schema validation failed  [%s]",
					aModuleName, aItemId, e);
			throw e;
		}
		pLogger.logInfo(this, "validateSchema", "module %s schema validated",
				aItemId);

	}

	private void writeFakeJson(final JSONObject aFakeJson,
			final String aModuleName, final String aItemId) throws IOException,
			JSONException {
		CXFileText wFileJsonFake = new CXFileText(pPathTarget
				+ File.separatorChar + pPrefixFakeJson + aModuleName + "_"
				+ aItemId);
		wFileJsonFake.writeAll(aFakeJson.toString(2));
		wFileJsonFake.close();
	}

}
