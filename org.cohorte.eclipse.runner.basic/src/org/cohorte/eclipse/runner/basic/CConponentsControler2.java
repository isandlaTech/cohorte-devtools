package org.cohorte.eclipse.runner.basic;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.felix.ipojo.ConfigurationException;
import org.apache.felix.ipojo.Factory;
import org.apache.felix.ipojo.MissingHandlerException;
import org.apache.felix.ipojo.UnacceptableConfiguration;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.apache.felix.ipojo.architecture.PropertyDescription;
import org.cohorte.composer.api.IIsolateComposer;
import org.cohorte.composer.api.RawComponent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.isolates.services.dirs.IPlatformDirsSvc;
import org.psem2m.utilities.CXStringUtils;
import org.psem2m.utilities.files.CXFileDir;
import org.psem2m.utilities.files.CXFileUtf8;
import org.psem2m.utilities.json.JSONArray;
import org.psem2m.utilities.json.JSONException;
import org.psem2m.utilities.json.JSONObject;

/**
 * This components simulates the node contôler, it instanciates all the
 * components defined in the composition file
 *
 * <ul>
 * <li>it retrieves the file "base/conf/composition.js"</li>
 * <li>it retrieves the components factories class name listed in this file</li>
 * <li>it waits for the availability of all the factories</li>
 * <li>it instanciates all the compoenents defined in the composition file</li>
 * </ul>
 *
 * sample
 *
 * <pre>
 * {
 *     "name": "AgiliumServices",
 *     "root": {
 *         "name": "AgiliumServices-composition",
 *         "components": [
 *         	{
 *         		"name": "ASPOSE_CONVERTER",
 *             	"factory": "fr.agilium.services.converter.provider.CAsposeConverter",
 *                 "isolate": "webserver"
 *         	},
 *         ...
 *         ]
 *     }
 * }
 * </pre>
 *
 * The available factories :
 *
 * <pre>
 * g! factories
 * ...
 * Factory fr.agilium.services.converter.provider.CAsposeConverter (VALID)
 * Factory fr.agilium.services.converter.provider.CAsposeLicenceManager (VALID)
 * Factory fr.agilium.services.main.CAgiliumServicesInfo (VALID)
 * Factory fr.agilium.services.rest.server.CRestServer (VALID)
 * ...
 * </pre>
 *
 * The details of the factory "CAsposeConverter"
 *
 * <pre>
 * g! factory fr.agilium.services.converter.provider.CAsposeConverter
 * factory name="fr.agilium.services.converter.provider.CAsposeConverter" bundle="51" state="valid" implementation-class="fr.agilium.services.converter.provider.CAsposeConverter"
 * 	requiredhandlers list="[org.apache.felix.ipojo:requires, org.apache.felix.ipojo:callback, org.apache.felix.ipojo:provides, org.apache.felix.ipojo:architecture]"
 * 	missinghandlers list="[]"
 * 	provides specification="fr.agilium.services.converter.IConverter"
 * 	inherited interfaces="[fr.agilium.services.converter.IConverter]" superclasses="[]"
 * </pre>
 *
 * @author ogattaz
 *
 */
@Component
// @Instantiate
public class CConponentsControler2 {

	private final BundleContext pBundleContext;

	// the map compnonent name => component infos
	private final Map<String, CComponentInfos> pComponentInfos = new HashMap<String, CComponentInfos>();

	private CXFileUtf8 pCompositionFile = null;

	// the map factory name => factory infos
	private final Map<String, CFactoryInfos> pFactoriesInfos = new HashMap<String, CFactoryInfos>();

	@Requires
	private IIsolateComposer pIsolateComposer;

	@Requires
	private IIsolateLoggerSvc pLogger;

	@Requires
	private IPlatformDirsSvc pPlatformDirsSvc;

	/**
	 * @param aBundleContext
	 */
	public CConponentsControler2(final BundleContext aBundleContext) {
		super();
		pBundleContext = aBundleContext;
	}

	/**
	 * retreive the "components" json array in the composition definition.
	 *
	 * <pre>
	 * {
	 *     "name": "...",
	 *     "root": {
	 *         "name": "...",
	 *         "components": [
	 *         	{
	 *         		...
	 *         	},
	 *         ...
	 *         ]
	 *     }
	 * }
	 * </pre>
	 *
	 *
	 * @param aCompositionDef
	 * @return
	 * @throws JSONException
	 */
	private JSONArray getComponentDefs(final JSONObject aCompositionDef)
			throws JSONException {

		return aCompositionDef.getJSONObject("root").getJSONArray("components");
	}

