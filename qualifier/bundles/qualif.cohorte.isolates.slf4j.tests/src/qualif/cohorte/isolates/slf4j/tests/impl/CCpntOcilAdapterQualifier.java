package qualif.cohorte.isolates.slf4j.tests.impl;

import java.util.logging.Level;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.cohorte.isolates.slf4j_ocil.IOcilManager;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.utilities.logging.CXJulUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qualif.cohorte.isolates.slf4j.tests.IOcilAdapterQualifier;

@Component(name = "qualifier-cohorte-isolates-ocil-adapter-factory")
@Provides(specifications = IOcilAdapterQualifier.class)
public class CCpntOcilAdapterQualifier implements IOcilAdapterQualifier {

	// e.g. CCpntOcilAdapterQualifier.test.A
	private static final String LOGGER_NAME_A = CCpntOcilAdapterQualifier.class
			.getSimpleName() + ".test.A";
	@Requires
	private IIsolateLoggerSvc pIsolateLogger;

	@Requires
	private IOcilManager pOcilManager;

	// simulate an existing Slf4j Logger
	private final Logger pSlf4jLogger = LoggerFactory.getLogger(LOGGER_NAME_A);

	/**
	 *
	 */
	public CCpntOcilAdapterQualifier() {
		super();
		pSlf4jLogger
				.info("CCpntOCILBridgeQualifier: instanciated : Hello World");
	}

	/**
	 *
	 */
	@Validate
	void validate() {
		pIsolateLogger.logInfo(this, "validate", "validating...");
		try {

			// ---- TEST A : existing Slf4j Ocil Logger Adapter ---

			// set the level of an existing Slf4j Ocil Loggr Adapter
			pOcilManager.setLevel(Level.INFO, LOGGER_NAME_A);

			// Test logging using the existing Slf4j Ocil Loggr Adapter
			pSlf4jLogger.info("Test log Info in pSlf4jlogger ");

			// set the level of all the existing Slf4j Ocil Loggr Adapter
			pOcilManager.setLevel(Level.FINE);

			// ---- TEST B : new Slf4j Ocil Loggr Adapter ---

			final String wNewLoggerName = getClass().getSimpleName()
					+ ".test.B";

			// new Slf4j Logger => new Slf4j Ocil Loggr Adapter
			final Logger wNewSlf4jlogger = LoggerFactory
					.getLogger(wNewLoggerName);

			// set the level of the new Slf4j Ocil Loggr Adapter
			pOcilManager.setLevel(Level.INFO, wNewLoggerName);

			// Test logging using the new Slf4j Ocil Loggr Adapter
			wNewSlf4jlogger.info("Test log Info in wSlf4jlogger ");

			pIsolateLogger.logInfo(this, "validate",
					CXJulUtils.dumpCurrentLoggers());

		} catch (Exception | Error e) {
			pIsolateLogger.logSevere(this, "validate", "ERROR: %s", e);

		}
		pIsolateLogger.logInfo(this, "validate", "validated");
	}
}
