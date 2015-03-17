package org.cohorte.eclipse.runner.basic;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.ConfigurationException;
import org.apache.felix.ipojo.Factory;
import org.apache.felix.ipojo.MissingHandlerException;
import org.apache.felix.ipojo.UnacceptableConfiguration;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.apache.felix.ipojo.architecture.PropertyDescription;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
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
@Instantiate
public class CConponentsControler implements ServiceListener {

	static final String PROP_FACTORY_NAME = "factory.name";

	private final BundleContext pBundleContext;

	// the map compnonent name => component infos
	private final Map<String, CComponentInfos> pComponentInfos = new HashMap<String, CComponentInfos>();

	private CXFileUtf8 pCompositionFile = null;

	// the map factory name => factory infos
	private final Map<String, CFactoryInfos> pFactoriesInfos = new HashMap<String, CFactoryInfos>();

	@Requires
	private IIsolateLoggerSvc pLogger;

	@Requires
	private IPlatformDirsSvc pPlatformDirsSvc;

	/**
	 * @param aBundleContext
	 */
	public CConponentsControler(final BundleContext aBundleContext) {
		super();
		pBundleContext = aBundleContext;
	}

	/**
	 * @return the collection af all the Factory services available in the
	 *         service registry.
	 * @throws InvalidSyntaxException
	 */
	private Collection<ServiceReference<Factory>> getAllFactoryServiceRefs()
			throws InvalidSyntaxException {

		return pBundleContext.getServiceReferences(Factory.class, null);
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
	 * @throws InvalidSyntaxException
	 */
	private Collection<ServiceReference<Factory>> getFilteredFactoryServiceRefs()
			throws InvalidSyntaxException {

		String wLdapFilter = null;

		String wFilter = System.getProperty(
				"org.cohorte.eclipse.runner.basic.service.filter", null);

		if (wFilter != null && !wFilter.isEmpty()) {
			// @see
			// http://www.ldapexplorer.com/en/manual/109010000-ldap-filter-syntax.htm

			wLdapFilter = String.format("(%s=fr.agilium*)", PROP_FACTORY_NAME);
		}
		return pBundleContext.getServiceReferences(Factory.class, wLdapFilter);

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
	 *
	 *
	 * @throws JSONException
	 * @throws IOException
	 */
	private void initMaps() throws JSONException, IOException {

		JSONArray wComponentDefArray = getComponentDefs(getCompositionDef(pCompositionFile));
		for (int wIdx = 0; wIdx < wComponentDefArray.length(); wIdx++) {
			JSONObject wDef = wComponentDefArray.getJSONObject(wIdx);
			String wfactoryName = wDef.getString("factory");

			CFactoryInfos wFactoryInfos = pFactoriesInfos.get(wfactoryName);
			if (wFactoryInfos == null) {
				wFactoryInfos = new CFactoryInfos(wfactoryName);
				pFactoriesInfos.put(wfactoryName, wFactoryInfos);
			}
			pComponentInfos.put(wDef.getString("name"), new CComponentInfos(
					wDef, wFactoryInfos));
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

		for (CComponentInfos wComponentInfos : pComponentInfos.values()) {

			synchronized (wComponentInfos) {
				// component is instantiated in this local isolate only if it
				// has no isolate defined in composition.js file or if the
				// defined isolate name match with this local isolate's name.
				final String wCmpIsolateName = wComponentInfos.getIsolateName();
				if (wCmpIsolateName == null
						|| wCmpIsolateName.trim().equalsIgnoreCase("")
						|| wCmpIsolateName.equalsIgnoreCase(pPlatformDirsSvc
								.getIsolateName())) {
					if (!wComponentInfos.isCreated()) {

						Properties wComponentProps = new Properties();
						wComponentProps.put("instance.name",
								wComponentInfos.getName());

						ComponentInstance wComponentInstance = wComponentInfos
								.getFactoryInfos().getFactory()
								.createComponentInstance(wComponentProps);

						wComponentInfos.setCreated();

						pLogger.logInfo(this, "instancaiateComponents",
								"Name=[%s] TimeStamp=[%s] component=[%s]",
								wComponentInfos.getName(),
								wComponentInfos.getCreationTimeStamp(),
								wComponentInstance);
					}
				}
			}
		}

		logControlerState();
	}

	/**
	 *
	 */
	@Invalidate
	public void invalidate() {

		unregisterFactoryServiceListener();

		pComponentInfos.clear();
		pFactoriesInfos.clear();

		pLogger.logInfo(this, "invalidate", "invalidated");
	}

	/**
	 * @return
	 */
	private boolean isAllFactoriesAvailable() {
		for (CFactoryInfos wDef : pFactoriesInfos.values()) {
			if (!wDef.hasFactoryServiceRef()) {
				return false;
			}
		}
		return true;
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
			for (CComponentInfos wComponentDef : pComponentInfos.values()) {
				CFactoryInfos wFactoryInfos = wComponentDef.getFactoryInfos();

				wSB.append(String.format("\n# ## Component=[%s]",
						wComponentDef.getName()));
				wSB.append(String.format(
						"\n#    -           Created:[%5s] TimeStamp=[%s]",
						wComponentDef.isCreated(),
						wComponentDef.getCreationTimeStamp()));
				wSB.append(String.format("\n#    -      Factory.Name:[%s]",
						wFactoryInfos.getName()));
				wSB.append(String.format("\n#    - Factory.available:[%s]",
						wFactoryInfos.hasFactoryServiceRef()));
				wSB.append(String.format("\n#    -  Factory.instance:[%s]",
						wFactoryInfos.getFactory()));
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

		pLogger.logInfo(this, "logFactoryServiceRef", "%s_%s", wfactorySRef
				.getClass().getSimpleName(), wfactorySRef.hashCode());
		int wIdx = 0;
		for (String wKey : wfactorySRef.getPropertyKeys()) {
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
	 * <pre>
	 *   -    factory.name=[fr.agilium.services.converter.provider.CAsposeConverter]
	 *   -   factory.state=[1]
	 * </pre>
	 *
	 * @throws InvalidSyntaxException
	 */
	private void logFactoryServiceRefs() throws InvalidSyntaxException {

		for (ServiceReference<Factory> wfactorySRef : getFilteredFactoryServiceRefs()) {
			logFactoryServiceRef(wfactorySRef);
		}
	}

	/**
	 * @throws InvalidSyntaxException
	 *
	 */
	private void registerFactoryServiceListener() throws InvalidSyntaxException {

		String wFilter = "(objectclass=" + Factory.class.getName() + ")";
		pBundleContext.addServiceListener(this, wFilter);

		pLogger.logInfo(this, "registerFactoryServiceListener",
				"Registered=[%b] FactoryServiceListener=[%s]", true, this);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.osgi.framework.ServiceListener#serviceChanged(org.osgi.framework.
	 * ServiceEvent)
	 */
	@Override
	public void serviceChanged(final ServiceEvent aServiceEvent) {

		try {
			@SuppressWarnings("unchecked")
			ServiceReference<Factory> wFactoryServiceRef = (ServiceReference<Factory>) aServiceEvent
					.getServiceReference();

			switch (aServiceEvent.getType()) {
			case ServiceEvent.REGISTERED: {
				setFactoryServiceRefAvaibility(wFactoryServiceRef,
						ServiceEvent.REGISTERED);
				break;
			}
			case ServiceEvent.UNREGISTERING: {
				setFactoryServiceRefAvaibility(wFactoryServiceRef,
						ServiceEvent.UNREGISTERING);
				break;
			}
			}

			if (isAllFactoriesAvailable()) {
				instancaiateComponents();
			}

		} catch (Exception e) {
			pLogger.logSevere(this, "serviceChanged", "Error: %s", e);
		}
	}

	/**
	 * @param wFactoryServiceRef
	 * @param aServiceEvent
	 * @throws Exception
	 */
	private void setFactoryServiceRefAvaibility(
			final ServiceReference<Factory> wFactoryServiceRef,
			final int aServiceEvent) throws Exception {

		String wFactoryName = (String) wFactoryServiceRef
				.getProperty(PROP_FACTORY_NAME);
		if (wFactoryName == null) {
			throw new Exception(String.format(
					"Unable to find '%s' property in a Factory service",
					PROP_FACTORY_NAME));
		}

		CFactoryInfos wFactoryInfos = pFactoriesInfos.get(wFactoryName);
		if (wFactoryInfos != null) {
			wFactoryInfos
					.setFactoryServiceRef((ServiceEvent.REGISTERED == aServiceEvent) ? wFactoryServiceRef
							: null);
		}
	}

	/**
	 * Update the data model of this component according the availability of the
	 * Factory services.
	 *
	 * @throws Exception
	 */
	private void setFactoryServiceRefsAvaibility() throws Exception {

		for (ServiceReference<Factory> wFactoryServiceRef : getAllFactoryServiceRefs()) {

			setFactoryServiceRefAvaibility(wFactoryServiceRef,
					ServiceEvent.REGISTERED);
		}
	}

	/**
	 *
	 */
	private void unregisterFactoryServiceListener() {

		pBundleContext.removeServiceListener(this);
		pLogger.logInfo(this, "unregisterFactoryServiceListener",
				"UnRegistered=[%b] FactoryServiceListener=[%s]", true, this);
	}

	/**
	 *
	 */
	@Validate
	public void validate() {

		boolean wMustControlComponent = false;

		try {
			// retreive the composition file
			pCompositionFile = getCompositionFile();

			// itialize the component info map
			initMaps();

			wMustControlComponent = (pComponentInfos.size() > 0);

			// if there's no component to control
			if (!wMustControlComponent) {
				logControlerState(wMustControlComponent);
			} else
			// else, if there is at least one component to control
			{
				// instal 'Factory' service listener
				registerFactoryServiceListener();

				// log existing 'Factory' services
				logFactoryServiceRefs();

				// update the availability of the waited 'Factory' services in
				// the
				// data model
				setFactoryServiceRefsAvaibility();

				// if all the waited 'Factory' services available,
				// instanciates the wanted components
				if (isAllFactoriesAvailable()) {
					instancaiateComponents();
				}
			}

		} catch (Exception e) {
			pLogger.logSevere(this, "validate", "Error: %s", e);
		}

		pLogger.logInfo(
				this,
				"validate",
				"validated. InAction=[%b] isFactoriesAvailable=[%b] CompositionFile=[%s]",
				wMustControlComponent, isAllFactoriesAvailable(),
				pCompositionFile);

	}
}
