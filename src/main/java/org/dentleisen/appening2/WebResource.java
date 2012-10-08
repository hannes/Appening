package org.dentleisen.appening2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.coobird.thumbnailator.Thumbnails;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

public class WebResource {

	private static Logger log = Logger.getLogger(WebResource.class);

	public enum Type {
		webpage, image
	}

	private String shortenedUrl;
	private Type type = Type.webpage;
	private String url;
	private String title;
	private boolean hadErrors = false;
	private File imageFile;
	private String imageUrl;
	private boolean loadedFromDb = false;

	private static Pattern pageTitlePattern = Pattern.compile(
			"<title>(.*?)</title>", Pattern.DOTALL);

	public WebResource(String shortenedUrl) {
		this.shortenedUrl = shortenedUrl;
	}

	public WebResource(String url, String type, String title, String imageUrl) {
		this.url = url;
		this.type = Type.valueOf(title);
		this.title = title;
		this.imageUrl = imageUrl;
		this.loadedFromDb = true;
	}

	public static Map<String, Pattern> imageScrapers = new HashMap<String, Pattern>();
	static {
		imageScrapers
				.put("twitpic",
						Pattern.compile(
								" <img src=\"(http://[^\"]*\\.cloudfront\\.net/photos/large/[^\"]*)\" ",
								Pattern.DOTALL));
		imageScrapers
				.put("instagram",
						Pattern.compile(
								"<img class=\"photo\" src=\"(http://[^\"]*\\.instagram.com/[^\"]*)\"",
								Pattern.DOTALL));
		imageScrapers.put("lockerz", Pattern.compile(
				"<img id=\"photo\" src=\"(http://[^\"]*)\"", Pattern.DOTALL));
		imageScrapers.put("img.ly", Pattern.compile(
				"<img [^>]* id=\"the-image\" src=\"(http://[^\"]*)\"",
				Pattern.DOTALL));
		imageScrapers.put("yfrog", Pattern.compile(
				"<img [^>]* id=\"main_image\" src=\"(http://[^\"]*)\"",
				Pattern.DOTALL));
	}

	public WebResource resolve() {
		try {
			HttpContext localContext = new BasicHttpContext();

			HttpGet get = new HttpGet(shortenedUrl);
			HttpResponse getResponse = httpClient.execute(get, localContext);

			url = getUrlAfterRedirects(localContext);

			// now check db and load ourselves from db if url is found
			WebResource dbResource = searchDb(url);
			if (dbResource != null) {
				this.type = dbResource.type;
				this.title = dbResource.title;
				this.imageUrl = dbResource.imageUrl;
				return this;
			}

			URL u = new URL(url);

			String pageContent = EntityUtils.toString(getResponse.getEntity());

			Matcher m = pageTitlePattern.matcher(pageContent);
			if (m.find()) {
				title = m.group(1);
				title = title.replace("\n", " ");
				title = title.replace("\t", " ");
				title = title.replaceAll(" {2,}", " ");
				title = title.trim();
			}

			for (Entry<String, Pattern> scraper : imageScrapers.entrySet()) {
				if (u.getHost().contains(scraper.getKey())) {
					Matcher sm = scraper.getValue().matcher(pageContent);
					if (sm.find()) {
						imageUrl = sm.group(1);
						type = Type.image;
					}
				}
			}
			// TODO: where is this decision taken?
			if (isImage()) {
				downloadImageAndResize();
			}
			save();

			return this;
		} catch (Exception e) {
			log.debug("Failed to resolve URL", e);
		}
		hadErrors = true;
		return null;
	}

