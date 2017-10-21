package qualif.cohorte.isolates.main.isolate.one.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.psem2m.isolates.base.IIsolateLoggerSvc;
import org.psem2m.isolates.services.dirs.IPlatformDirsSvc;
import org.psem2m.utilities.CXException;
import org.psem2m.utilities.CXStringUtils;
import org.psem2m.utilities.CXTimer;
import org.psem2m.utilities.files.CXFileUtf8;
import org.psem2m.utilities.json.JSONArray;
import org.psem2m.utilities.json.JSONException;
import org.psem2m.utilities.json.JSONObject;
import org.wololo.jts2geojson.GeoJSONReader;
import org.wololo.jts2geojson.GeoJSONWriter;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.PrecisionModel;

/**
 *
 * This component validates the geograpic toolset available in the bundle
 * "org.cohorte.libs.jts-all"
 *
 *
 * This component executes 3 tests when it is validated.
 *
 * To be able to execute the tests (union of polygon representing departements),
 * the component loads the definitions of the departements.
 *
 *
 * Summary of the trace of the loading of the file departements_definitions.json
 *
 * <pre>
 *   DepartementsPath=[data-isolate-one/geographic/departements_definitions.json]
 *   JsonLength=[1076794]
 *   NbDeps=[101] Duration=[48,194]
 * </pre>
 *
 * Summary of the trace of the construction of the map containing the 'geometry'
 * of each departement
 *
 * <pre>
 *  Begin
 *  Dep=[01] geometry.type=[     Polygon] coordinates.NbPoints=[482]
 *  ...
 *  Dep=[85] geometry.type=[MultiPolygon] coordinates.NbPoints=[,28,509,51]
 * ...
 *  Dep=[95] geometry.type=[     Polygon] coordinates.NbPoints=[,258]
 *  End. DepGeometriesMap.size=[95]  Duration=[3255,541]
 * </pre>
 *
 * Summary of the trace of the 3 tests :
 *
 * <pre>
 *  Label=[Savoies] Deps:73,74
 *  Region=[             Savoies] Dep=[73] GeoJson Found=[true]
 *  Region=[             Savoies] Dep=[73] NbSubGeometrie=[1] POLYGON ((6.802515 45.778372, ... , 6.802515 45.778372))
 *  Region=[             Savoies] Dep=[74] GeoJson Found=[true]
 *  Region=[             Savoies] Dep=[74] NbSubGeometrie=[1] POLYGON ((6.802515 45.778372, ... , 6.802515 45.778372))
 *  Region=[             Savoies] Geometries.size=[ 2]
 *  Region=[             Savoies] wNbCoordilnates=[613] GeoJsonUnion.size=[12783] Duration=[301,809]
 *  GeoJsonStr.length=[12783]
 *  GeoJsonStr: {"type":"Polygon","coordinates":[[[6.802515,45.778372], ... ,[6.802515,45.778372]]]}
 *  Duration=[254,658]
 *  Label=[Paca] Deps:13,83,84,06
 *  Region=[                Paca] Dep=[13] GeoJson Found=[true]
 *  Region=[                Paca] Dep=[13] NbSubGeometrie=[1] POLYGON ((4.73906 43.924062, ... , 5.017834 43.469321))
 *  Region=[                Paca] Dep=[83] GeoJson Found=[true]
 *  Region=[                Paca] Dep=[83] NbSubGeometrie=[4] MULTIPOLYGON (((6.434149 43.013347, ... , 5.753645 43.72462)))
 *  Region=[                Paca] Dep=[84] GeoJson Found=[true]
 *  Region=[                Paca] Dep=[84] NbSubGeometrie=[2] MULTIPOLYGON (((4.888121 44.331685, ... , 5.498786 44.115717)))
 *  Region=[                Paca] Dep=[06] GeoJson Found=[true]
 *  Region=[                Paca] Dep=[06] NbSubGeometrie=[2] MULTIPOLYGON (((7.067118 43.513649, ... , 6.887435 44.361051)))
 *  Region=[                Paca] Geometries.size=[ 9]
 *  Region=[                Paca] wNbCoordilnates=[1485] GeoJsonUnion.size=[30918] Duration=[53,198]
 *  GeoJsonStr.length=[30918]
 *  GeoJsonStr: {"type":"MultiPolygon","coordinates":[[[[7.067118,43.513649], ... ,[4.888121,44.331685]]]]}
 *  Duration=[51,999]
 *  Label=[Corsica] Deps:2A,2B
 *  Region=[             Corsica] Dep=[2A] GeoJson Found=[true]
 *  Region=[             Corsica] Dep=[2A] NbSubGeometrie=[2] MULTIPOLYGON (((9.271032 41.364959, ... , 8.573409 42.381405)))
 *  Region=[             Corsica] Dep=[2B] GeoJson Found=[true]
 *  Region=[             Corsica] Dep=[2B] NbSubGeometrie=[1] POLYGON ((9.402271 41.858702, ... , 9.402271 41.858702))
 *  Region=[             Corsica] Geometries.size=[ 3]
 *  Region=[             Corsica] wNbCoordilnates=[827] GeoJsonUnion.size=[17220] Duration=[20,216]
 *  GeoJsonStr.length=[17220]
 *  GeoJsonStr:{"type":"MultiPolygon","coordinates":[[[[9.271032,41.364959], ... ,[9.402271,41.858702]]]]}
 *  Duration=[22,428]
 *  valdated
 * </pre>
 *
 * @author ogattaz
 *
 */