	/**
	 * Convert the content of the "base/conf/composituion.js" file in a json
	 * object.
	 *
	 * @param aCompositionFile
	 *            The "base/conf/composituion.js" file
	 * @return A json object instance
	 * @throws IOException
	 * @throws JSONException
	 */
	private JSONObject getCompositionDef(final CXFileUtf8 aCompositionFile)
			throws JSONException, IOException {

		return new JSONObject(aCompositionFile.readAll());
	}

	/**
	 * @return an instance of CXFileUtf8 corresponding to the existing
	 *         "base/conf/composituion.js" file
	 *
	 * @throws IOException
	 */
	private CXFileUtf8 getCompositionFile() throws IOException {

		CXFileDir wConfDir = new CXFileDir(pPlatformDirsSvc.getPlatformBase(),
				"conf");
		if (!wConfDir.exists()) {
			throw new IOException(String.format(
					"The cohorte 'conf' directory [%s] doesn't exist",
					wConfDir.getAbsolutePath()));
		}

		CXFileUtf8 wCompositionFile = new CXFileUtf8(wConfDir, "composition.js");
		if (!wCompositionFile.exists()) {
			throw new IOException(String.format(
					"The cohorte 'composition' file [%s] doesn't exist",
					wCompositionFile.getAbsolutePath()));
		}
		return wCompositionFile;
	}

	/**
	 * @return
	 */
	boolean hasCompositionFile() {
		return pCompositionFile != null;
	}

	/**
	 * initialize the content of the "pFactoriesInfos" and "pComponentInfos"
	 * maps.
	 *
	 * MOD_OG_20150417 Manage explicitly the component flag "isInCurrentIsolate"
	 * and the factory flag "isNeeded"
	 *
	 * @throws JSONException
	 * @throws IOException
	 */
	private void initMaps() throws JSONException, IOException {

		String wCurrentIsolateName = pPlatformDirsSvc.getIsolateName();

		pLogger.logInfo(this, "initMaps", "CurrentIsolateName={%s]",
				wCurrentIsolateName);

		JSONArray wComponentDefArray = getComponentDefs(getCompositionDef(pCompositionFile));

		for (int wIdx = 0; wIdx < wComponentDefArray.length(); wIdx++) {

			JSONObject wDef = wComponentDefArray.getJSONObject(wIdx);
			String wfactoryName = wDef.getString(CComponentInfos.PROP_FACTORY);

			CFactoryInfos wFactoryInfos = pFactoriesInfos.get(wfactoryName);
			if (wFactoryInfos == null) {
				wFactoryInfos = new CFactoryInfos(wfactoryName);
				pFactoriesInfos.put(wfactoryName, wFactoryInfos);
			}
			CComponentInfos wComponentInfo = new CComponentInfos(wDef,
					wFactoryInfos);

			// MOD_OG_20150417
			boolean wInCurrentIsolate = wComponentInfo
					.initIsInCurrentIsolate(wCurrentIsolateName);

			// MOD_OG_20150417
			wFactoryInfos.setNeeded(wInCurrentIsolate);

			pLogger.logInfo(this, "initMaps", "Factory=[%60s] setNeeded=[%s]",
					wFactoryInfos.getName(), wInCurrentIsolate);

			pComponentInfos.put(wComponentInfo.getName(), wComponentInfo);
		}
	}

	/**
	 * @throws ConfigurationException
	 * @throws MissingHandlerException
	 * @throws UnacceptableConfiguration
	 *
	 */
	private void instancaiateComponents() throws UnacceptableConfiguration,
			MissingHandlerException, ConfigurationException {

		String wCurrentIsolateName = pPlatformDirsSvc.getIsolateName();

		pLogger.logInfo(this, "instancaiateComponents",
				"CurrentIsolateName=[%s]", wCurrentIsolateName);

		Set<RawComponent> aCpts = new LinkedHashSet<RawComponent>();

		for (CComponentInfos wComponentInfos : pComponentInfos.values()) {

			// component is instantiated in this local isolate only if it
			// has no isolate defined in composition.js file or if the
			// defined isolate name match with this local isolate's name.
			// MOD_OG_20150417 use the explicit flag
			if (wComponentInfos.isInCurrentIsolate()) {
				pLogger.logInfo(
						this,
						"instancaiateComponents",
						"Component [%s] will be instantiated in this local Isolate => [%s]",
						wComponentInfos.getName(),
						wComponentInfos.getIsolateName());
				// if (!wComponentInfos.isCreated()) {

				// MOD_BD_20150629 using of Cohorte's Isolate Composer
				// to instantiate components
				RawComponent aCpt = new RawComponent(
						wComponentInfos.getFactoryName(),
						wComponentInfos.getName());
				aCpts.add(aCpt);

				// wComponentInfos.setCreated();

				/*
				 * pLogger.logInfo(this, "instancaiateComponents",
				 * "Name=[%s] TimeStamp=[%s] component=[%s]",
				 * wComponentInfos.getName(),
				 * wComponesntInfos.getCreationTimeStamp(), null);
				 */
				// }
			} else {

				pLogger.logInfo(this, "instancaiateComponents",
						"Component [%s] explicitly in another Isolate => [%s]",
						wComponentInfos.getName(),
						wComponentInfos.getIsolateName());
			}

		}
		// order the isolate composer to instantiate the components.
		pLogger.logInfo(this, "instancaiateComponents",
				"Ordering Isolate Composer to instantiate [%s] components",
				aCpts.size());
		pIsolateComposer.instantiate(aCpts);

		logControlerState();
	}