	private static WebResource searchDb(String anUrl) {
		WebResource res = null;
		Connection c = null;
		PreparedStatement s = null;
		ResultSet rs = null;
		try {
			c = Utils.getConnection();
			s = c.prepareStatement("SELECT `url`,`type`,`title`,`imageUrl` FROM `urls` WHERE `url`=?");
			s.setString(1, anUrl);
			rs = s.executeQuery();

			if (rs.next()) {
				res = new WebResource(rs.getString("url"),
						rs.getString("type"), rs.getString("title"),
						rs.getString("imageUrl"));
			}
		} catch (SQLException e) {
			log.warn("Failed to run statement", e);
		} finally {
			try {
				rs.close();
			} catch (SQLException e) {
				log.warn("Failed to clean up after statement", e);
			}
			try {
				s.close();
			} catch (SQLException e) {
				log.warn("Failed to clean up after statement", e);
			}
			try {
				c.close();
			} catch (SQLException e) {
				log.warn("Failed to clean up after statement", e);
			}
		}
		return res;
	}

	private WebResource downloadImageAndResize() {
		if (this.type != Type.image) {
			log.warn("Cannot download non-images!");
			return null;
		}
		try {
			File f = File.createTempFile(this.getClass().getSimpleName() + "-",
					".image");
			HttpResponse getResponse = httpClient
					.execute(new HttpGet(imageUrl));
			getResponse.getEntity().writeTo(new FileOutputStream(f));

			imageFile = File.createTempFile(this.getClass().getSimpleName()
					+ "-", ".png");
			Thumbnails.of(f).size(400, 400).outputFormat("png")
					.toFile(imageFile);
			f.delete();

			return this;
		} catch (Exception e) {
			log.warn(this + ": Unable to download file", e);
			imageFile = null;
			hadErrors = true;
		}
		return this;

	}

	private static DefaultHttpClient httpClient = new DefaultHttpClient(
			new ThreadSafeClientConnManager());
	public static final String LAST_REDIRECT_URL = "last_redirect_url";

