package org.cohorte.eclipse.runner.basic;

import java.util.HashMap;
import java.util.Map;

import org.psem2m.utilities.CXDateTime;
import org.psem2m.utilities.json.JSONObject;

/**
 * Contains the json component definition and a link to the factory infos
 *
 * <pre>
 *         	{
 *         		"name": "ASPOSE_CONVERTER",
 *             	"factory": "fr.agilium.services.converter.provider.CAsposeConverter",
 *              "isolate": "webserver"
 *         	},
 *         {
 *             	"name": "AGILIUM_SERVICES_ASPOSE_CLEANER",
 *             	"factory": "Agilium-CCpntDirCleaner-factory",
 *              "isolate": "servermain",
 *         		"properties" : {
 *         			"base.subdir" : "storage/CAsposeConverter/out"
 *         		}
 *         	}
 * </pre>
 *
 * @author ogattaz
 *
 */
public class CComponentInfos {

	// MOD_OG_20150417
	static final String PROP_FACTORY = "factory";
	static final String PROP_ISOLATE = "isolate";
	static final String PROP_NAME = "name";
	// MOD_OG_20160906 component properties
	static final String PROP_PROPERTIES = "properties";

	private long pCreationTimeStanp = -1;
	private final JSONObject pDef;
	private final CFactoryInfos pFactoryInfos;

	private boolean pIsInCurrentIsolate = false;

	/**
	 * @param aDef
	 * @param aFactoryInfos
	 */
	CComponentInfos(final JSONObject aDef, final CFactoryInfos aFactoryInfos) {
		super();
		pDef = aDef;
		pFactoryInfos = aFactoryInfos;
	}

	/**
	 * @return
	 */
	String getCreationTimeStamp() {
		return (isCreated()) ? CXDateTime
				.getIso8601TimeStamp(pCreationTimeStanp) : "n/a";
	}

	/**
	 * @return
	 */
	CFactoryInfos getFactoryInfos() {
		return pFactoryInfos;
	}

	/**
	 * @return
	 */
	String getFactoryName() {
		return getFactoryInfos().getName();
	}

	/**
	 * @return
	 */
	String getIsolateName() {
		return pDef.optString(PROP_ISOLATE);
	}

	/**
	 * @return
	 */
	String getName() {
		return pDef.optString(PROP_NAME);
	}

	/**
	 * MOD_OG_20160906 component properties
	 *
	 * @return the optional json object "properties"
	 */
	JSONObject getProperties() {
		return pDef.optJSONObject(PROP_PROPERTIES);
	}

	/**
	 * MOD_OG_20160906 component properties
	 *
	 * @return the optional json object "properties"as a Map<StringObject>
	 */
	Map<String, Object> getPropertiesMap() {
		Map<String, Object> wMap = new HashMap<String, Object>();
		if (hasProperties()) {
			JSONObject wProps = pDef.optJSONObject(PROP_PROPERTIES);
			for (String wKey : wProps.keySet()) {
				wMap.put(wKey, wProps.opt(wKey));
			}
		}
		return wMap;
	}

	/**
	 * @return
	 */
	boolean hasFactory() {
		return getFactoryInfos().hasFactoryServiceRef();
	}

	/**
	 * MOD_OG_20160906 component properties
	 *
	 * @return true if the optional json object "properties" is present
	 */
	boolean hasProperties() {
		return pDef.has(PROP_PROPERTIES);
	}

	/**
	 * MOD_OG_20150417
	 *
	 * @param aCurrentaIsolateName
	 *            the name of the current isolate
	 * @return true is the isolate property of the component is the same as the
	 *         passed current isolate name or if this property isn't set in the
	 *         composition
	 */
	boolean initIsInCurrentIsolate(final String aCurrentaIsolateName) {

		String wIsolateName = getIsolateName();

		pIsInCurrentIsolate = (wIsolateName == null
				|| wIsolateName.trim().isEmpty() || wIsolateName
				.equalsIgnoreCase(aCurrentaIsolateName));

		return pIsInCurrentIsolate;
	}

	/**
	 * @return
	 */
	boolean isCreated() {
		return pCreationTimeStanp > 0;
	}

	/**
	 * MOD_OG_20150417 create
	 *
	 * @return true if the component must be instanciate in this isolate
	 */
	boolean isInCurrentIsolate() {
		return pIsInCurrentIsolate;
	}

	/**
	 *
	 */
	void setCreated() {
		pCreationTimeStanp = System.currentTimeMillis();
	}
}
