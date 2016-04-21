package org.cohorte.eclipse.runner.basic;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.ServiceProperty;
import org.cohorte.herald.HeraldException;
import org.cohorte.herald.IHerald;
import org.cohorte.herald.IMessageListener;
import org.cohorte.herald.Message;
import org.cohorte.herald.MessageReceived;
import org.cohorte.herald.NoTransport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.psem2m.isolates.base.IIsolateLoggerSvc;

/**
 * Gogo command to stop all Isolate on Eclipse developpement environement.
 *
 * @author bdebbabi
 *
 */
@Component
@Instantiate
@Provides(specifications = { CShutdownGogoCommand.class, IMessageListener.class })
public class CShutdownGogoCommand implements IConstants, IMessageListener {

	/**
	 * OSGi Bundle Context
	 */
	BundleContext pBundleContext;

	/** The Gogo commands */
	@ServiceProperty(name = "osgi.command.function", value = "{shutdown}")
	private String[] pCommands;

	/** Herald message filters */
	@ServiceProperty(name = org.cohorte.herald.IConstants.PROP_FILTERS, value = "{"
			+ IConstants.SHUTDOWN_MESSAGE + "}")
	private String[] pFilters;

	@Requires
	private IHerald pHerald;

	/**
	 * Cohorte Logger service
	 */
	@Requires
	private IIsolateLoggerSvc pLogger;
	/**
	 * The Gogo commands scope
	 */
	@ServiceProperty(name = "osgi.command.scope", value = "runner")
	private String pScope;

	/**
	 * Constructor
	 *
	 * @param aBundleContext
	 */
	public CShutdownGogoCommand(final BundleContext aBundleContext) {
		pBundleContext = aBundleContext;
	}

	/**
	 * Stops bundle 0 (OSGi framework).
	 */
	private void doShutdown() {
		pLogger.logInfo(this, "doShutdown", "trying to stop bundle 0...");
		try {
			pBundleContext.getBundle(0).stop();
			pLogger.logInfo(this, "doShutdown", "bundle 0 stopped");
		} catch (BundleException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void heraldMessage(final IHerald aHerald,
			final MessageReceived aMessage) throws HeraldException {
		pLogger.logInfo(this, "heraldMessage", "receiving a message...");
		String wSubject = aMessage.getSubject();
		pLogger.logInfo(this, "heraldMessage", "subject=" + "wSubject");
		if (wSubject.equalsIgnoreCase(SHUTDOWN_MESSAGE)) {
			doShutdown();
		}
	}

	/**
	 * Shutdown Gogo command.
	 */
	public void shutdown() {
		// dispatch SHUTDOWN message to all other isolates
		pLogger.logInfo(this, "shutdown",
				"sending SHUTDOWN message to all isolates");
		try {
			pHerald.fireGroup("all", new Message(SHUTDOWN_MESSAGE));
		} catch (NoTransport e) {
			e.printStackTrace();
		}
		// stop actual isolate
		try {
			// wait the broadcast message to be sent
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		doShutdown();
	}
}