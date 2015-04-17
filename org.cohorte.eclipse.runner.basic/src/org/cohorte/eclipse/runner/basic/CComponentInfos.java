package org.cohorte.eclipse.runner.basic;

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
	 * @return
	 */
	boolean hasFactory() {
		return getFactoryInfos().hasFactoryServiceRef();
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
