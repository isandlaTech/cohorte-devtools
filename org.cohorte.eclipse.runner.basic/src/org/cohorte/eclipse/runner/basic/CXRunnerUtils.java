package org.cohorte.eclipse.runner.basic;

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.psem2m.utilities.CXException;
import org.psem2m.utilities.CXStringUtils;

/**
 * @author ogattaz
 *
 */
public class CXRunnerUtils {

	/**
	 * @param aChar
	 *            the char used for the lines
	 * @param aInterline
	 *            add a line between the lines of the text
	 * @param aText
	 *            the text of the banner
	 * @return
	 */
	public static String buildBanner(final char aChar,
			final boolean aInterline, final String aText) {
		final StringBuilder wSB = new StringBuilder();
		final String wLineSep = CXStringUtils.strFromChar(aChar, 140);
		wSB.append('\n');
		wSB.append('\n').append(wLineSep);
		if (aText != null) {
			for (final String wLine : aText.split("\n")) {
				if (aInterline) {
					wSB.append('\n').append(aChar);
				}
				wSB.append('\n').append(aChar).append(' ').append(wLine);
			}
		}
		if (aInterline) {
			wSB.append('\n').append(aChar);
		}
		wSB.append('\n').append(wLineSep);
		wSB.append('\n');
		return wSB.toString();
	}

	/**
	 * @param aBundleContext
	 * @param aMessage
	 */
	public static void stopIsolate(final BundleContext aBundleContext,
			final String aMessage) {

		System.err.println(CXRunnerUtils.buildBanner('#', false, aMessage));

		try {
			aBundleContext.getBundle(0).stop();
		} catch (BundleException e) {
			System.err.println(CXException.eInString(e));
		}
	}

}
