package org.cohorte.eclipse.runner.basic.jython;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.ServiceController;
import org.apache.felix.ipojo.annotations.Validate;
import org.cohorte.eclipse.runner.basic.CXRunnerUtils;
import org.osgi.framework.BundleContext;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.isolates.services.dirs.IPlatformDirsSvc;
import org.psem2m.utilities.CXException;

/**
 * component that allow a bridge between java and python using jython it provide
 * a way to instanciate python class implement an java interface
 *
 * @author apisu
 *
 */
@Component(name = "Cohorte-devtools-CCpntPythonBridge-factory")
@Instantiate
@Provides(specifications = IPythonBridge.class)
public class CCpntPythonBridge implements IPythonBridge {

	/**
	 * <pre>
	 * -Dorg.cohorte.eclipse.runner.basic.jython.stdlib=${project_loc:org.cohorte.eclipse.runner.basic}/lib
	 * </pre>
	 */
	static final String PROP_JYTHON_STD_LIB_PATH = "org.cohorte.eclipse.runner.basic.jython.stdlib.path";

	private final BundleContext pBundleContext;

	@Requires
	IIsolateLoggerSvc pLogger;

	/**
	 * contain a map of factory and python interpreter so
	 */
	Map<String, IPythonFactory> pMapFactory;

	@Requires
	IPlatformDirsSvc pPlatformDirsSvc;

	// Service not published by default
	@ServiceController(value = false)
	private boolean pServiceController;

	private String pStdLibPath;

	/**
	 * @param aContext
	 */
	public CCpntPythonBridge(final BundleContext aBundleContext) {
		pMapFactory = new HashMap<>();
		pBundleContext = aBundleContext;
	}

	/**
	 * @return
	 */
	BundleContext getBundleContext() {
		return pBundleContext;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.cohorte.eclipse.runner.basic.jython.IPythonBridge#getPythonObjectFactory
	 * (java.lang.String)
	 */
	@Override
	public IPythonFactory getPythonObjectFactory(final String aId) {
		return this.getPythonObjectFactory(aId, null);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.cohorte.eclipse.runner.basic.jython.IPythonBridge#getPythonObjectFactory
	 * (java.lang.String, java.util.List)
	 */
	@Override
	public IPythonFactory getPythonObjectFactory(final String aId,
			final List<String> aPythonPath) {
		// need to add issue jython http://bugs.jython.org/issue2355
		synchronized (pMapFactory) {

			if (pMapFactory.get(aId) == null) {
				pMapFactory.put(aId, new CPythonFactory(aPythonPath,
						pStdLibPath));
			}
		}
		return pMapFactory.get(aId);
	}

	/**
	 *
	 */
	@Invalidate
	public void inValidate() {
		pLogger.logInfo(this, "inValidate", "inValidating...");
		// clean all factory
		synchronized (pMapFactory) {
			pMapFactory.values().stream().forEach(wFactory -> {
				wFactory.clear();
			});
			// clean properly the factory
			pMapFactory.clear();
		}

		pLogger.logInfo(this, "inValidate", "inValidated");

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.cohorte.eclipse.runner.basic.jython.IPythonBridge#remove(java.lang
	 * .String)
	 */
	@Override
	public void remove(final String aId) {
		synchronized (pMapFactory) {
			final IPythonFactory wFactory = pMapFactory.remove(aId);
			// clean properly the factory
			wFactory.clear();
		}
	}

	/**
	 *
	 */
	@Validate
	public void validate() {
		pLogger.logInfo(this, "validate", "validating...");
		// check existance of
		try {
			// add stdLibPath

			pStdLibPath = System.getProperty(PROP_JYTHON_STD_LIB_PATH);

			pLogger.logInfo(this, "validate", "	Jython Std Lib Path=[%s]",
					pStdLibPath);

			if (pStdLibPath == null || !new File(pStdLibPath).exists()) {
				throw new Exception(
						String.format(
								"jython standard librarie can't be found in path [%s]. Look at the value uo give [-D%s=...]",
								pStdLibPath, PROP_JYTHON_STD_LIB_PATH));
			}

			// Service published if all OK
			pServiceController = true;

		} catch (final Exception e) {
			pLogger.logSevere(this, "validate", "ERROR %s", e);

			final StringBuilder wMess = new StringBuilder();
			wMess.append('\n').append(
					"The component 'CCpntPythonBridge' isn't validated.");
			wMess.append('\n').append(
					"The service 'IPythonBridge' isn't registred.");
			wMess.append('\n').append(
					String.format("ERROR: %s",
							CXException.eCauseMessagesInString(e)));
			wMess.append('\n').append("The OSGi framework is stopping.");

			CXRunnerUtils.stopIsolate(pBundleContext, wMess.toString());
		}
		pLogger.logInfo(this, "validate", "validated");
	}

}
