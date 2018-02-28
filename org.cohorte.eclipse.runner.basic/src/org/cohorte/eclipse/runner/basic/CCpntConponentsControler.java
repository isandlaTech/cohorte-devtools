package org.cohorte.eclipse.runner.basic;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

import javax.script.ScriptException;

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
import org.cohorte.composer.api.IIsolateComposer;
import org.cohorte.composer.api.RawComponent;
import org.cohorte.eclipse.runner.basic.jython.IFileFinder;
import org.cohorte.eclipse.runner.basic.jython.IFileIncluder;
import org.cohorte.eclipse.runner.basic.jython.IPythonBridge;
import org.cohorte.eclipse.runner.basic.jython.IPythonFactory;
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
import org.psem2m.utilities.logging.CXLoggerUtils;

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
@Component(name = "Cohorte-devtools-CConponentsControler-factory")
@Instantiate
public class CCpntConponentsControler implements ServiceListener {

	static final String FMT_COMPOSITION_FILENAME = "composition%s.js";

	static final String PROP_FACTORY_NAME = "factory.name";

	/**
	 * Define a file name suffix used to load alternate composition file as
	 * "compositionSuffix.js" rather than the classic "composition.js" file.
	 *
	 * <pre>
	 * -Dorg.conhorte.runner.basic.composition.suffix=_ogat_test ==> [conf/composition_ogat_test.js]
	 * </pre>
	 *
	 */
	static final String PROP_RUNNER_BASIC_COMPOSITION_FILENAME_SUFFIX = "org.cohorte.eclipse.runner.basic.composition.suffix";

	/**
	 * <pre>
	 * -Dorg.cohorte.eclipse.runner.basic.loglevel=INFO
	 * </pre>
	 */
	static final String PROP_RUNNER_BASIC_LOG_LEVEL = "org.cohorte.eclipse.runner.basic.loglevel";

	/**
	 * <pre>
	 * -Dorg.cohorte.eclipse.runner.basic.logging.servicerefs.filter=com.cohorte*
	 * </pre>
	 */
	static final String PROP_RUNNER_BASIC_LOGGING_SERVICEREF_FILTER = "org.cohorte.eclipse.runner.basic.logging.servicerefs.filter";

	/**
	 * <pre>
	 * -Dorg.cohorte.eclipse.runner.basic.cohorte.dev=${project_loc:cohorte-dev}
	 * </pre>
	 */
	static final String PROP_RUNNER_BASIC_COHORTE_DEV = "cohorte.dev";

	
	
	static final String[] PROPS_RUNNER_BASIC = { PROP_RUNNER_BASIC_LOG_LEVEL,
			PROP_RUNNER_BASIC_LOGGING_SERVICEREF_FILTER, PROP_RUNNER_BASIC_COMPOSITION_FILENAME_SUFFIX };

	private static String PYTHON_FACTORY = "controller";

	private final BundleContext pBundleContext;

	// the map compnonent name => component infos
	private final Map<String, CComponentInfos> pComponentInfos = new TreeMap<>();

	private final CXFileUtf8 pCompositionFile = null;

	// the map factory name => factory infos
	private final Map<String, CFactoryInfos> pFactoriesInfos = new TreeMap<>();

	private IFileFinder pFinder;

	private IFileIncluder pIncluder;

	@Requires(filter = "(!(service.imported=*))")
	// MOD_BD_20150811
	private IIsolateComposer pIsolateComposer;

	@Requires
	private IIsolateLoggerSvc pIsolateLogger;

	@Requires
	private IPlatformDirsSvc pPlatformDirsSvc;

	@Requires
	IPythonBridge pPythonBridge;

	@Requires
	private CShutdownGogoCommand pShutDownCommand;

	/**
	 * @param aBundleContext
	 */
	public CCpntConponentsControler(final BundleContext aBundleContext) {
		super();
		pBundleContext = aBundleContext;
	}

	/**
	 * buil a generic message about a property
	 *
	 * @param aPropertyName
	 *            eg.org.cohorte.eclipse.runner.basic.loglevel
	 * @param aMessage
	 *            eg. isn't defined
	 * @return eg. "The property [%s] of the Basic Runner isn't defined "
	 */
	private String buildBasicRunnerPropMessage(final String aPropertyName, final String aMessage) {
		return String.format("The  property [%s] of the Basic Runner %s", aPropertyName, aMessage);
	}

