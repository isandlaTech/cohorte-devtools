package qualif.cohorte.isolates.slf4j.tests;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.psem2m.isolates.base.activators.CActivatorBase;

/**
 * @author ogattaz
 *
 */
public class Activator extends CActivatorBase {

	/** first instance **/
	private static Activator sMe = null;

	/**
	 * @return
	 */
	public static Activator getInstance() {

		return sMe;
	}

	public Activator() {
		super();
		sMe = this;
	}

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

		super.stop(bundleContext);

		System.out.printf("%50s | Bundle=[%50s] stopped\n", "Activator.stop()",
				bundleContext.getBundle().getSymbolicName());

	}

}
