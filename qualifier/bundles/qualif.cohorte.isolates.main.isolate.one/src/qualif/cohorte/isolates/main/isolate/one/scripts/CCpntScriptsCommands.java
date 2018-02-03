/**
 * Copyright 2014 isandlaTech
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qualif.cohorte.isolates.main.isolate.one.scripts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.ServiceProperty;
import org.apache.felix.ipojo.annotations.Validate;
import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.cohorte.remote.IRemoteServicesConstants;
import org.psem2m.isolates.base.CAbstractGoGoCommand;
import org.psem2m.isolates.base.IGoGoCommand;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.utilities.CXQueryString;
import org.psem2m.utilities.files.CXFileDir;
import org.psem2m.utilities.rsrc.CXRsrcProvider;
import org.psem2m.utilities.scripting.CXJsException;
import org.psem2m.utilities.scripting.IXJsRuningReply;

/**
 * Implementations of Gogo shell commands to simplify OSGi debugging
 *
 * @author ogattaz
 */
@Component(name = "qualifier-cohorte-isolates-scripts-gogocommand-Factory")
@Provides(specifications = { IGoGoCommand.class })
public class CCpntScriptsCommands extends CAbstractGoGoCommand implements
		IGoGoCommand {

	/**
	 * The "pelix.remote.export.reject" property limits the remote export of the
	 * service
	 */
	@ServiceProperty(name = IRemoteServicesConstants.PROP_EXPORT_REJECT, immutable = true)
	private final String pBaseXShellCommandsNotRemote = IGoGoCommand.class
			.getName();

	/**
	 * The Gogo commands name. ATTENTION : look at the name of the methods and
	 * the declaration
	 */
	@ServiceProperty(name = "osgi.command.function", value = "{srun,slist,sinfos,sclean}")
	private String[] pCommands;

	private String pLastScriptUri = null;

	private Map<String, Object> pLastSrciptVariablesMap = null;

	/**
	 * Cohorte isolate logger service
	 */
	@Requires
	private IIsolateLoggerSvc pLogger;

	@Requires
	private CCpntQualifierScripts pQualifierScripts;

	/**
	 * The Gogo commands scope
	 */
	@ServiceProperty(name = "osgi.command.scope", value = "scipts")
	private String pScope;

	/**
	 * Sets up the members
	 *
	 * 
	 */
	public CCpntScriptsCommands() {
		super();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.psem2m.isolates.base.IGogoCommand#getCommands()
	 */
	@Override
	public String[] getCommands() {
		return pCommands;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.psem2m.isolates.base.CAbstractGoGoCommand#getLogger()
	 */
	@Override
	public IIsolateLoggerSvc getLogger() {
		return pLogger;
	}

	@Invalidate
	public void invalidate() {
		pLogger.logInfo(this, "invalidate", "invalidated");
	}

	/**
	 * @param aWithTrace
	 * @return
	 * @throws IOException
	 */
	private List<String> listScripts(final boolean aWithTrace)
			throws IOException {

		List<String> wScriptList = new ArrayList<>();
		int wScriptIdx = 0;

		List<CXRsrcProvider> wProviders = pQualifierScripts.getJsManager()
				.getProviders();
		int wIdx = 0;
		for (CXRsrcProvider wProfider : wProviders) {
			String wUrlPath = wProfider.getDefDirectory().getUrlPath();
			CXFileDir wProviderDir = new CXFileDir(wUrlPath);
			if (aWithTrace) {
				logTwiceInfo("slist",
						" - Provider(%d): urlPath=[%s] exists=[%b]", wIdx,
						wUrlPath, wProviderDir.exists());
			}

			ArrayList<File> wFiles = wProviderDir.getMyFiles(
					CXFileDir.getFilterExtension("js"),
					!CXFileDir.WITH_SUBDIRS, CXFileDir.WITH_TEXTFILE);
			for (File wFile : wFiles) {
				String wScriptUri = wFile.getName();
				wScriptList.add(wScriptUri);
				if (aWithTrace) {
					logTwiceInfo("slist", "   - script(%d): urlPath=[%s]",
							wScriptIdx, wScriptUri);
				}
				wScriptIdx++;
			}
			wIdx++;
		}
		return wScriptList;
	}

	@Descriptor("Clean the last script context")
	public void sclean() {
		logTwiceInfo("sclean", "execute");

		pLastScriptUri = null;
		pLastSrciptVariablesMap = null;
	}

	/**
	 * Prints the details of the given service
	 */
	@Descriptor("Prints the details of js manager")
	public void sinfos() {
		logTwiceInfo("sinfos", "execute");

		try {

			logTwiceInfo("slist", pQualifierScripts.getJsManager()
					.toDescription());

		} catch (final Exception e) {
			logTwiceSevere("sinfos", e,
					"Error when printing the details of js manager");
		}
	}

	/**
	 * Prints the result of getServiceReferences()
	 *
	 * @param aSpecification
	 *            Service specification
	 */
	@Descriptor("Prints the list of the script found in the RsrcProviders ")
	public void slist() {
		logTwiceInfo("slist", "execute  ");
		try {

			listScripts(true);

		} catch (final Exception e) {
			logTwiceSevere("slist", e, "Error printing list of script");
		}
	}

	/**
	 * Prints the references that matches the given specification
	 * 
	 * @param aSpecification
	 *            A service specification
	 */
	@Descriptor("Run a script available in the RsrcProviders")
	public void srun(
			@Descriptor("A script uri") @Parameter(absentValue = "", names = {
					"-u", "--uri" }) final String aScriptUri,
			@Descriptor("The variables to the script : id=value;id=value;...") @Parameter(absentValue = "", names = {
					"-v", "--variables" }) final String aVariablesDefs) {
		logTwiceInfo("srun", "execute  script(uri:%s,vars:%s)", aScriptUri,
				aVariablesDefs);

		try {
			String wScriptUri = aScriptUri;
			if (wScriptUri == null || wScriptUri.isEmpty()) {
				if (pLastScriptUri != null && !pLastScriptUri.isEmpty()) {
					wScriptUri = pLastScriptUri;
				} else {
					logTwiceInfo("srun", "Unable to launch the script [%s]",
							aScriptUri);
					return;
				}
			} else {
				// if numeric get the Idx scripturi in the list
				try {
					int wIdx = Integer.parseInt(wScriptUri);
					wScriptUri = listScripts(false).get(wIdx);
				} catch (final Exception e) {
					// nothing
				}
			}

			Map<String, Object> wScriptVariablesMap = new LinkedHashMap<>();

			if (aVariablesDefs != null && !aVariablesDefs.isEmpty()) {
				wScriptVariablesMap.putAll(new CXQueryString(aVariablesDefs,
						";", "=").toMapOfString());
			} else {
				if (pLastSrciptVariablesMap != null) {
					wScriptVariablesMap = pLastSrciptVariablesMap;
				}
			}

			logTwiceInfo("srun", "execute  script=[%s] variables=[%s])",
					wScriptUri, wScriptVariablesMap);

			IXJsRuningReply wReply = pQualifierScripts.runScript(wScriptUri,
					wScriptVariablesMap);

			pLastScriptUri = wScriptUri;
			pLastSrciptVariablesMap = wScriptVariablesMap;

			logTwiceInfo("srun", wReply.toDescription());

		} catch (final Exception e) {

			if (e instanceof CXJsException) {
				String wExcepCtx = "\n"
						+ ((CXJsException) e).getExcepCtx().toDescription()
						+ "\n";
				logTwiceSevere("srun",
						"Error when launching the script [%s]\n==> %s\n%s",
						aScriptUri, e.getMessage(), wExcepCtx);
			} else {
				logTwiceSevere("srun", e,
						"Error when launching the script [%s]", aScriptUri);
			}
		}
	}

	/**
	 * 
	 */
	@Validate
	public void validate() {
		pLogger.logInfo(this, "validate", "validated");
	}
}