	/**
	 * <pre>
	 * -Dorg.cohorte.eclipse.runner.basic.loglevel=INFO
	 * -Dorg.cohorte.eclipse.runner.basic.service.filter=*iotpack*
	 * -Dorg.cohorte.eclipse.runner.basic.composition.suffix=${COMPOSITION_SUFFIX}
	 * -Djython.stdlib=${project_loc:org.cohorte.eclipse.runner.basic}/lib/Lib
	 * </pre>
	 */
	private void checkBasicRunnerProperties() {

		for (String wPropName : PROPS_RUNNER_BASIC) {

			String wValue = System.getProperty(wPropName);
			boolean wExists = (wValue != null && !wValue.isEmpty());
			pIsolateLogger.logInfo(this, "checkRunnerProperties", "PropName=[%-60s] Exixts=[%5s] value=[%s]", wPropName,
					wExists, wValue);

			if (!wExists) {
				if (PROP_RUNNER_BASIC_LOG_LEVEL.equals(wPropName)) {

					String wMessageBeginning = buildBasicRunnerPropMessage(PROP_RUNNER_BASIC_LOG_LEVEL,
							"isn't defined.");
					pIsolateLogger.logWarn(this, "checkRunnerProperties",
							"%s => the log level still set to FINE during the validation of that component [%s].",
							wMessageBeginning, getClass().getSimpleName());

				}
			}
		}

	}

	/**
	 * MOD_OG_20160906 Basic Runner log enhancement
	 *
	 * <pre>
	 * CConponentsControler_0956;                  validate; All Needed Factories are NOT Available, the components will not be instantiated!
	 * CConponentsControler_0956; eededFactoriesUnavailable; Unavailable Factory=[Agilium-CAgiliumServicesInfos-factory]
	 * </pre>
	 */
	private String dumpUnavailableFactories() {

		StringBuilder wSB = new StringBuilder();

		int wIdx = 0;
		for (final CFactoryInfos wDef : pFactoriesInfos.values()) {
			if (wDef.isNeeded() && !wDef.hasFactoryServiceRef()) {
				wIdx++;
				wSB.append(String.format("\n - Factory (%3d) : [%-80s] ", wIdx, wDef.getName()));

			}
		}
		return wSB.toString();
	}

	/**
	 * @return the collection af all the Factory services available in the service
	 *         registry.
	 * @throws InvalidSyntaxException
	 */
	private Collection<ServiceReference<Factory>> getAllFactoryServiceRefs() throws InvalidSyntaxException {

		return pBundleContext.getServiceReferences(Factory.class, null);
	}