	/**
	 *
	 */
	@Invalidate
	public void invalidate() {

		pComponentInfos.clear();
		pFactoriesInfos.clear();

		pLogger.logInfo(this, "invalidate", "invalidated");
	}

	/**
	 *
	 */
	private void logControlerState() {
		logControlerState(true);
	}

	/**
	 * <pre>
	 * #################################################################################
	 * #
	 * # [org.cohorte.eclipse.runner.basic.CConponentsControler] in action [false]
	 * #
	 * # UNABLE TO INSTANCIATE THE COMPONENTS OF THE COMPOSITION [/Users/ogattaz/workspaces/AgiliumServices_git/agilium-services-base/conf/composition.js]
	 * #
	 * #################################################################################
	 * </pre>
	 *
	 * <pre>
	 * #################################################################################
	 * #
	 * # [org.cohorte.eclipse.runner.basic.CConponentsControler] in action [true]
	 * #
	 * # ## Component=[ASPOSE_LICENCE_MANAGER]
	 * #    -           Created:[ true] TimeStamp=[2015-03-06T15:16:17.0000441+0100]
	 * #    -      Factory.Name:[fr.agilium.services.converter.provider.CAsposeLicenceManager]
	 * #    - Factory.available:[true]
	 * #    -  Factory.instance:[org.apache.felix.ipojo.ComponentFactory@621316d0]
	 * # ## Component=[AGILIUM_SERVICES_MAIN]
	 * #    -           Created:[ true] TimeStamp=[2015-03-06T15:16:17.0000447+0100]
	 * #    -      Factory.Name:[fr.agilium.services.main.CAgiliumServicesInfo]
	 * #    - Factory.available:[true]
	 * #    -  Factory.instance:[org.apache.felix.ipojo.ComponentFactory@2b7a6005]
	 * # ## Component=[REST_SERVER]
	 * #    -           Created:[ true] TimeStamp=[2015-03-06T15:16:17.0000463+0100]
	 * #    -      Factory.Name:[fr.agilium.services.rest.server.CRestServer]
	 * #    - Factory.available:[true]
	 * #    -  Factory.instance:[org.apache.felix.ipojo.ComponentFactory@4ce6049b]
	 * # ## Component=[ASPOSE_CONVERTER]
	 * #    -           Created:[ true] TimeStamp=[2015-03-06T15:16:17.0000595+0100]
	 * #    -      Factory.Name:[fr.agilium.services.converter.provider.CAsposeConverter]
	 * #    - Factory.available:[true]
	 * #    -  Factory.instance:[org.apache.felix.ipojo.ComponentFactory@66cb47ac]
	 * #
	 * #################################################################################
	 * </pre>
	 *
	 * @param aInAction
	 */
	private void logControlerState(final boolean aInAction) {

		StringBuilder wSB = new StringBuilder();
		wSB.append(String.format("\n#%s", CXStringUtils.strFromChar('#', 80)));
		wSB.append("\n#");
		wSB.append(String.format("\n# [%s] in action [%s]", getClass()
				.getName(), aInAction));
		wSB.append("\n#");

		if (!aInAction) {
			wSB.append(String
					.format("\n# UNABLE TO INSTANCIATE THE COMPONENTS OF THE COMPOSITION [%s]",
							pCompositionFile));
		} else {
			for (CComponentInfos wComponentInfos : pComponentInfos.values()) {
				CFactoryInfos wFactoryInfos = wComponentInfos.getFactoryInfos();

				wSB.append(String.format("\n# ## Component=[%s]",
						wComponentInfos.getName()));
				wSB.append(String.format("\n#    -isInCurrentIsolate=[%5s]",
						wComponentInfos.isInCurrentIsolate()));
				wSB.append(String.format(
						"\n#    -           Created:[%5s] TimeStamp=[%s]",
						wComponentInfos.isCreated(),
						wComponentInfos.getCreationTimeStamp()));
				wSB.append(String.format("\n#    -      Factory.Name=[%s]",
						wFactoryInfos.getName()));
				wSB.append(String.format("\n#    - Factory.available=[%5s]",
						wFactoryInfos.hasFactoryServiceRef()));
				wSB.append(String.format("\n#    -  Factory.instance=[%s]",
						wFactoryInfos.getFactoryService()));
			}
		}
		wSB.append("\n#");
		wSB.append(String.format("\n#%s", CXStringUtils.strFromChar('#', 80)));

		for (String wLine : wSB.toString().split("\n")) {
			pLogger.logInfo(this, "logControlerState", wLine);
		}
	}

