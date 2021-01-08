package org.cohorte.eclipse.runner.basic;

import org.osgi.framework.Bundle;
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

		// MOD_OG_20160906 Basic Runner log enhancement
		final Bundle wBundle = bundleContext.getBundle();
		System.out.printf("%50s | Bundle=[%50s][%s] started\n",
				"Activator.start()", wBundle.getSymbolicName(),
				wBundle.getVersion());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(final BundleContext bundleContext) throws Exception {

		// MOD_OG_20160906 Basic Runner log enhancement
		final Bundle wBundle = bundleContext.getBundle();
		System.out.printf("%50s | Bundle=[%50s] stopped\n", "Activator.stop()",
				wBundle.getSymbolicName());

		super.stop(bundleContext);

	}
}
