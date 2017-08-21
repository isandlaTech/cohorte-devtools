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
import org.apache.felix.ipojo.annotations.Validate;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.isolates.services.dirs.IPlatformDirsSvc;

/**
 * component that allow a bridge between java and python using jython it provide
 * a way to instanciate python class implement an java interface
 *
 * @author apisu
 *
 */
@Component
@Instantiate
@Provides(specifications = IPythonBridge.class)
public class CCpntPythonBridge implements IPythonBridge {

	@Requires
	IIsolateLoggerSvc pLogger;

	/**
	 * contain a map of factory and python interpreter so
	 */
	Map<String, IPythonFactory> pMapFactory;

	@Requires
	IPlatformDirsSvc pPlatformDirsSvc;

	private String pStdLibPath;

	public CCpntPythonBridge() {
		pMapFactory = new HashMap<String, IPythonFactory>();
	}

	@Override
	public IPythonFactory getPythonObjectFactory(final String aId) {
		return this.getPythonObjectFactory(aId, null);
	}

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

	@Override
	public void remove(final String aId) {
		synchronized (pMapFactory) {
			IPythonFactory wFactory = pMapFactory.remove(aId);
			// clean properly the factory
			wFactory.clear();
		}
	}

	@Validate
	public void validate() {
		pLogger.logInfo(this, "validate", "validating...");
		// check existance of
		try {
			String wDataDir = pPlatformDirsSvc.getNodeDataDir()
					.getAbsolutePath();
			pStdLibPath = wDataDir + File.separatorChar + "jython";
			if (!new File(pStdLibPath).exists()) {
				throw new Exception(String.format(
						"jython standard librarie can't be find in data in %s",
						pStdLibPath));
			}
		} catch (Exception e) {
			pLogger.logSevere(this, "validate", "ERROR %s", e);

		}
		pLogger.logInfo(this, "validate", "validated");
	}
}
