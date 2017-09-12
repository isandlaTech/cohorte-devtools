package qualif.cohorte.isolates.main.isolate.one.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.logging.Logger;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.osgi.framework.BundleContext;
import org.psem2m.isolates.base.IIsolateLoggerSvc;

import qualif.cohorte.isolates.main.isolate.one.IQualifierMainIsolateOne;
import qualif.cohorte.isolates.slf4j.tests.IOcilAdapterQualifier;

/**
 * This component manage the service ILoRaFeature which represents the general
 * availability of the LoRa Server and its satelites
 *
 * It prints a banner when it is validated.
 *
 * <pre>
 *    ____  __  _____    __    ________________________     ____           __      __          ____
 *   / __ \/ / / /   |  / /   /  _/ ____/  _/ ____/ __ \   /  _/________  / /___ _/ /____     / __ \____  ___
 *  / / / / / / / /| | / /    / // /_   / // __/ / /_/ /   / // ___/ __ \/ / __ `/ __/ _ \   / / / / __ \/ _ \
 * / /_/ / /_/ / ___ |/ /____/ // __/ _/ // /___/ _, _/  _/ /(__  ) /_/ / / /_/ / /_/  __/  / /_/ / / / /  __/
 * \___\_\____/_/  |_/_____/___/_/   /___/_____/_/ |_|  /___/____/\____/_/\__,_/\__/\___/   \____/_/ /_/\___/
 * 
 * http://patorjk.com/software/taag/#p=display&f=Slant&t=QUALIFIER%20Isolate%20One
 *
 * </pre>
 *
 * @author ogattaz
 *
 */
@Component(name = "qualifier-cohorte-isolates-main-isolate-one-factory")
@Provides(specifications = { IQualifierMainIsolateOne.class })
public class CCpntQualifierMainIsolateOne implements IQualifierMainIsolateOne {

	// eg. CCpntLoRaBootstrap-banner.txt
	private static final String BANNER_FILENAME_SUFFIX = "-banner.txt";

	private final String BANNER_LINE_FORMAT = " #   %s";

	// The url maybe like this: bundle://2.0:2/com/my/weager/impl/test.txt
	// But this url is not a real file path :-( , you could't use it as a
	// file.
	final URL pBannerBundleUrl;

	private final BundleContext pBundleContext;

	@Requires
	private IIsolateLoggerSvc pLogger;

	@Requires
	private IOcilAdapterQualifier pOcilAdapterQualifier;

	/**
	 * @param aBundleContext
	 */
	public CCpntQualifierMainIsolateOne(final BundleContext aBundleContext) {
		super();
		pBundleContext = aBundleContext;

		// the bundle URL of the banner.
		pBannerBundleUrl = buildBannerUrl();
	}

	/**
	 * @param aLine
	 * @return
	 * @throws Exception
	 */
	private String buildBannerLine() throws Exception {

		return buildBannerLine("");
	}

	/**
	 * @param aLine
	 * @return
	 * @throws Exception
	 */
	private String buildBannerLine(final String aLine) throws Exception {

		return String.format(BANNER_LINE_FORMAT, aLine);
	}

	/**
	 * @throws Exception
	 */
	private String buildBannerText(final URL wBannerFileUrl, final String aText)
			throws Exception {

		// This url should be handled by the specific
		// URLHandlersBundleStreamHandler, you can look up details in
		// BundleRevisionImpl.createURL(int port,String path)
		final BufferedReader br = new BufferedReader(new InputStreamReader(
				wBannerFileUrl.openConnection().getInputStream()));
		final StringBuilder wSB = new StringBuilder();
		// begin by a empty line !
		while (br.ready()) {
			wSB.append('\n');
			wSB.append(buildBannerLine(br.readLine()));
		}
		br.close();

		if (aText != null && !aText.isEmpty()) {
			wSB.append('\n').append(buildBannerLine());
			wSB.append('\n').append(buildBannerLine(aText));
		}
		return wSB.toString();
	}

	/**
	 * The url maybe like this: bundle://2.0:2/com/my/weager/impl/test.txt
	 *
	 * But this url is not a real file path :-(
	 *
	 * You could't use it as a the bundle URL of the banner.
	 *
	 * @return the URL off the ressource located in the bundle
	 */
	private URL buildBannerUrl() {

		final String wBannerFilePath = getClass().getName().replace('.', '/')
				+ BANNER_FILENAME_SUFFIX;

		// BannerFilePath=[com/cohorte/iot/lora/core/impl/CCpntLoRaBootstrap-banner.txt]
		pLogger.logInfo(this, "buildBannerUrl", "BannerFilePath=[%s]",
				wBannerFilePath);

		final URL wBannerFileUrl = pBundleContext.getBundle().getResource(
				wBannerFilePath);

		pLogger.logInfo(this, "buildBannerUrl", "BannerFileUrl=[%s]",
				(wBannerFileUrl != null) ? wBannerFileUrl.toExternalForm()
						: "null");

		return wBannerFileUrl;
	}

	/**
	 *
	 */
	@Invalidate
	void invalidate() {

		pLogger.logInfo(this, "invalidate", "invalidating...");

		String wBannerText = "CANT READ BANNER";
		try {

			wBannerText = buildBannerText(pBannerBundleUrl, "Stoping...");

			// print the banner using the Jul logger "root"
			Logger.getLogger("").info(wBannerText);

		} catch (Exception | Error e) {
			pLogger.logSevere(this, "invalidate", "ERROR: %s", e);
		}
		pLogger.logInfo(this, "invalidate", "invalidated\n%s", wBannerText);
	}

	/**
	 *
	 */
	@Validate
	void validate() {

		pLogger.logInfo(this, "validate", "validating...");

		String wBannerText = "CANT READ BANNER";
		try {

			wBannerText = buildBannerText(pBannerBundleUrl, "Available OK");

			// print the banner using the Jul logger "root"
			Logger.getLogger("").info(wBannerText);

		} catch (Exception | Error e) {
			pLogger.logSevere(this, "validate", "ERROR: %s", e);
		}
		pLogger.logInfo(this, "validate", "validated\n%s", wBannerText);
	}
}