	static {
		httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
			@Override
			public void process(HttpResponse response, HttpContext context)
					throws HttpException, IOException {
				if (response.containsHeader("Location")) {
					Header[] locations = response.getHeaders("Location");
					if (locations.length > 0)
						context.setAttribute(LAST_REDIRECT_URL,
								locations[0].getValue());
				}
			}
		});
	}

	private static String getUrlAfterRedirects(HttpContext context) {
		String lastRedirectUrl = (String) context
				.getAttribute(LAST_REDIRECT_URL);
		if (lastRedirectUrl != null)
			return lastRedirectUrl;
		else {
			HttpUriRequest currentReq = (HttpUriRequest) context
					.getAttribute(ExecutionContext.HTTP_REQUEST);
			HttpHost currentHost = (HttpHost) context
					.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
			String currentUrl = (currentReq.getURI().isAbsolute()) ? currentReq
					.getURI().toString() : (currentHost.toURI() + currentReq
					.getURI());
			return currentUrl;
		}
	}

	public Type getType() {
		return type;
	}

	public String getUrl() {
		return url;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public URL getUrlObj() {
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			log.debug("Malformed URL", e);
			return null;
		}
	}

	public void setImageUrl(String newUrl) {
		imageUrl = newUrl;
	}

	public String getTitle() {
		return title;
	}

	@Override
	public String toString() {
		return type + ": " + title + " (" + url + ")";
	}

	public boolean hadErrors() {
		return hadErrors;
	}

	public File getImageFile() {
		return imageFile;
	}

	public boolean isImage() {
		return this.type.equals(Type.image);
	}

	@SuppressWarnings("unchecked")
	public Object toJSON() {
		JSONObject msgObj = new JSONObject();
		msgObj.put("type", type.toString());
		msgObj.put("url", url);
		msgObj.put("title", title);
		msgObj.put("imageUrl", imageUrl);
		return msgObj;
	}

	public void save() {
		if (this.loadedFromDb) {
			return;
		}
		try {
			Connection c = Utils.getConnection();
			PreparedStatement s = c
					.prepareStatement("INSERT DELAYED IGNORE INTO `urls` (`url`,`type`,`title`,`imageUrl`) VALUES (?,?,?,?)");

			s.setString(1, getUrl());
			s.setString(2, getType().toString());
			s.setString(3, getTitle());
			s.setString(4, getImageUrl());

			s.executeUpdate();
			s.close();
			c.close();
		} catch (SQLException e) {
			log.warn("Failed to save web resource " + toString() + " to db", e);
		}
	}

	public static void main(String[] args) {
		ExecutorService es = Executors.newFixedThreadPool(10);

		Set<String> s = new HashSet<String>(Arrays.asList(
				"http://t.co/gpGeFIWC", "http://t.co/mHMgEL3g",
				"http://t.co/u96DwxBH", "http://t.co/hjIjBxCf",
				"http://t.co/jFiJbgMv", "http://t.co/HKhrgaXS",
				"http://t.co/jDUUuTHw", "http://t.co/2opPCBIF",
				"http://t.co/N4O5sm58", "http://t.co/LaZ6i4fl"));

		List<Future<WebResource>> futures = new ArrayList<Future<WebResource>>();
		for (final String url : s) {
			futures.add(es.submit(new Callable<WebResource>() {
				@Override
				public WebResource call() throws Exception {
					WebResource r = new WebResource(url);
					r.resolve();
					if (r.hadErrors()) {
						return r;
					}
					if (r.getType() == Type.image) {
						r.downloadImageAndResize();
						if (r.hadErrors()) {
							return r;
						}
					}
					return r;
				}
			}));
		}
		do {
			Iterator<Future<WebResource>> futureIterator = futures.iterator();
			while (futureIterator.hasNext()) {
				Future<WebResource> wr = futureIterator.next();
				if (wr.isDone()) {
					try {
						log.info(wr.get());
						futureIterator.remove();

					} catch (Exception e) {
						// should not happen, we have already checked if the
						// result is ready.
						e.printStackTrace();
					}
				}
			}

		} while (futures.size() > 0);
		es.shutdownNow();
	}

	private static final ExecutorService resolverThreadPool = Executors
			.newFixedThreadPool(10);

	private static Set<String> ignoreDomains = new HashSet<String>();
	static {
		ignoreDomains.add("foursquare");
	}

	public static Collection<WebResource> resolveLinks(
			List<Message> recentMessages) {
		Set<String> links = new HashSet<String>();

		for (Message msg : recentMessages) {
			links.addAll(msg.findLinks());
		}

		List<Future<WebResource>> futures = new ArrayList<Future<WebResource>>();
		for (final String url : links) {
			futures.add(resolverThreadPool.submit(new Callable<WebResource>() {
				@Override
				public WebResource call() throws Exception {
					WebResource r = new WebResource(url);
					r.resolve();
					if (r.hadErrors()) {
						return r;
					}
					if (r.getType() == Type.image) {
						r.downloadImageAndResize();
						if (r.hadErrors()) {
							return r;
						}
					}
					return r;
				}
			}));
		}
		Map<String, WebResource> resources = new HashMap<String, WebResource>();
		do {
			Iterator<Future<WebResource>> futureIterator = futures.iterator();
			while (futureIterator.hasNext()) {
				Future<WebResource> wrf = futureIterator.next();
				if (wrf.isDone()) {
					futureIterator.remove();
					try {
						WebResource wr = wrf.get();
						URL u = wr.getUrlObj();
						if (u == null) {
							continue;
						}
						boolean ignore= false;
						for (String ignoreDomain : ignoreDomains) {
							if (u.getHost().contains(ignoreDomain)) {
								ignore = true;
							}
						}
						if (ignore) {
							continue;
						}
						if (!resources.containsKey(wr.getUrl())) {
							resources.put(wr.getUrl(), wr);
						}

					} catch (Exception e) {
						// should not happen, we have already checked if
						// the
						// result is ready.
						e.printStackTrace();
					}
				}
			}

		} while (futures.size() > 0);

		return resources.values();
	}
}
