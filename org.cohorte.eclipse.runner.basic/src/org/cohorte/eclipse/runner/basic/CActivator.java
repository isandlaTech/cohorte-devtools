package org.cohorte.eclipse.runner.basic;

import org.osgi.framework.BundleContext;
import org.psem2m.isolates.base.activators.CActivatorBase;

/**
 * 
 * @author ogattaz
 *
 */
public class CActivator extends CActivatorBase {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	@Override
	public void start(final BundleContext bundleContext) throws Exception {

		super.start(bundleContext);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(final BundleContext bundleContext) throws Exception {

		super.stop(bundleContext);

	}
}
