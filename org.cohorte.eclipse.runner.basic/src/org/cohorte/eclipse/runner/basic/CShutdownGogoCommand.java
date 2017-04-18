package org.cohorte.eclipse.runner.basic;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.ServiceProperty;
import org.apache.felix.ipojo.annotations.Validate;
import org.cohorte.herald.HeraldException;
import org.cohorte.herald.IHerald;
import org.cohorte.herald.IMessageListener;
import org.cohorte.herald.Message;
import org.cohorte.herald.MessageReceived;
import org.cohorte.herald.NoTransport;
import org.cohorte.remote.IRemoteServicesConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.utilities.CXStringUtils;

/**
 * Gogo command to stop all Isolate on Eclipse developpement environement.
 *
 * MOD_OG_20170418 add event handler to allow the programmatic usage of the
 * shutdown command in the current isolate as the herald messages are not
 * delivred to their emmiter
 *
 * @author bdebbabi
 * @author ogattaz
 *
 */
@Component
@Instantiate
@Provides(specifications = { CShutdownGogoCommand.class,
		IMessageListener.class, EventHandler.class })
public class CShutdownGogoCommand implements IConstants, IMessageListener,
		EventHandler {

	/**
	 * OSGi Bundle Context
	 */
	BundleContext pBundleContext;

	/** The Gogo commands */
	@ServiceProperty(name = "osgi.command.function", value = "{shutdown}")
	private String[] pCommands;

	/** Herald message filters */
	@ServiceProperty(name = org.cohorte.herald.IConstants.PROP_FILTERS, value = "{"
			+ RUNNER_BASIC_SHUTDOWN_MESSAGE + "}")
	private String[] pFilters;

	@Requires
	private IHerald pHerald;
	/**
	 * Cohorte Logger service
	 */
	@Requires
	private IIsolateLoggerSvc pLogger;

	/**
	 * MOD_OG_20170418 The "pelix.remote.export.reject" property to limit the
	 * remote export of the services
	 */
	@ServiceProperty(name = IRemoteServicesConstants.PROP_EXPORT_REJECT, immutable = true)
	private final String[] pNotRemote = { IMessageListener.class.getName(),
			EventHandler.class.getName() };

	/**
	 * The Gogo commands scope
	 */
	@ServiceProperty(name = "osgi.command.scope", value = "runner")
	private String pScope;

	// MOD_OG_20170418 an array !
	@ServiceProperty(name = EventConstants.EVENT_TOPIC, value = "{"
			+ RUNNER_BASIC_SHUTDOWN_TOPIC + "}")
	private String[] pTopics;

	/**
	 * Constructor
	 *
	 * @param aBundleContext
	 */
	public CShutdownGogoCommand(final BundleContext aBundleContext) {
		pBundleContext = aBundleContext;

		// System.out.printf("devtool-basic-runner: %50s | instanciated \n",
		// this.getClass().getName());
	}

	/**
	 * Stops bundle 0 (OSGi framework).
	 */
	private void doShutdown() {
		pLogger.logInfo(this, "doShutdown",
				"trying to stop bundle 0 after 2 second...");
		// stop actual isolate
		try {
			// wait the broadcast message to be sent
			Thread.sleep(2000);
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		try {
			pBundleContext.getBundle(0).stop();
			pLogger.logInfo(this, "doShutdown", "bundle 0 stopped");
		} catch (final BundleException e) {
			e.printStackTrace();
		}
	}

	/**
	 * MOD_OG_20170418
	 *
	 * @see org.osgi.service.event.EventHandler#handleEvent(org.osgi.service.event
	 *      .Event)
	 */
	@Override
	public void handleEvent(Event aEvent) {

		pLogger.logInfo(this, "handleEvent", "receiving a event...");

		try {

			String wEventTopic = aEvent.getTopic();

			pLogger.logInfo(this, "handleEvent", "Event: %s", aEvent);

			if (RUNNER_BASIC_SHUTDOWN_TOPIC.equalsIgnoreCase(wEventTopic)) {
				doShutdown();
			}

		} catch (Exception e) {

			if (pLogger == null) {
				e.printStackTrace();
			}
			//
			else {
				pLogger.logSevere(this, "handleEvent", "ERROR: %s", e);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.cohorte.herald.IMessageListener#heraldMessage(org.cohorte.herald.
	 * IHerald, org.cohorte.herald.MessageReceived)
	 */
	@Override
	public void heraldMessage(final IHerald aHerald,
			final MessageReceived aMessage) throws HeraldException {

		pLogger.logInfo(this, "heraldMessage", "receiving a Herald message...");
		final String wSubject = aMessage.getSubject();
		pLogger.logInfo(this, "heraldMessage", "subject=" + "wSubject");
		if (RUNNER_BASIC_SHUTDOWN_MESSAGE.equalsIgnoreCase(wSubject)) {
			doShutdown();
		}
	}

	/**
	 * MOD_OG_20160905 Log traces enhancement
	 */
	@Invalidate
	public void invalidate() {

		pLogger.logInfo(this, "invalidate", "invalidated");

	}

	/**
	 * Shutdown Gogo command.
	 */
	public void shutdown() {

		pLogger.logInfo(this, "shutdown",
				"Fire SHUTDOWN message to all isolates");
		try {
			pHerald.fireGroup("all", new Message(RUNNER_BASIC_SHUTDOWN_MESSAGE));
		} catch (final NoTransport e) {
			e.printStackTrace();
		}
		// as the method fireGroup send the message to the other but not at the
		// current isolate !
		doShutdown();
	}

	/**
	 * MOD_OG_20160905 Log traces enhancement
	 */
	@Validate
	public void validate() {

		pLogger.logInfo(this, "validate", "Validating...");

		pLogger.logInfo(this, "validate", "Commands=[%s]",
				CXStringUtils.stringTableToString(pCommands));

		pLogger.logInfo(this, "validate", "Validated");

	}

}
