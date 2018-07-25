package org.cohorte.eclipse.runner.basic.jython;

import org.osgi.framework.Bundle;
import org.psem2m.utilities.logging.IActivityLogger;

/**
 * @see https 
 *      ://stackoverflow.com/questions/31001850/passing-osgi-bundles-for-jython
 *      -interpreter-on-the-fly add jython class loader in order to pass it on
 *      the interpreter t resolve stdlib on OSGI
 * @author apisu
 *
 */
public class JythonClassLoader extends ClassLoader {

	private final Bundle pBundle;
	private final IActivityLogger pLogger;

	public JythonClassLoader(final IActivityLogger aLogger,
			final ClassLoader aParent, final Bundle aBundle) {
		super(aParent);
		pBundle = aBundle;
		pLogger = aLogger;
	}

	/*
	 * lookin for class on the bundle class loader
	 */
	@Override
	protected Class<?> findClass(final String aName)
			throws ClassNotFoundException {
		pLogger.logDebug(this, "findClass", "find class name %s", aName);
		return pBundle.loadClass(aName);

	}
}