	/**
	 * @return the log level ti use during the validation of that component
	 */
	private Level getBasicRunnerLogLevel() {
		String wLevel = System.getProperty(PROP_RUNNER_BASIC_LOG_LEVEL, "FINE");
		try {
			return Level.parse(wLevel);
		} catch (IllegalArgumentException e) {

			String wMessageBeginning = buildBasicRunnerPropMessage(PROP_RUNNER_BASIC_LOG_LEVEL,
					"contains a unparsable Level.");

			pIsolateLogger.logSevere(this, "checkRunnerProperties", "%s => wrong value=[%s] => use FINE",
					wMessageBeginning, wLevel);
			return Level.FINE;
		}
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
	private JSONArray getComponentDefs(final JSONObject aCompositionDef) throws JSONException {

		return aCompositionDef.getJSONObject("root").getJSONArray("components");
	}

	/**
	 * return the composition file merge using jython
	 *
	 * @return
	 * @throws ScriptException
	 * @throws FileNotFoundException
	 */
	private String getCompositionContent() {
		if (pIncluder != null) {
			final String wFileNameSuffix = pBundleContext.getProperty(PROP_RUNNER_BASIC_COMPOSITION_FILENAME_SUFFIX);
			final String wCompositionFileName = getCompositionFileName(wFileNameSuffix);
			final Object wRes = pIncluder.get_content("conf" + File.separatorChar + wCompositionFileName, false);

			pPythonBridge.remove(PYTHON_FACTORY);
			return wRes.toString();
		}
		return null;
	}

	/**
	 * Convert the content of the "base/conf/composituion.js" file in a json object.
	 *
	 * @param aCompositionFile
	 *            The "base/conf/composituion.js" file
	 * @return A json object instance
	 * @throws IOException
	 * @throws JSONException
	 */
	private JSONObject getCompositionDef() throws JSONException, IOException {

		pIsolateLogger.logInfo(this, "getCompositionDef", "call python includer");
		String wCompositionStr = getCompositionContent();

		// using jython doesn't work. means object can't be use or jython failed
		if (wCompositionStr == null) {
			pIsolateLogger.logInfo(this, "getCompositionDef", "python includer failed or not initialize, read file");

			final CXFileUtf8 wCompositionFile = getCompositionFile();
			wCompositionStr = wCompositionFile.readAll();
		}

		final JSONObject wComposition = new JSONObject(wCompositionStr);

		final JSONArray wParentsComponents = getParentsComponents(wComposition);

		for (int i = 0; i < wParentsComponents.length(); i++) {
			wComposition.getJSONObject("root").getJSONArray("components").put(wParentsComponents.get(i));
		}
		return wComposition;
	}

	/**
	 * @return an instance of CXFileUtf8 corresponding to the existing
	 *         "base/conf/composituion.js" file
	 *
	 * @throws IOException
	 */
	private CXFileUtf8 getCompositionFile() throws IOException {

		final CXFileDir wConfDir = new CXFileDir(pPlatformDirsSvc.getPlatformBase(), "conf");
		if (!wConfDir.exists()) {
			throw new IOException(
					String.format("The cohorte 'conf' directory [%s] doesn't exist", wConfDir.getAbsolutePath()));
		}

		// Returns the value of the requested property, or null if the property
		// is undefined.
		final String wFileNameSuffix = pBundleContext.getProperty(PROP_RUNNER_BASIC_COMPOSITION_FILENAME_SUFFIX);

		return getCompositionFile(wConfDir, wFileNameSuffix);
	}

	/**
	 * @param aConfDir
	 * @param aFileNameSuffix
	 * @return
	 * @throws IOException
	 */
	private CXFileUtf8 getCompositionFile(final CXFileDir aConfDir, final String aFileNameSuffix) throws IOException {

		final String wFileName = getCompositionFileName(aFileNameSuffix);

		final CXFileUtf8 wCompositionFile = new CXFileUtf8(aConfDir, wFileName);

		pIsolateLogger.logInfo(this, "getCompositionFile", "Suffix=[%s] FileName=[%s] Exists=[%b] path=[%s]",
				aFileNameSuffix, wFileName, wCompositionFile.exists(), wCompositionFile);

		// if the composition file doesn't exist => Exception
		if (!wCompositionFile.exists()) {

			final String wMessage = String.format("The cohorte composition file [%s] doesn't exist. path=[%s]",
					wFileName, wCompositionFile.getAbsolutePath());

			throw new IOException(wMessage);

		}
		return wCompositionFile;
	}

	private String getCompositionFileName(final String aFileNameSuffix) {
		return String.format(FMT_COMPOSITION_FILENAME, (aFileNameSuffix != null) ? aFileNameSuffix : "");
	}

	/**
	 * @return
	 * @throws InvalidSyntaxException
	 */
	private Collection<ServiceReference<Factory>> getFilteredFactoryServiceRefs() throws InvalidSyntaxException {

		String wLdapFilter = null;

		final String wFilter = System.getProperty(PROP_RUNNER_BASIC_LOGGING_SERVICEREF_FILTER, null);

		if (wFilter != null && !wFilter.isEmpty()) {
			// @see
			// http://www.ldapexplorer.com/en/manual/109010000-ldap-filter-syntax.htm

			// eg. (factory.name=fr.agilium.*
			wLdapFilter = String.format("(%s=%s)", PROP_FACTORY_NAME, wFilter);

			pIsolateLogger.logInfo(this, "getFilteredFactoryServiceRefs", "Current ldap filter=[%s]", wLdapFilter);

		}
		return pBundleContext.getServiceReferences(Factory.class, wLdapFilter);

	}

	/*
	 * MOD_BD_20161202
	 */
	private JSONArray getParentsComponents(final JSONObject aComposition) throws JSONException, IOException {
		final JSONArray wResult = new JSONArray();
		final JSONObject wRoot = aComposition.getJSONObject("root");
		if (wRoot != null) {
			final JSONArray wImportFiles = wRoot.optJSONArray("import-files");
			if (wImportFiles != null) {
				for (int i = 0; i < wImportFiles.length(); i++) {

					final CXFileDir wConfDir = new CXFileDir(pPlatformDirsSvc.getPlatformBase(), "conf");
					final String wImportFile = wImportFiles.getString(i);
					final CXFileUtf8 wParentCompositionFile = new CXFileUtf8(wConfDir, wImportFile);

					final JSONObject wParentComposition = new JSONObject(wParentCompositionFile.readAll());

					final JSONArray wParentComponents = getParentsComponents(wParentComposition);
					if (wParentComponents != null) {
						for (int j = 0; j < wParentComponents.length(); j++) {
							wResult.put(wParentComponents.get(j));
						}
					}
					if (wParentComposition.has("root")) {
						final JSONArray wComponents = wParentComposition.optJSONObject("root")
								.optJSONArray("components");
						if (wComponents != null) {
							for (int j = 0; j < wComponents.length(); j++) {
								wResult.put(wComponents.get(j));
							}
						}
					}
				}
			}
		}
		return wResult;
	}

	/**
	 * MOD_BD_20160406 log remaining factories
	 *
	 * @return list of remaining not available factories
	 */
	private List<String> getRemainingFactoriesList() {
		final List<String> wResult = new ArrayList<>();
		for (final CFactoryInfos wDef : pFactoriesInfos.values()) {
			if (wDef.isNeeded() && !wDef.hasFactoryServiceRef()) {
				wResult.add(wDef.getName());
			}
		}
		return wResult;
	}

	/**
	 * @return
	 */
	boolean hasCompositionFile() {
		return pCompositionFile != null;
	}

	/**
	 * Use IPythonBridge provided by CCpntPythonBridge.
	 *
	 * Note : it's the CCpntPythonBridge componenent which deals with the JytonLibs
	 */
	private void initJythonObject() {
		try {
			pIsolateLogger.logInfo(this, "initJythonObject", "Begin");
			// create finder and includer python object to resolve the
			// configuration
			// file
			final IPythonFactory wPythonFactory = pPythonBridge.getPythonObjectFactory(PYTHON_FACTORY,
					Arrays.asList(new String[] { pPlatformDirsSvc.getPlatformHome().getAbsolutePath()
							+ File.separatorChar + IPlatformDirsSvc.DIRNAME_REPOSITORY }));

			pFinder = (IFileFinder) wPythonFactory.newInstance(IFileFinder.class);
			pIsolateLogger.logInfo(this, "initJythonObject", "init IFileFinder %s ", pFinder);

			// set cohorte base and data to the finder

			pIncluder = (IFileIncluder) wPythonFactory.newInstance(IFileIncluder.class);
			pIsolateLogger.logInfo(this, "initJythonObject", "init IFileIncluder : %s", pIncluder);
			String wCohorteDev = System.getProperty(PROP_RUNNER_BASIC_COHORTE_DEV);
			if( wCohorteDev != null ) {
				// set the roots for the finder and add cohorte-dev
				pFinder._set_roots(Arrays.asList(new String[] { wCohorteDev, pPlatformDirsSvc.getNodeDataDir().getAbsolutePath(),
						pPlatformDirsSvc.getPlatformBase().getAbsolutePath() }));
			}else {
				pFinder._set_roots(Arrays.asList(new String[] { pPlatformDirsSvc.getNodeDataDir().getAbsolutePath(),
						pPlatformDirsSvc.getPlatformBase().getAbsolutePath() }));
			}
		
			pIncluder.set_finder(pFinder);

		} catch (final Exception e) {
			e.printStackTrace();

			pIsolateLogger.logSevere(this, "initJythonObject", "can't init Jython object %d", e);
		}
	}

	/**
	 * initialize the content of the "pFactoriesInfos" and "pComponentInfos" maps.
	 *
	 * <pre>
	 * </pre>
	 *
	 * MOD_OG_20150417 Manage explicitly the component flag "isInCurrentIsolate" and
	 * the factory flag "isNeeded"
	 *
	 * @throws JSONException
	 * @throws IOException
	 */
	private void initMaps() throws JSONException, IOException {

		final String wCurrentIsolateName = pPlatformDirsSvc.getIsolateName();

		pIsolateLogger.logInfo(this, "initMaps", "CurrentIsolateName={%s]", wCurrentIsolateName);

		final JSONArray wComponentDefArray = getComponentDefs(getCompositionDef());

		for (int wIdx = 0; wIdx < wComponentDefArray.length(); wIdx++) {

			final JSONObject wDef = wComponentDefArray.getJSONObject(wIdx);
			final String wfactoryName = wDef.getString(CComponentInfos.PROP_FACTORY);

			CFactoryInfos wFactoryInfos = pFactoriesInfos.get(wfactoryName);
			if (wFactoryInfos == null) {
				wFactoryInfos = new CFactoryInfos(wfactoryName);
				pFactoriesInfos.put(wfactoryName, wFactoryInfos);
			}
			final CComponentInfos wComponentInfo = new CComponentInfos(wDef, wFactoryInfos);

			// MOD_OG_20150417
			final boolean wInCurrentIsolate = wComponentInfo.initIsInCurrentIsolate(wCurrentIsolateName);

			// MOD_OG_20150417
			wFactoryInfos.setNeeded(wInCurrentIsolate);

			pIsolateLogger.logInfo(this, "initMaps", "FactoryName=[%-80s] setNeeded=[%s]", wFactoryInfos.getName(),
					wInCurrentIsolate);

			pComponentInfos.put(wComponentInfo.getName(), wComponentInfo);
		}
	}

	/**
	 * @throws ConfigurationException
	 * @throws MissingHandlerException
	 * @throws UnacceptableConfiguration
	 *
	 */
	private void instancaiateComponents()
			throws UnacceptableConfiguration, MissingHandlerException, ConfigurationException {

		final String wCurrentIsolateName = pPlatformDirsSvc.getIsolateName();

		pIsolateLogger.logInfo(this, "instancaiateComponents", "CurrentIsolateName=[%s]", wCurrentIsolateName);

		final Set<RawComponent> wRawCpnts = new LinkedHashSet<>();

		for (final CComponentInfos wComponentInfos : pComponentInfos.values()) {

			// synchronized (wComponentInfos) {
			// component is instantiated in this local isolate only if it
			// has no isolate defined in composition.js file or if the
			// defined isolate name match with this local isolate's name.
			// MOD_OG_20150417 use the explicit flag
			if (wComponentInfos.isInCurrentIsolate()) {
				if (!wComponentInfos.isCreated()) {

					// MOD_BD_20150629 using of Cohorte's Isolate Composer
					// to instantiate components
					final RawComponent wRawCpt = new RawComponent(wComponentInfos.getFactoryName(),
							wComponentInfos.getName());

					// MOD_OG_20160906 Addition of the managment of the
					// component properties
					if (wComponentInfos.hasProperties()) {
						wRawCpt.setProperties(wComponentInfos.getPropertiesMap());
					}
					wRawCpnts.add(wRawCpt);

					/*
					 * Properties wComponentProps = new Properties();
					 * wComponentProps.put("instance.name", wComponentInfos.getName());
					 * wComponentProps.put( Constants.SERVICE_EXPORTED_INTERFACES, "*");
					 * wComponentProps.put("toto", "lolo"); ComponentInstance wComponentInstance =
					 * wComponentInfos .getFactoryInfos().getFactory()
					 * .createComponentInstance(wComponentProps);
					 */
					wComponentInfos.setCreated();

					pIsolateLogger.logInfo(this, "instancaiateComponents",
							"RawComponent(%3d): name=[%-80s] factory=[%-80s] bundle=[%s][%s]", wRawCpnts.size(),
							wComponentInfos.getName(), wRawCpt.getFactory(), wRawCpt.getBundle_name(),
							wRawCpt.getBundle_version());
				} else {
					pIsolateLogger.logWarn(this, "instancaiateComponents", "--- Component [%s] is already created!",
							wComponentInfos.getName());
				}
			} else {

				pIsolateLogger.logDebug(this, "instancaiateComponents",
						"--- Component [%s] explicitly in another Isolate => [%s]", wComponentInfos.getName(),
						wComponentInfos.getIsolateName());
			}
			// }
		}
		final StringBuilder wSB = new StringBuilder();
		int wCpntIdx = 0;
		for (final RawComponent rc : wRawCpnts) {
			wCpntIdx++;
			wSB.append(String.format("\n -(%3d) : [%-80s] [%-80s]", wCpntIdx, rc.getName(), rc.getFactory()));
		}

		pIsolateLogger.logDebug(this, "instancaiateComponents", "List of components to instantiate: %s",
				wSB.toString());

		CXLoggerUtils.logBanner(pIsolateLogger, Level.INFO, this, "instancaiateComponents",
				"ISOLATE COMPOSER WILL START INSTANCIATION ...");

		// order the isolate composer to instantiate the components.
		pIsolateComposer.instantiate(wRawCpnts);
		pIsolateLogger.logDebug(this, "instancaiateComponents", "End instantiation");
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

		pIsolateLogger.logInfo(this, "invalidate", "invalidated");
	}

	/**
	 * MOD_OG_20150417 rename
	 *
	 * @return true if all the needed factory are available
	 */
	private boolean isAllNeededFactoriesAvailable() {

		for (final CFactoryInfos wDef : pFactoriesInfos.values()) {
			if (wDef.isNeeded() && !wDef.hasFactoryServiceRef()) {
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
	 *  logControlerState; #########################################################################################################################################################################################################
	 *  logControlerState; #
	 *  logControlerState; # [org.cohorte.eclipse.runner.basic.CConponentsControler] in action [true]
	 *  logControlerState; #
	 *  logControlerState; # ## Component=[IotPack-Aggregator-Core-Managers-CCpntAuthenticationManager                     ]   created:[ true] timeStamp=[2017-11-18T18:07:15.0000275+0100] isInCurrentIsolate=[ true]
	 *  logControlerState; #    - Factory=[IotPack-Aggregator-Core-Managers-CCpntAuthenticationManager-Factory             ] available=[ true]  instance=[IotPack-Aggregator-Core-Managers-CCpntAuthenticationManager-Factory_2114186360  ]
	 *  logControlerState; # ## Component=[IotPack-Aggregator-RestApi-CCpntSensors                                         ]   created:[ true] timeStamp=[2017-11-18T18:07:15.0000275+0100] isInCurrentIsolate=[ true]
	 *  logControlerState; #    - Factory=[IotPack-Aggregator-RestApi-CCpntSensors-Factory                                 ] available=[ true]  instance=[IotPack-Aggregator-RestApi-CCpntSensors-Factory_1092639615                      ]
	 *  logControlerState; # ## Component=[IotPack-Aggregator-Core-Internal-CCpntSubSystemsRealm                           ]   created:[ true] timeStamp=[2017-11-18T18:07:15.0000275+0100] isInCurrentIsolate=[ true]
	 *  logControlerState; #    - Factory=[IotPack-Aggregator-Core-Internal-CCpntSubSystemsRealm-Factory                   ] available=[ true]  instance=[IotPack-Aggregator-Core-Internal-CCpntSubSystemsRealm-Factory_99111414          ]
	 *
	 *  ...
	 *
	 *  logControlerState; # ## Component=[IotPack-Aggregator-RestApi-CCpntModules                                         ]   created:[ true] timeStamp=[2017-11-18T18:07:15.0000278+0100] isInCurrentIsolate=[ true]
	 *  logControlerState; #    - Factory=[IotPack-Aggregator-RestApi-CCpntModules-Factory                                 ] available=[ true]  instance=[IotPack-Aggregator-RestApi-CCpntModules-Factory_393947289                       ]
	 *  logControlerState; # ## Component=[IotPack-RawData-CCpntLoraUdpListenerLoggerFile                                  ]   created:[ true] timeStamp=[2017-11-18T18:07:15.0000278+0100] isInCurrentIsolate=[ true]
	 *  logControlerState; #    - Factory=[IotPack-RawData-CCpntLoraUdpListenerLoggerFile-factory                          ] available=[ true]  instance=[IotPack-RawData-CCpntLoraUdpListenerLoggerFile-factory_2046127282               ]
	 *  logControlerState; #
	 *  logControlerState; #########################################################################################################################################################################################################
	 *           validate; validated. InAction=[true] isAllNeededFactoriesAvailable=[true] CompositionFile=[null] remainingFactories=[]
	 * </pre>
	 *
	 * @param aInAction
	 */
	private void logControlerState(final boolean aInAction) {

		final StringBuilder wSB = new StringBuilder();
		wSB.append(String.format("\n#%s", CXStringUtils.strFromChar('#', 200)));
		wSB.append("\n#");
		wSB.append(String.format("\n# [%s] in action [%s]", getClass().getName(), aInAction));
		wSB.append("\n#");

		if (!aInAction) {
			wSB.append(String.format("\n# UNABLE TO INSTANCIATE THE COMPONENTS OF THE COMPOSITION [%s]",
					pCompositionFile));
		} else {
			for (final CComponentInfos wComponentInfos : pComponentInfos.values()) {
				final CFactoryInfos wFactoryInfos = wComponentInfos.getFactoryInfos();

				// log all the components only if the level is FINER ( higher
				// than DEBUG )
				final boolean wLogComponent = wComponentInfos.isInCurrentIsolate()
						|| pIsolateLogger.isLoggable(Level.FINER);

				if (wLogComponent) {
					wSB.append(String.format(
							"\n# ## Component=[%-80s]   created:[%5s] timeStamp=[%s] isInCurrentIsolate=[%5s]",
							wComponentInfos.getName(), wComponentInfos.isCreated(),
							wComponentInfos.getCreationTimeStamp(), wComponentInfos.isInCurrentIsolate()));

					wSB.append(String.format("\n#    - Factory=[%-80s] available=[%5s]  instance=[%-80s]",
							wFactoryInfos.getName(), wFactoryInfos.hasFactoryServiceRef(),
							wFactoryInfos.getFactoryServiceInfos()));
				}
			}
		}
		wSB.append("\n#");
		wSB.append(String.format("\n#%s", CXStringUtils.strFromChar('#', 200)));

		for (final String wLine : wSB.toString().split("\n")) {
			pIsolateLogger.logInfo(this, "logControlerState", wLine);
		}
	}

	/**
	 * ====> ONLY IF LOG LEVEL IS FINER
	 *
	 * <pre>
	 *  logFactoryServiceRef; ServiceReferenceImpl_1345167917
	 *  logFactoryServiceRef;  |  0)                          component.class=[com.cohorte.iot.aggregator.core.managers.CCpntStartersManager]
	 *  logFactoryServiceRef;  |  1)                    component.description=[factory name="IotPack-Aggregator-Core-Managers-CCpntStartersManager-Factory" bundle="62" state="valid" requiredhandlers list="[org.apache.felix.ipojo:requires, org.apache.felix.ipojo:callback, org.apache.felix.ipojo:provides, org.apache.felix.ipojo:architecture]"§	missinghandlers list="[]"§	provides com.cohorte.iot.api.managers.IDataManager, com.cohorte.iot.aggregator.core.internal.ISubSystem, com.cohorte.iot.api.managers.system.IModelController, com.cohorte.iot.api.managers.IManager, com.cohorte.iot.api.managers.IStartersManager, r.core.internal.manager.CAbstractManager, com.cohorte.iot.aggregator.core.internal.manager.CAbstractModelManager, com.cohorte.iot.aggregator.core.api.CAbstractComponentBase,
	 *  logFactoryServiceRef;  |  2)                     component.properties=[]
	 *  logFactoryServiceRef;  |  3)  component.providedServiceSpecifications=[com.cohorte.iot.api.managers.IStartersManager]
	 *  logFactoryServiceRef;  |  4)                             factory.name=[IotPack-Aggregator-Core-Managers-CCpntStartersManager-Factory]
	 *  logFactoryServiceRef;  |  5)                            factory.state=[1]
	 *  logFactoryServiceRef;  |  6)                              objectClass=[org.apache.felix.ipojo.Factory]
	 *  logFactoryServiceRef;  |  7)                         service.bundleid=[62]
	 *  logFactoryServiceRef;  |  8)                               service.id=[370]
	 *  logFactoryServiceRef;  |  9)                              service.pid=[IotPack-Aggregator-Core-Managers-CCpntStartersManager-Factory]
	 *  logFactoryServiceRef;  | 10)                            service.scope=[singleton]
	 *  logFactoryServiceRef; ServiceReferenceImpl_1004428252
	 *  logFactoryServiceRef;  |  0)                          component.class=[com.cohorte.iot.aggregator.core.internal.session.CCpntDefaultSessionManager]
	 *  logFactoryServiceRef;  |  1)                    component.description=[factory name="IotPack-Aggregator-Core-Internal-CCpntDefaultSessionManager-Factory" bundle="62" state="valid" sionManager"§	requiredhandlers list="[org.apache.felix.ipojo:callback, org.apache.felix.ipojo:provides, org.apache.felix.ipojo:architecture]"§	missinghandlers list="[]"§	provides erited interfaces="[com.cohorte.iot.aggregator.core.internal.IInternalSessionManager]" superclasses="[]"]
	 *  logFactoryServiceRef;  |  2)                     component.properties=[]
	 *  logFactoryServiceRef;  |  3)  component.providedServiceSpecifications=[com.cohorte.iot.aggregator.core.internal.IInternalSessionManager]
	 *  logFactoryServiceRef;  |  4)                             factory.name=[IotPack-Aggregator-Core-Internal-CCpntDefaultSessionManager-Factory]
	 *  logFactoryServiceRef;  |  5)                            factory.state=[1]
	 *  logFactoryServiceRef;  |  6)                              objectClass=[org.apache.felix.ipojo.Factory]
	 *  logFactoryServiceRef;  |  7)                         service.bundleid=[62]
	 *  logFactoryServiceRef;  |  8)                               service.id=[331]
	 *  logFactoryServiceRef;  |  9)                              service.pid=[IotPack-Aggregator-Core-Internal-CCpntDefaultSessionManager-Factory]
	 *  logFactoryServiceRef;  | 10)                            service.scope=[singleton]
	 * </pre>
	 *
	 * @param wfactorySRef
	 */
	private void logFactoryServiceRef(final ServiceReference<Factory> wfactorySRef) {

		if (!pIsolateLogger.isLoggable(Level.FINER)) {
			return;
		}

		final String[] wPropertyKeys = wfactorySRef.getPropertyKeys();

		pIsolateLogger.log(Level.FINER, this, "logFactoryServiceRef", "%s_%s", wfactorySRef.getClass().getSimpleName(),
				wfactorySRef.hashCode(), wPropertyKeys.length);

		int wIdx = 0;
		for (final String wKey : wPropertyKeys) {
			String wStrValue = null;
			final Object wObj = wfactorySRef.getProperty(wKey);
			if (wObj != null) {
				if (wObj instanceof String) {
					wStrValue = (String) wObj;
				} else
				//
				if (wObj instanceof String[]) {
					wStrValue = CXStringUtils.stringTableToString((String[]) wObj);
				} else
				//
				if (wObj instanceof PropertyDescription[]) {
					final StringBuilder wSB = new StringBuilder();
					for (final PropertyDescription wPropertyDescription : ((PropertyDescription[]) wObj)) {
						wSB.append(String.format("%s=\"%s\" ", wPropertyDescription.getName(),
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
			pIsolateLogger.log(Level.FINER, this, "logFactoryServiceRef", " | %2d) %40s=[%s]", wIdx, wKey, wStrValue);
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

		for (final ServiceReference<Factory> wfactorySRef : getFilteredFactoryServiceRefs()) {
			logFactoryServiceRef(wfactorySRef);
		}
	}

	/**
	 * @throws InvalidSyntaxException
	 *
	 */
	private void registerFactoryServiceListener() throws InvalidSyntaxException {

		final String wFilter = "(objectclass=" + Factory.class.getName() + ")";
		pBundleContext.addServiceListener(this, wFilter);

		pIsolateLogger.logInfo(this, "registerFactoryServiceListener", "Registered=[%b] FactoryServiceListener=[%s]",
				true, this);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.osgi.framework.ServiceListener#serviceChanged(org.osgi.framework.
	 * ServiceEvent)
	 */
	@Override
	public void serviceChanged(final ServiceEvent aServiceEvent) {

		try {
			@SuppressWarnings("unchecked")
			final ServiceReference<Factory> wFactoryServiceRef = (ServiceReference<Factory>) aServiceEvent
					.getServiceReference();

			switch (aServiceEvent.getType()) {
			case ServiceEvent.REGISTERED: {
				setFactoryServiceRefAvaibility(wFactoryServiceRef, ServiceEvent.REGISTERED);
				break;
			}
			case ServiceEvent.UNREGISTERING: {
				setFactoryServiceRefAvaibility(wFactoryServiceRef, ServiceEvent.UNREGISTERING);
				break;
			}
			}

			if (isAllNeededFactoriesAvailable()) {
				// instancaiateComponents();
			}

		} catch (final Exception e) {
			pIsolateLogger.logSevere(this, "serviceChanged", "Error: %s", e);
		}
	}

	/**
	 * @param wFactoryServiceRef
	 * @param aServiceEvent
	 * @throws Exception
	 */
	private void setFactoryServiceRefAvaibility(final ServiceReference<Factory> wFactoryServiceRef,
			final int aServiceEvent) throws Exception {

		final String wFactoryName = (String) wFactoryServiceRef.getProperty(PROP_FACTORY_NAME);
		if (wFactoryName == null) {
			throw new Exception(String.format("Unable to find '%s' property in a Factory service", PROP_FACTORY_NAME));
		}

		final CFactoryInfos wFactoryInfos = pFactoriesInfos.get(wFactoryName);
		if (wFactoryInfos != null) {
			final boolean wRegistered = (ServiceEvent.REGISTERED == aServiceEvent);
			wFactoryInfos.setFactoryServiceRef(wRegistered ? wFactoryServiceRef : null);

			pIsolateLogger.logInfo(this, "setFactoryServiceRefAvaibility",
					"FactoryName=[%-80s] Registered=[%5s] FactoryServiceRef=[%s]", wFactoryName, wRegistered,
					wRegistered
							? wFactoryServiceRef.toString() + '_'
									+ CXStringUtils.strAdjustRight(wFactoryServiceRef.hashCode(), 5)
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

		final Collection<ServiceReference<Factory>> wFactoryServiceRefs = getAllFactoryServiceRefs();

		pIsolateLogger.logInfo(this, "setFactoryServiceRefsAvaibility", "NbAvalaibleServicefactory=[%d]",
				wFactoryServiceRefs.size());

		for (final ServiceReference<Factory> wFactoryServiceRef : wFactoryServiceRefs) {

			setFactoryServiceRefAvaibility(wFactoryServiceRef, ServiceEvent.REGISTERED);
		}
	}

	/**
	 *
	 */
	private void unregisterFactoryServiceListener() {

		pBundleContext.removeServiceListener(this);
		pIsolateLogger.logInfo(this, "unregisterFactoryServiceListener",
				"UnRegistered=[%b] FactoryServiceListener=[%s]", true, this);
	}

	/**
	 * <pre>
	 * 2017/11/18; 20:11:30:998; INFO   ;          Timer-0; pntConponentsControler_5277;                  validate; Validating...
	 * 2017/11/18; 20:11:30:998; INFO   ;          Timer-0; pntConponentsControler_5277;                  validate;
	 *
	 * --------------------------------------------------------------------------------------------------------------------------------------------
	 * COHORTE Basic Runner for Eclipse :  Component Controler starting...
	 * --------------------------------------------------------------------------------------------------------------------------------------------
	 *
	 * </pre>
	 */
	@Validate
	public void validate() {

		pIsolateLogger.logInfo(this, "validate", "Validating...");

		CXLoggerUtils.logBanner(pIsolateLogger, Level.INFO, this, "validate", '-', false,
				"COHORTE Basic Runner for Eclipse : Component Controler starting...");

		checkBasicRunnerProperties();

		Level wCurrentLogLevel = pIsolateLogger.getLevel();
		pIsolateLogger.setLevel(getBasicRunnerLogLevel());

		boolean wMustControlComponent = false;

		try {
			// init jython object to get composition
			initJythonObject();

			// itialize the component info map
			initMaps();

			wMustControlComponent = (pComponentInfos.size() > 0);

			// if there's no component to control
			if (!wMustControlComponent) {
				pIsolateLogger.logSevere(this, "validate", "There is no component to control in this isolate!");
				logControlerState(wMustControlComponent);
			} else
			// else, if there is at least one component to control
			{
				// instal 'Factory' service listener
				registerFactoryServiceListener();

				// log existing 'Factory' services
				logFactoryServiceRefs();

				// update the availability of the waited 'Factory' services in
				// the data model
				setFactoryServiceRefsAvaibility();

				// if all the needed 'Factory' services are available,
				// instanciates the wanted components
				if (isAllNeededFactoriesAvailable()) {

					CXLoggerUtils.logBanner(pIsolateLogger, Level.INFO, this, "validate", '-', false,
							"COHORTE Basic Runner for Eclipse : All the needed factories are Available => proceed to the instantiation of the components.");

					instancaiateComponents();
				}

				// All the needed factories are NOT available
				else {
					StringBuilder wText = new StringBuilder();

					wText.append("COHORTE Basic Runner for Eclipse :");
					wText.append("\nAll the needed factories are NOT Available.");
					wText.append("\nThe components will NOT be instantiated and the OSGi framework will stop.");
					wText.append("\nList of the unavailabled factories:");
					wText.append(dumpUnavailableFactories());

					CXLoggerUtils.logBanner(pIsolateLogger, Level.SEVERE, this, "validate", '-', false,
							wText.toString());

					// in the Eclipse console !
					System.out.println(CXLoggerUtils.buildBanner('-', false, wText.toString()));

					pShutDownCommand.shutdown();

				}
			}

		} catch (final Exception e) {
			pIsolateLogger.logSevere(this, "validate", "Error: %s", e);
			e.printStackTrace();
			pShutDownCommand.shutdown();

		} finally {

			// reset
			pIsolateLogger.setLevel(wCurrentLogLevel);

		}
		pIsolateLogger.logInfo(this, "validate",
				"validated. InAction=[%b] isAllNeededFactoriesAvailable=[%b] CompositionFile=[%s] remainingFactories=[%s]",
				wMustControlComponent, isAllNeededFactoriesAvailable(), pCompositionFile,
				CXStringUtils.stringListToString(getRemainingFactoriesList()));

	}
}