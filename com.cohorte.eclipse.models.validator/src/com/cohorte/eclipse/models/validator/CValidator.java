package com.cohorte.eclipse.models.validator;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.cohorte.iot.json.validator.api.CJsonValidator;
import org.cohorte.utilities.json.provider.CHandlerMemoryCacheSchema;
import org.cohorte.utilities.json.provider.CJsonProvider;
import org.cohorte.utilities.json.provider.CJsonRsrcResolver;
import org.psem2m.utilities.CXBytesUtils;
import org.psem2m.utilities.files.CXFileDir;
import org.psem2m.utilities.json.JSONArray;
import org.psem2m.utilities.json.JSONObject;
import org.psem2m.utilities.logging.IActivityLogger;
import org.psem2m.utilities.rsrc.CXRsrcProviderFile;
import org.psem2m.utilities.rsrc.CXRsrcProviderMemory;

public class CValidator {

	public static String EXTENSION = ".js";

	private static final String ITEMS = "items";
	private static final String SCHEMA = "schema";

	private final String pCohorteBase;
	private final String pCohorteData;
	private final String pIncludeTag;
	private CJsonProvider pJsonProvider;
	private Set<String> pListModule;

	private final IActivityLogger pLogger;

	private CXFileDir pModelBase;

	private CXFileDir pModelData;

	private final String pPrefixModule;

	public CValidator(final IActivityLogger aLogger) {
		pLogger = aLogger;
		pCohorteData = System.getenv("cohorte.data");
		pCohorteBase = System.getenv("cohorte.base");
		pIncludeTag = System.getenv("tag") != null ? System.getenv("tag")
				: "$include";
		pPrefixModule = System.getenv("module.prefix") != null ? System
				.getenv("module.prefix") : "module_";
		try {
			initProvider();
			initModuleList();
		} catch (Exception e) {
			pLogger.logSevere(null, "main", "ERROR; exception [%s]", e);
		}
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
		for (String wName : pListModule) {
			pLogger.logInfo(this, "validate", "module %s include validating",
					wName);
			try {
				JSONObject wObj = pJsonProvider.getJSONObject(pIncludeTag,
						wName, pPrefixModule + wName + EXTENSION);
				pLogger.logInfo(this, "validate",
						"===>module %s include validated", wName);

				JSONArray wArr = wObj.optJSONArray(ITEMS);
				for (int i = 0; wArr != null && i < wArr.length(); i++) {
					JSONObject wItem = wArr.getJSONObject(i);
					validateSchema(wItem.optString("id"),
							wItem.getJSONObject(SCHEMA));
				}
			} catch (Exception e) {
				pLogger.logSevere(this, "validate",
						"===>module %s include validation failed ", wName);
			}
		}
	}

	private void validateSchema(final String aItemId, final JSONObject aSchema)
			throws Exception {
		pLogger.logInfo(this, "validateSchema", "module %s schema validating",
				aItemId);
		try {
			CJsonValidator.getDefault().getSchema(pLogger, aSchema);
		} catch (Exception e) {
			pLogger.logSevere(this, "validateSchema",
					"module %s schema validated", aItemId);
			throw e;
		}
		pLogger.logInfo(this, "validateSchema", "module %s schema validated",
				aItemId);

	}
}