@Component(name = "qualifier-cohorte-isolates-geographic-Factory")
@Provides(specifications = { CCpntQualifierGeographic.class })
public class CCpntQualifierGeographic {

	static final String DIR_DATA_GEOGRAPHIC = "geographic";
	static final String DIR_DATA_ISOLATE_ONE = "data-isolate-one";

	// available in .../cohorte-data/data-isolate-one/geographic/definitions/
	static final String FILENAME_DEPARTEMENTS_DEFS = "departements_definitions.json";

	private JSONObject pDepartementDefs;

	final Map<String, JSONObject> pDepGeometriesMap = new TreeMap<String, JSONObject>();

	@Requires
	private IIsolateLoggerSvc pLogger;

	@Requires
	IPlatformDirsSvc pPlatformDirs;

	/**
	 *
	 */
	public CCpntQualifierGeographic() {
		super();
	}

	/**
	 *
	 *
	 * generate a the polygon or a multi-plygon representing the geographical
	 * limits of an organisation.
	 *
	 *
	 * Appends all the polygon(s) or a multi-plygon(s) representing each
	 * departement of the list of departement of an organisation.
	 *
	 * Returns the geometry as a GeoJson flow.
	 *
	 * <pre>
	 * {
	 *    "geometry":{
	 *       "type":"Polygon",
	 *       "coordinates":[
	 *          [
	 *             [
	 *                7.525634765625,
	 *                47.83897065647554
	 *             ],
	 *             [
	 *                7.525634765625,
	 *                47.83897065647554
	 *             ]
	 *          ]
	 *       ]
	 *    }
	 * }
	 * </pre>
	 *
	 *
	 * @param aMeteoOrgDef
	 * @return a GeoJson flow (String)
	 * @throws CMeteoException
	 */
	private String calcGeometry(final String aRegionLbl,
			final JSONArray aDepArray) throws Exception {

		final CXTimer wTimer = CXTimer.newStartedTimer();

		final List<Geometry> wGeometries = new ArrayList<Geometry>();

		final GeoJSONReader reader = new GeoJSONReader();

		final List<String> wDepartements = aDepArray.getEntries(String.class);

		for (final String wDepId : wDepartements) {

			final JSONObject wGeoJsonPolygon = pDepGeometriesMap.get(wDepId);
			final boolean wFound = wGeoJsonPolygon != null;

			pLogger.logInfo(this, "calcGeometry",
					"Region=[%20s] Dep=[%2s] GeoJson Found=[%b]", aRegionLbl,
					wDepId, wFound);

			final Geometry wGeometry = reader.read(wGeoJsonPolygon.toString());

			final int wNbSubGeometries = wGeometry.getNumGeometries();

			pLogger.logInfo(this, "calcGeometry",
					"Region=[%20s] Dep=[%2s] NbSubGeometrie=[%d] %s",
					aRegionLbl, wDepId, wNbSubGeometries, wGeometry);

			if (wNbSubGeometries > 1) {
				for (int wKdx = 0; wKdx < wNbSubGeometries; wKdx++) {
					wGeometries.add(wGeometry.getGeometryN(wKdx));
				}
			} else {
				wGeometries.add(wGeometry);
			}
		}
		pLogger.logInfo(this, "calcGeometry",
				"Region=[%20s] Geometries.size=[%2d]", aRegionLbl,
				wGeometries.size());

		final GeometryFactory wGeometryFactory = new GeometryFactory(
				new PrecisionModel(PrecisionModel.FLOATING));

		// Build an appropriate Geometry, MultiGeometry, or
		// GeometryCollection to contain the Geometrys in it.
		final Geometry wUnion = wGeometryFactory.buildGeometry(wGeometries)
				.union();

		final GeoJSONWriter wWriter = new GeoJSONWriter();

		final int wNbCoordilnates = wUnion.getCoordinates().length;

		final String wGeoJsonUnion = wWriter.write(wUnion).toString();

		final int wGeoJsonSize = wGeoJsonUnion.length();

		pLogger.logInfo(
				this,
				"doCommandGenRegionGeometry",
				"Region=[%20s] wNbCoordilnates=[%d] GeoJsonUnion.size=[%d] Duration=[%s]",
				aRegionLbl, wNbCoordilnates, wGeoJsonSize,
				wTimer.getDurationStrMicroSec());

		return wGeoJsonUnion;
	}

