package org.cohorte.eclipse.runner.basic;

import java.util.HashMap;
import java.util.Map;

import org.apache.felix.ipojo.Factory;
import org.osgi.framework.ServiceReference;

/**
 * @author ogattaz
 *
 */
public class CFactoryInfos {

	private ServiceReference<Factory> pFactoryServiceRef = null;

	private final String pName;

	// MOD_OG_20150417
	private boolean pNeeded = false;

	/**
	 * @param oName
	 *            The name of the factory
	 */
	CFactoryInfos(final String oName) {
		super();
		pName = oName;
	}

	/**
	 * @return The service Factory or null if no FactoryServiceRef is available
	 */
	public Factory getFactory() {

		ServiceReference<Factory> wFactoryServiceRef = getFactoryServiceRef();
		if (wFactoryServiceRef == null) {
			return null;
		} else {
			return pFactoryServiceRef.getBundle().getBundleContext()
					.getService(pFactoryServiceRef);
		}
	}

	/**
	 * A ServiceReference object may be shared between bundles and can be used
	 * to examine the properties of the service and to get the service object.
	 *
	 * @return
	 *
	 * @see https 
	 *      ://osgi.org/javadoc/r4v43/core/org/osgi/framework/ServiceReference
	 *      .html
	 */
	public synchronized ServiceReference<Factory> getFactoryServiceRef() {
		return pFactoryServiceRef;
	}

	/**
	 * @return The name of the factory.
	 */
	public String getName() {
		return pName;
	}

	/**
	 * @return A map containing the properties of the service or an empty map if
	 *         no FactoryServiceRef is available
	 */
	public Map<String, Object> getProperties() {

		Map<String, Object> wProperties = new HashMap<String, Object>();

		ServiceReference<Factory> wFactoryServiceRef = getFactoryServiceRef();
		if (wFactoryServiceRef != null) {
			for (String wKey : wFactoryServiceRef.getPropertyKeys()) {
				wProperties.put(wKey, wFactoryServiceRef.getProperty(wKey));
			}
		}
		return wProperties;
	}

	/**
	 * @return True if a FactoryServiceRef is available
	 */
	public synchronized boolean hasFactoryServiceRef() {
		return pFactoryServiceRef != null;
	}

	/**
	 * MOD_OG_20150417
	 *
	 * @return
	 */
	boolean isNeeded() {
		return pNeeded;
	}

	/**
	 * @param the
	 *            FactoryServiceRef of the service which has the right factory
	 *            name
	 */
	synchronized void setFactoryServiceRef(
			final ServiceReference<Factory> aFactoryServiceRef) {
		pFactoryServiceRef = aFactoryServiceRef;
	}

	/**
	 * MOD_OG_20150417
	 *
	 * @param aNeeded
	 */
	void setNeeded(final boolean aNeeded) {
		pNeeded = aNeeded;

	}
}