	/**
	 *
	 * <pre>
	 *  org.apache.felix.framework.ServiceRegistrationImpl$ServiceReferenceImpl_481588007
	 *   -                          component.class=[fr.agilium.services.converter.provider.CAsposeConverter]
	 *   -                    component.description=[factory name="fr.agilium.services.converter.provider.CAsposeConverter"
	 *                                               bundle="51"
	 *                                               state="valid"
	 *                                               implementation-class="fr.agilium.services.converter.provider.CAsposeConverter"
	 *                                               requiredhandlers list="[org.apache.felix.ipojo:requires,
	 *                                                                       org.apache.felix.ipojo:callback,
	 *                                                                       org.apache.felix.ipojo:provides,
	 *                                                                       org.apache.felix.ipojo:architecture]"
	 *                                               missinghandlers list="[]"
	 *                                               provides specification="fr.agilium.services.converter.IConverter"
	 *                                               inherited interfaces="[fr.agilium.services.converter.IConverter]"
	 *                                               superclasses="[]"]
	 *   -                     component.properties=[]
	 *   -  component.providedServiceSpecifications=[fr.agilium.services.converter.IConverter]
	 *   -                             factory.name=[fr.agilium.services.converter.provider.CAsposeConverter]
	 *   -                            factory.state=[1]
	 *   -                              objectClass=[org.apache.felix.ipojo.Factory]
	 *   -                               service.id=[192]
	 *   -                              service.pid=[fr.agilium.services.converter.provider.CAsposeConverter]
	 * </pre>
	 *
	 * @param wfactorySRef
	 */
	private void logFactoryServiceRef(
			final ServiceReference<Factory> wfactorySRef) {

		String[] wPropertyKeys = wfactorySRef.getPropertyKeys();

		pLogger.logInfo(this, "logFactoryServiceRef", "%s_%s", wfactorySRef
				.getClass().getSimpleName(), wfactorySRef.hashCode(),
				wPropertyKeys.length);
		int wIdx = 0;
		for (String wKey : wPropertyKeys) {
			String wStrValue = null;
			Object wObj = wfactorySRef.getProperty(wKey);
			if (wObj != null) {
				if (wObj instanceof String) {
					wStrValue = (String) wObj;
				} else
				//
				if (wObj instanceof String[]) {
					wStrValue = CXStringUtils
							.stringTableToString((String[]) wObj);
				} else
				//
				if (wObj instanceof PropertyDescription[]) {
					StringBuilder wSB = new StringBuilder();
					for (PropertyDescription wPropertyDescription : ((PropertyDescription[]) wObj)) {
						wSB.append(String.format("%s=\"%s\" ",
								wPropertyDescription.getName(),
								wPropertyDescription.getCurrentValue()));
					}
					wStrValue = wSB.toString();
				} else
				//
				{
					wStrValue = String.valueOf(wObj);
				}
				if (wStrValue.indexOf('\n') > 0) {
					wStrValue = wStrValue.replace('\n', '§');
				}
			}
			pLogger.logInfo(this, "logFactoryServiceRef", " | %2d) %40s=[%s]",
					wIdx, wKey, wStrValue);
			wIdx++;
		}
	}

	/**
	 *
	 */
	@Validate
	public void validate() {

		try {
			// retreive the composition file
			pCompositionFile = getCompositionFile();

			pLogger.logSevere(this, "validate", "CompositionFile=[%s]",
					pCompositionFile);

			// itialize the component info map
			initMaps();

			instancaiateComponents();

		} catch (Exception e) {
			pLogger.logSevere(this, "validate", "Error: %s", e);
		}

		pLogger.logInfo(this, "validate", "validated.  CompositionFile=[%s]",
				pCompositionFile);

	}
}
