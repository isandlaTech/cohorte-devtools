package qualif.cohorte.isolates.main.isolate.one.scripts;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.script.ScriptEngineManager;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.ServiceProperty;
import org.apache.felix.ipojo.annotations.Validate;
import org.cohorte.extra.jsrhino.IJsRhinoRunner;
import org.cohorte.remote.IRemoteServicesConstants;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.isolates.services.dirs.IPlatformDirsSvc;
import org.psem2m.utilities.CXBytesUtils;
import org.psem2m.utilities.files.CXFileDir;
import org.psem2m.utilities.rsrc.CXRsrcProviderFile;
import org.psem2m.utilities.scripting.CXJsManager;
import org.psem2m.utilities.scripting.IXJsRuningReply;

@Component(name = "qualifier-cohorte-isolates-scripts-Factory")
@Provides(specifications = { CCpntQualifierScripts.class })
public class CCpntQualifierScripts {

	/**
	 * The "pelix.remote.export.reject" property limits the remote export of the
	 * service
	 */
	@ServiceProperty(name = IRemoteServicesConstants.PROP_EXPORT_REJECT, immutable = true)
	private final String pBaseXShellCommandsNotRemote = CCpntQualifierScripts.class
			.getName();

	@Requires
	private IIsolateLoggerSvc pIsolateLogger;

	private CXJsManager pJsManager = null;

	@Requires
	private IJsRhinoRunner pJsRhinoRunner;

	@Requires
	private IIsolateLoggerSvc pLogger;

	@Requires
	private IPlatformDirsSvc pPlatformDirsSvc;

	private CXFileDir pScriptsBaseDir = null;

	private CXFileDir pScriptsDataDir = null;

	String SCRIPTS_DIR_NAME = "scripts";

	/**
	 * 
	 */
	public CCpntQualifierScripts() {
		super();
	}

	/**
	 * @return
	 */
	public CXJsManager getJsManager() {
		return pJsManager;
	}

	/**
	 * 
	 */
	@Invalidate
	public void invalidate() {
		pLogger.logInfo(this, "invalidate", "invalidating...");

		try {

			if (pJsManager != null) {

				// free something ?

				pJsManager = null;
			}

		} catch (Exception | Error e) {
			pLogger.logSevere(this, "invalidate", "ERROR: %s", e);
		}

		pLogger.logInfo(this, "invalidate", "invaldated");
	}

	/**
	 * @param aScriptId
	 *            eg. "script_one.js" or "script_one" or "/script_one.js"
	 */
	public IXJsRuningReply runScript(final String aScriptUri) throws Exception {
		return runScript(aScriptUri, null);
	}

	/**
	 * @param aScriptId
	 *            eg. "script_one.js" or "script_one" or "/script_one.js"
	 * @param aVariablesMap
	 * @return
	 * @throws Exception
	 */
	public IXJsRuningReply runScript(final String aScriptUri,
			final Map<String, Object> aVariablesMap) throws Exception {

		Map<String, Object> wScriptVariablesMap = (aVariablesMap != null) ? aVariablesMap
				: new LinkedHashMap<>();

		wScriptVariablesMap.put("gPlatformDirsSvc", pPlatformDirsSvc);

		// The given activity logger can be a contextual one (eg.
		// pInternalSystem.getActivityLogger() => see IoT Pack)
		return pJsManager.runScript(pIsolateLogger, aScriptUri,
				wScriptVariablesMap);
	}

	/**
	 * 
	 */
	@Validate
	public void validate() {
		pLogger.logInfo(this, "validate", "validating...");

		try {

			// .../cohorte_base/scripts
			pScriptsBaseDir = new CXFileDir(pPlatformDirsSvc.getPlatformBase(),
					SCRIPTS_DIR_NAME);

			pLogger.logInfo(this, "validate", "ScriptsBaseDir=[%s]",
					pScriptsBaseDir.getAbsolutePath());
			pLogger.logInfo(this, "validate", "ScriptsBaseDir.exists=[%b]",
					pScriptsBaseDir.exists());

			// .../cohorte_data/scripts
			pScriptsDataDir = new CXFileDir(pPlatformDirsSvc.getNodeDataDir(),
					SCRIPTS_DIR_NAME);

			pLogger.logInfo(this, "validate", "ScriptsDataDir=[%s]",
					pScriptsDataDir.getAbsolutePath());
			pLogger.logInfo(this, "validate", "ScriptsDataDir.exists=[%b]",
					pScriptsDataDir.exists());
			ScriptEngineManager wScriptEngineManager = pJsRhinoRunner
					.getManager();

			pLogger.logInfo(this, "validate", "Available engines:\n%s",
					CXJsManager.dumpAvailableEngines(wScriptEngineManager));

			pJsManager = new CXJsManager(pIsolateLogger, wScriptEngineManager,
					IJsRhinoRunner.JS_RHINO_NAME);

			// TODO : to read in properties
			boolean wMustCheckTimeStamp = true;
			pJsManager.setCheckTimeStamp(wMustCheckTimeStamp);

			pJsManager.addProvider(
					"ScriptsBaseDir",
					new CXRsrcProviderFile(pScriptsBaseDir, Charset
							.forName(CXBytesUtils.ENCODING_UTF_8)));

			pJsManager.addProvider(
					"ScriptsDataDir",
					new CXRsrcProviderFile(pScriptsDataDir, Charset
							.forName(CXBytesUtils.ENCODING_UTF_8)));

			pLogger.logInfo(this, "validate", "JsManager:\n%s",
					pJsManager.toDescription());

		} catch (Exception | Error e) {
			pLogger.logSevere(this, "validate", "ERROR: %s", e);
		}

		pLogger.logInfo(this, "validate", "valdated");
	}
}
