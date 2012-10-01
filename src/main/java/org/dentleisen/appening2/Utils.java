package org.dentleisen.appening2;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.acl.GroupGrantee;
import org.jets3t.service.acl.Permission;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.mchange.v2.c3p0.ComboPooledDataSource;

public class Utils {
	private static Logger log = Logger.getLogger(Utils.class);

	// has to correspond to value in mysql stored
	// procedures, so DO NOT CHANGE
	public static final double messageThreshold = 13;

	private static Properties configuration = new Properties();
	static {
		try {
			configuration.load(Utils.class.getClassLoader()
					.getResourceAsStream("appening.properties"));
		} catch (IOException e) {
			log.warn("Unable to find properties in Classpath!");
		}
	}

	private static ComboPooledDataSource cpds = null;

	public static String getCfgStr(String key) {
		if (!configuration.containsKey(key)) {
			log.warn("Unable to find key '" + key + "' in configuration file!");
			return "";
		}
		return (String) configuration.getProperty(key);
	}

	public static long getCfgInt(String key) {
		return Long.parseLong(getCfgStr(key));
	}

	public static double getCfgDbl(String key) {
		return Double.parseDouble(getCfgStr(key));
	}

	public synchronized static Connection getConnection() {
		try {
			if (cpds == null) {
				cpds = new ComboPooledDataSource();
				try {
					cpds.setDriverClass("com.mysql.jdbc.Driver");
				} catch (PropertyVetoException e) {
					log.warn("Unable to use JDBC driver", e);
				}
				cpds.setJdbcUrl(getCfgStr("appening.db.url"));
				cpds.setUser(getCfgStr("appening.db.user"));
				cpds.setPassword(getCfgStr("appening.db.password"));
			}
			return cpds.getConnection();
		} catch (SQLException e) {
			log.warn("Unable to get db connection", e);
		}
		return null;
	}

	public static final DateFormat sqlDateTimeFormat = new SimpleDateFormat(
			"yyyy-MM-dd' 'HH:mm:ss");
	public static final DateFormat sqlDateFormat = new SimpleDateFormat(
			"yyyy-MM-dd");
	static {
		sqlDateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		sqlDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	public static final DateFormat jsonDateFormat = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mmZ");
	static {
		jsonDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private static DefaultHttpClient httpClient = new DefaultHttpClient();

	public static JSONObject getJsonFromUrl(String url) {
		JSONParser p = new JSONParser();
		try {
			HttpResponse getResponse = httpClient.execute(new HttpGet(url));
			return (JSONObject) p.parse(new InputStreamReader(getResponse
					.getEntity().getContent()));

		} catch (Exception e) {
			log.warn("Failed to retrieve data from '" + url + "'", e);
		}
		return null;
	}

	public static String makeURL(String base, Map<String, String> params) {
		if ("".equals(base) || params == null) {
			throw new IllegalArgumentException("Me no like.");
		}
		String url = base;
		if (!url.endsWith("?")) {
			url += "?";
		}
		Iterator<Entry<String, String>> it = params.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, String> param = it.next();
			try {
				url += URLEncoder.encode(param.getKey(), "UTF-8") + "="
						+ URLEncoder.encode(param.getValue(), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				log.warn(e);
			}
			if (it.hasNext()) {
				url += "&";
			}
		}

		return url;
	}

	public static Date getTwitterDate(String date) throws ParseException {
		final String TWITTER = "EEE, dd MMM yyyy HH:mm:ss Z";
		SimpleDateFormat sf = new SimpleDateFormat(TWITTER, Locale.ENGLISH);
		sf.setLenient(true);
		return sf.parse(date);
	}

	public static double slope(long[] points) {
		if (points.length < 2) {
			return 0;
		}

		// fitted curve: y=a+b*x
		// a= (sum(y)*sum(x^2) - sum(x)*sum(x*y)) / (n*sum(x^2)-(sum(x))^2)
		// b = (n*sum(x*y) - sum(x)*sum(y)) / (n*sum(x^2)-(sum(x))^2)

		// http://www.efunda.com/math/leastsquares/lstsqr1dcurve.cfm
		long sx2 = 0;
		long sx = 0;
		long sy = 0;
		long sxy = 0;
		long n = points.length;
		for (int x = 0; x < n; x++) {
			long y = points[x];
			sx2 += Math.pow(x, 2);
			sx += x;
			sy += y;
			sxy += x * y;
		}

		double denom = n * sx2 - Math.pow(sx, 2);
		// $a = ($sy*sx2 - $sx*$sxy) / $denom; // not interesting?
		double b = (n * sxy - sx * sy) / denom;
		return b;
	}

	public static long[] lastHours(long[] points, int maxHours) {
		long[] h = new long[maxHours];
		for (int x = 0; x < maxHours; x++) {
			long y = points[x];
			if (x < maxHours) {
				h[x] = y;
			}
		}
		return h;
	}

	public static long[] fillField(long[] points, int numHours) {
		long[] h = { 0 };
		for (int x = 0; x < numHours; x++) {
			h[x] = points[x];
		}
		return h;
	}

	private static final String awsAccessKey = Utils
			.getCfgStr("appening.export.awsAccessKey");
	private static final String awsSecretKey = Utils
			.getCfgStr("appening.export.awsSecretKey");
	private static final String s3Bucket = Utils
			.getCfgStr("appening.export.S3Bucket");
	private static final String s3Prefix = Utils
			.getCfgStr("appening.export.s3Prefix");

	private static RestS3Service s3 = null;
	private static AccessControlList bucketAcl = null;

	private static String jsonArrToS3(JSONArray json, String s3Key) {
		/*
		 * File sTmpFile = new File(
		 * "/Users/hannes/eclipse-ws/Appening/src/main/frontend/" + s3Key); try
		 * { FileWriter sWriter = new FileWriter(sTmpFile);
		 * json.writeJSONString(sWriter); sWriter.close();
		 * 
		 * } catch (IOException e1) { log.warn(e1); }
		 * 
		 * if (true) { return s3Key; }
		 */

		if (s3 == null) {
			try {
				s3 = new RestS3Service(new AWSCredentials(awsAccessKey,
						awsSecretKey));
				s3.getHttpClient().getParams()
						.setParameter("http.protocol.content-charset", "UTF-8");
			} catch (S3ServiceException e) {
				log.warn("Unable to initialize S3 client", e);
			}
		}
		if (bucketAcl == null) {
			try {
				bucketAcl = s3.getBucketAcl(s3Bucket);
			} catch (ServiceException e) {
				log.warn("Unable to update S3 Bucket ACL", e);
			}
			bucketAcl.grantPermission(GroupGrantee.ALL_USERS,
					Permission.PERMISSION_READ);
		}

		try {
			File tmpFile = File.createTempFile("appening-upload", ".json");
			FileWriter writer = new FileWriter(tmpFile);
			json.writeJSONString(writer);
			writer.close();

			S3Object dataFileObject = new S3Object(tmpFile);
			dataFileObject.setKey(s3Key);
			dataFileObject.setAcl(bucketAcl);
			dataFileObject.setContentType("application/json");
			dataFileObject.setContentEncoding("UTF-8");

			s3.putObject(s3Bucket, dataFileObject);
			tmpFile.delete();

			return s3.createUnsignedObjectUrl(s3Bucket, s3Key, true, false,
					false);
		} catch (Exception e) {
			log.warn("Unable to upload JSON to S3", e);
		}

		return "";
	}
}
