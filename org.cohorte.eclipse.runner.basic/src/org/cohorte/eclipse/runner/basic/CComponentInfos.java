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

	private long pCreationTimeStanp = -1;
	private final JSONObject pDef;
	private final CFactoryInfos pFactoryInfos;

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
		return pDef.optString("isolate");
	}

	/**
	 * @return
	 */
	String getName() {
		return pDef.optString("name");
	}

	/**
	 * @return
	 */
	boolean hasFactory() {
		return getFactoryInfos().hasFactoryServiceRef();
	}

	/**
	 * @return
	 */
	boolean isCreated() {
		return pCreationTimeStanp > 0;
	}

	/**
	 * 
	 */
	void setCreated() {
		pCreationTimeStanp = System.currentTimeMillis();
	}

}