	/**
	 * @throws Exception
	 */
	private JSONObject loadDepDefs() throws Exception {

		final CXTimer wTimer = CXTimer.newStartedTimer();

		final String wDepartementsPath = DIR_DATA_ISOLATE_ONE
				+ File.separatorChar + DIR_DATA_GEOGRAPHIC + File.separatorChar
				+ FILENAME_DEPARTEMENTS_DEFS;

		pLogger.logInfo(this, "loadDepDefs", " DepartementsPath=[%s]",
				wDepartementsPath);

		final CXFileUtf8 wDepartementsDefsFile = new CXFileUtf8(
				pPlatformDirs.getNodeDataDir(), wDepartementsPath);

		if (!wDepartementsDefsFile.isFile()) {
			throw new CXException(
					"Unable to find the departements definitions file [%s]",
					wDepartementsDefsFile);
		}

		final String wDepartementsDefsJson = wDepartementsDefsFile.readAll();

		final int wJsonLength = (wDepartementsDefsJson != null) ? wDepartementsDefsJson
				.length() : -1;

		pLogger.logInfo(this, "loadDepDefs", " JsonLength=[%d]", wJsonLength);

		JSONObject wDepartementsDef;

		try {
			wDepartementsDef = new JSONObject(wDepartementsDefsJson);
		} catch (final Exception e) {
			throw new CXException(
					e,
					"Unable to load the departements definitions from file [%s]",
					wDepartementsDefsFile);
		}

		final JSONArray wDeps = wDepartementsDef.optJSONArray("departements");

		final int wNbDeps = (wDeps != null) ? wDeps.length() : -1;

		pLogger.logInfo(this, "loadDepDefs", " NbDeps=[%d] Duration=[%s]",
				wNbDeps, wTimer.getDurationStrMicroSec());

		if (wNbDeps < 0) {
			throw new CXException(
					"The loaded departements definitions doesn't contain a 'departements' array");
		}

		return wDepartementsDef;
	}

