package qualif.cohorte.isolates.main.isolate.one.impl;

import java.util.logging.Level;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.isolates.loggers.CLogChannelException;
import org.psem2m.isolates.loggers.ILogChannelSvc;
import org.psem2m.isolates.loggers.ILogChannelsSvc;
import org.psem2m.isolates.loggers.ILoggingConditions;
import org.psem2m.isolates.loggers.ILoggingConditionsManager;

/**
 * debugMe Response Filter.
 *
 * MOD_248 Associates a LogConditions to each LogChannels
 *
 * @author apisu
 *
 */

@Component(name = "qualifier-cohorte-isolates-loggers-Factory")
@Provides(specifications = { CCpntQualifierLoggers.class })
public class CCpntQualifierLoggers {

	private static String CHANNEL_ID = "QUALIF";

	private static String CONDITION_ONE_ID = "Condition-One";
	private static String CONDITION_SPECIFIC_ID = "Condition-Specific";

	private ILogChannelSvc pLogChannel;

	@Requires
	IIsolateLoggerSvc pLogger;

	@Requires
	ILogChannelsSvc pLoggerChannels;

	@Requires
	ILoggingConditions pLoggingConditionsDefault;
	@Requires
	ILoggingConditionsManager pLoggingConditionsManager;

	private ILoggingConditions pLoggingConditionsSpecific;

	/**
	 *
	 */
	@Invalidate
	public void invalidate() {
		pLogger.logInfo(this, "validate", "intvalidating...");
		try {

			testChanneInvalidatel();

		} catch (Exception | Error e) {
			pLogger.logSevere(this, "validate", "ERROR: %s", e);
		}
		pLogger.logInfo(this, "validate", "intvalidated");

	}

	/**
	 * @param aID
	 * @param aLoggingConditions
	 */
	private void logLoggingConditions(final String aID,
			final ILoggingConditions aLoggingConditions) {
		pLogChannel.logInfo(this, "logLoggingConditions",
				"LoggingCondition [%s] usage OK : %s ", aID,
				aLoggingConditions.toDescription());
	}

	/**
	 *
	 */
	private void logLoggingConditionsManager() {
		pLogChannel.logInfo(this, "logLoggingConditionsManager",
				"LoggingConditionsManager: %s",
				pLoggingConditionsManager.toDescription());
	}

	/**
	 * @throws CLogChannelException
	 */
	private void testChanneInvalidatel() throws CLogChannelException {

		pLogChannel.logInfo(this, "testChanneInvalidatel",
				"Channel usage OK (Level=[%s])", pLogChannel.getLevel()
						.getName());

		if (pLoggingConditionsDefault.isOn(CONDITION_ONE_ID, Level.INFO)) {

			logLoggingConditions(CONDITION_ONE_ID, pLoggingConditionsDefault);
		}

		if (pLoggingConditionsSpecific.isOn(CONDITION_SPECIFIC_ID, Level.INFO)) {

			logLoggingConditions(CONDITION_SPECIFIC_ID,
					pLoggingConditionsSpecific);
		}

	}

	/**
	 * @throws CLogChannelException
	 */
	private void testChannelValidate() throws CLogChannelException {

		// ------------------------------------------------------------------------------------------
		// channel
		// ------------------------------------------------------------------------------------------

		// get a log channel called CHANNEL_ID
		pLogChannel = pLoggerChannels.getLogChannel(CHANNEL_ID);

		// set its level => FINE
		pLogChannel.setLevel(Level.FINE);

		logLoggingConditionsManager();

		// ------------------------------------------------------------------------------------------
		// new logging condition "CONDITION_ONE_ID" in
		// "pLoggingConditionsDefault"
		// ------------------------------------------------------------------------------------------

		// use the log channel
		pLogChannel.logInfo(this, "testChannelValidate",
				"Channel [%s] usage OK (Level=[%s])", CHANNEL_ID, pLogChannel
						.getLevel().getName());

		// add the logging contiton CONDITION_ONE_ID in the DEFAULT
		// LoggingConditions
		pLoggingConditionsDefault.newLoggingCondition(CONDITION_ONE_ID,
				Level.INFO, "Logging condition %s", CONDITION_ONE_ID);

		pLogChannel.logInfo(this, "testChannelValidate",
				"LoggingCondition: add condition=[%s] in [%s]",
				CONDITION_ONE_ID, "pLoggingConditionsDefault");

		if (pLoggingConditionsDefault.isOn(CONDITION_ONE_ID, Level.INFO)) {

			logLoggingConditions(CONDITION_ONE_ID, pLoggingConditionsDefault);
		}

		logLoggingConditionsManager();

		// ------------------------------------------------------------------------------------------
		// new logging condition "CONDITION_SPECIFIC_ID" in
		// "pLoggingConditionsSpecific"
		// ------------------------------------------------------------------------------------------

		// new Logging conditions associated to the channel
		pLoggingConditionsSpecific = pLoggingConditionsManager
				.newLoggingConditions(CHANNEL_ID);

		pLoggingConditionsSpecific.newLoggingCondition(CONDITION_SPECIFIC_ID,
				Level.FINE, "Logging condition %s", CONDITION_SPECIFIC_ID);

		pLogChannel.logInfo(this, "testChannelValidate",
				"LoggingCondition: add condition=[%s] in [%s]",
				CONDITION_SPECIFIC_ID, "pLoggingConditionsSpecific");

		if (pLoggingConditionsSpecific.isOn(CONDITION_SPECIFIC_ID, Level.INFO)) {

			logLoggingConditions(CONDITION_SPECIFIC_ID,
					pLoggingConditionsSpecific);

		}

		logLoggingConditionsManager();

	}

	/**
	 *
	 */
	@Validate
	public void validate() {
		pLogger.logInfo(this, "validate", "validating...");

		try {
			pLogger.logInfo(this, "validate", "LoggerChannels.Ids=[%s]",
					pLoggerChannels.getChannelsIds());
			pLogger.logInfo(this, "validate", "LoggingConditionsManager=[%s]",
					pLoggingConditionsManager.toDescription());

			testChannelValidate();

		} catch (Exception | Error e) {
			pLogger.logSevere(this, "validate", "ERROR: %s", e);
		}

		pLogger.logInfo(this, "validate", "valdated");
	}

}
