package org.dentleisen.appening2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

public class WebResource {

	private static Logger log = Logger.getLogger(WebResource.class);

	public enum Type {
		webpage, image
	}

	private String shortenedUrl;
	private Type type;
	private String url;
	private String title;
	private boolean hadErrors = false;
	private File imageFile;

	private static Pattern pageTitlePattern = Pattern.compile(
			"<title>(.*?)</title>", Pattern.DOTALL);

	public WebResource(String shortenedUrl) {
		this.shortenedUrl = shortenedUrl;
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
			String pageContent = EntityUtils.toString(getResponse.getEntity());

			Matcher m = pageTitlePattern.matcher(pageContent);
			if (m.find()) {
				title = m.group(1);
			}

			URL u = new URL(url);

			for (Entry<String, Pattern> scraper : imageScrapers.entrySet()) {
				if (u.getHost().contains(scraper.getKey())) {
					Matcher sm = scraper.getValue().matcher(pageContent);
					if (sm.find()) {
						url = sm.group(1);
						type = Type.image;
						return this;
					}
				}
			}

			type = Type.webpage;
			return this;
		} catch (Exception e) {
			log.warn("Failed to resolve URL", e);
		}
		hadErrors = true;
		return null;
	}

	public WebResource downloadImageAndResize() {
		if (this.type != Type.image) {
			log.warn("Cannot download non-images!");
			return null;
		}
		try {
			File f = File.createTempFile(this.getClass().getSimpleName() + "-",
					".image");
			HttpResponse getResponse = httpClient.execute(new HttpGet(url));
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

	private static DefaultHttpClient httpClient = new DefaultHttpClient();
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

	private Object getImageFile() {
		return imageFile;
	}

	public static void main(String[] args) {

		Set<String> s = new HashSet<String>(Arrays.asList(
				"http://t.co/gpGeFIWC", "http://t.co/mHMgEL3g",
				"http://t.co/u96DwxBH", "http://t.co/hjIjBxCf",
				"http://t.co/jFiJbgMv", "http://t.co/HKhrgaXS",
				"http://t.co/jDUUuTHw", "http://t.co/2opPCBIF",
				"http://t.co/N4O5sm58", "http://t.co/LaZ6i4fl"));

		for (String url : s) {
			WebResource r = new WebResource(url);
			r.resolve();
			if (r.hadErrors()) {
				continue;
			}
			log.info(r);
			if (r.getType() == Type.image) {
				r.downloadImageAndResize();
				if (r.hadErrors()) {
					continue;
				}
				log.info(r.getImageFile());
			}
		}

	}
}
