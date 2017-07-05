package main.java.com.cohorte.models.validator;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.psem2m.utilities.logging.CActivityLoggerBasicConsole;

/**
 * Goal which touches a timestamp file.
 *
 * @goal validator
 *
 * @phase process-sources
 */
public class CValidatorMojo extends AbstractMojo {

	/**
	 * cohorte base path.
	 *
	 * @parameter expression="${cohorte.base}"
	 * @required
	 */
	private String pCohorteBase;

	/**
	 * cohorte base path.
	 *
	 * @parameter expression="${cohorte.data}"
	 * @required
	 */
	private String pCohorteData;

	/**
	 * target directory path where the json fake are generated.
	 *
	 * @parameter expression="${target.dir}"
	 *            default-value="cohorte.base/generate"
	 * @required
	 */
	private String pPathTarget;

	/**
	 * prefix for json fake files
	 *
	 * @parameter expression="${prefix.json}" default-value="json_fake_"
	 * @required
	 */
	private String pPrefixJson;

	/**
	 * cohorte base path.
	 *
	 * @parameter expression="${prefix.module}" default-value="module_"
	 * @required
	 */
	private String pPrefixModule;

	/**
	 * tag to naalyse (default $include).
	 *
	 * @parameter expression="${tag}" default-value="$include"
	 * @required
	 */
	private String pTag;

	@Override
	public void execute() throws MojoExecutionException {
		try {
			CValidator wValidator = new CValidator(
					CActivityLoggerBasicConsole.getInstance(), pCohorteData,
					pCohorteBase, pTag, pPrefixModule, pPathTarget, pPrefixJson);

			wValidator.validate();
		} catch (Exception e) {
			throw new MojoExecutionException("validation failed ", e);
		}
	}
}