	/**
	 * Populate the map "pDepartementGeometrys" using the content of the given
	 * Json object aDepartementDefs
	 *
	 * The geometry attributes contains a GeoJson string containing a Polygon or
	 * a MultiPolygon
	 *
	 * <pre>
	 * {
	 * "departements": [
	 *   {
	 *     "prefecture": "Bourg-en-Bresse",
	 *     "org_id": {"ref": "Sud-Est_82"},
	 *     "lbl": "Ain",
	 *     "geometry": "{\"coordinates\":[[[4.780208,46.176676],...,[4.780208,46.176676]]],\"type\":\"Polygon\"}",
	 *     "id": "01"
	 *   },
	 *   ...
	 *   {
	 *     "prefecture":"Avignon",
	 *     "org_id":{"ref":"Sud-Est_93"},
	 *     "lbl":"Vaucluse",
	 *     "geometry":"{\"coordinates\":[[ [[4.888121,44.331685], ... ,[4.888121,44.331685]] ],[ [[5.498786,44.115717], ... ,[5.498786,44.115717]]]],\"type\":\"MultiPolygon\"}",
	 *     "id":"84"
	 *   }
	 *   ]
	 * }
	 * </pre>
	 *
	 * @param aCmdeLine
	 * @throws Exception
	 */
	private void loadDepGeometries(final JSONObject aDepartementDefs)
			throws Exception {

		final CXTimer wTimer = CXTimer.newStartedTimer();

		pLogger.logInfo(this, "loadDepGeometries", "Begin");

		final JSONArray wDepDefsArray = aDepartementDefs
				.optJSONArray("departements");

		if (wDepDefsArray == null) {
			throw new CXException(
					"Unable to retreive the array of the definitions of the departements called [departements] ");
		}

		String wDepId = null;

		final int wMax = wDepDefsArray.length();

		for (int wIdx = 0; wIdx < wMax; wIdx++) {

			try {
				final JSONObject wDepDef = wDepDefsArray.optJSONObject(wIdx);

				// get the String in the property "id"
				wDepId = wDepDef.getString("id");

				// get the String in the optional property "geometry"
				final String wGeometryStr = wDepDef.optString("geometry");

				if (wGeometryStr != null && !wGeometryStr.isEmpty()) {

					final JSONObject wGeometryJson = new JSONObject(
							wGeometryStr);

					// put new entry using the ID of the departement as the
					// key
					pDepGeometriesMap.put(wDepId, wGeometryJson);

					// get geometry type : "Polygon" or "MultiPolygon"
					final String wType = wGeometryJson.getString("type");

					// dump the nb of points of each polygon of the shape
					final StringBuilder wSB = new StringBuilder();
					final JSONArray wJA0 = wGeometryJson
							.getJSONArray("coordinates");
					final int wNbPolygon = wJA0.length();
					for (int wJdx = 0; wJdx < wNbPolygon; wJdx++) {
						JSONArray wJA1 = wJA0.getJSONArray(wJdx);
						if ("MultiPolygon".equals(wType)) {
							wJA1 = wJA1.getJSONArray(0);
						}
						if (wIdx > 0) {
							wSB.append(',');
						}
						wSB.append(wJA1.length());
					}

					pLogger.logInfo(
							this,
							"loadDepGeometries",
							"Dep=[%s] geometry.type=[%12s] coordinates.NbPoints=[%s]",
							wDepId, wType, wSB);
				}
			} catch (final JSONException e) {
				throw new CXException(
						e,
						"Unable to load the geometry of the departement [%s] (cycle %d)",
						wDepId, wIdx);
			}
		}

		pLogger.logInfo(this, "loadDepGeometries",
				"End. DepGeometriesMap.size=[%d]  Duration=[%s]",
				pDepGeometriesMap.size(), wTimer.getDurationStrMicroSec());
	}

	/**
	 * @param alabel
	 * @param aDepArray
	 * @throws Exception
	 */
	private void testCalcGeometry(final String alabel, final JSONArray aDepArray)
			throws Exception {

		final CXTimer wTimer = CXTimer.newStartedTimer();

		pLogger.logInfo(this, "testCalcGeometry", "Label=[%s] Deps:%s", alabel,
				CXStringUtils.stringListToString(aDepArray
						.getEntries(String.class)));

		final String wGeoJsonStr = calcGeometry(alabel, aDepArray);

		pLogger.logInfo(this, "testCalcGeometry", "GeoJsonStr.length=[%d]",
				wGeoJsonStr.length());
		pLogger.logInfo(this, "testCalcGeometry", "GeoJsonStr: %s", wGeoJsonStr);

		pLogger.logInfo(this, "testCalcGeometry", "Duration=[%s]",
				wTimer.getDurationStrMicroSec());

	}

	/**
	 * Summary of the trace of the 3 tests :
	 *
	 *
	 */
	@Validate
	public void validate() {
		pLogger.logInfo(this, "validate", "validating...");

		try {

			/**
			 * load the content of the definition of the departements
			 *
			 * <pre>
			 *   DepartementsPath=[data-isolate-one/geographic/departements_definitions.json]
			 *   JsonLength=[1076794]
			 *   NbDeps=[101] Duration=[48,194]
			 * </pre>
			 */
			pDepartementDefs = loadDepDefs();

			/**
			 * build a map containing the 'geometry' of each departement
			 *
			 * <pre>
			 *  Begin
			 *  Dep=[01] geometry.type=[     Polygon] coordinates.NbPoints=[482]
			 *  ...
			 *  Dep=[85] geometry.type=[MultiPolygon] coordinates.NbPoints=[,28,509,51]
			 * ...
			 *  Dep=[95] geometry.type=[     Polygon] coordinates.NbPoints=[,258]
			 *  End. DepGeometriesMap.size=[95]  Duration=[3255,541]
			 * </pre>
			 */
			loadDepGeometries(pDepartementDefs);

			// test 1 Savoies

			testCalcGeometry("Savoies", new JSONArray(
					new String[] { "73", "74" }));

			// test 2 Paca
			testCalcGeometry("Paca", new JSONArray(new String[] { "13", "83",
					"84", "06" }));

			// test 3 Corsica
			testCalcGeometry("Corsica", new JSONArray(
					new String[] { "2A", "2B" }));

		} catch (Exception | Error e) {
			pLogger.logSevere(this, "validate", "ERROR: %s", e);
		}

		pLogger.logInfo(this, "validate", "valdated");
	}

}
