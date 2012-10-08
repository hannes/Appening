package org.dentleisen.appening2;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.io.SyndFeedOutput;

public class DataExporter {

	private static Logger log = Logger.getLogger(DataExporter.class);

	private static final long minMentions = Utils
			.getCfgInt("appening.export.minMentions");
	private static final long minMentionsDays = Utils
			.getCfgInt("appening.export.minMentionDays");

	private static final String awsAccessKey = Utils
			.getCfgStr("appening.export.awsAccessKey");
	private static final String awsSecretKey = Utils
			.getCfgStr("appening.export.awsSecretKey");
	private static final String s3Bucket = Utils
			.getCfgStr("appening.export.S3Bucket");
	private static final String s3Prefix = Utils
			.getCfgStr("appening.export.s3Prefix");

	private static final long interval = Utils
			.getCfgInt("appening.export.intervalSeconds") * 1000;

	// i know, wrong prefix... but hey
	private static final String urlPrefix = Utils
			.getCfgStr("appening.twitter.urlPrefix");

	private static final int numMessages = 20;
	
	// patterns to extract URLs from tweets
    private static final Pattern split = Pattern.compile("[\\s;\\(\\)]");
    private static final Pattern stripBeginning = Pattern.compile("^['\":-]+");
    private static final Pattern stripEnding = Pattern.compile("['\":\\?!\\.]+$");

	public static void runTask() {
		exportRSS();
		exportJson();
	}

	public static void exportRSS() {
		try {
			SyndFeed feed = new SyndFeedImpl();
			feed.setFeedType("rss_2.0");
			feed.setLink("http://www.appening.at");
			feed.setTitle("Appening Amsterdam");
			feed.setDescription("Things happening now in Amsterdam according to Twitter.");
			List<PopularPlace> places = Place.loadLastMentionedPlaces(50);
			List<SyndEntry> entries = new ArrayList<SyndEntry>();

			for (PopularPlace p : places) {
				SyndEntry entry = new SyndEntryImpl();
				SyndContent description;

				entry.setTitle(p.name);
				String link = urlPrefix + "#"
						+ URLEncoder.encode(p.name, "UTF-8") + "-"
						+ Long.toHexString(p.lastMentioned.getTime()) + "-"
						+ p.id;

				entry.setLink(link);

				entry.setPublishedDate(p.lastMentioned);
				description = new SyndContentImpl();
				description.setType("text/html");

				String html = "<ul><li><a href=\""
						+ link
						+ "\">Appening page for place</a></li><li><a href=\"https://maps.google.com/maps?t=h&q=loc:"
						+ p.lat + "," + p.lng
						+ "&z=17\">Google Maps</a></li></ul><br /><ul>";
				List<Message> recentMessages = p.loadRecentMessages(
						numMessages, Utils.messageThreshold);

				for (Message m : recentMessages) {
					html += "<li>"
							+ Utils.linkify("@" + m.getUser() + ": "
									+ m.getText()) + "</li>";
				}

				html += "</ul>";

				description.setValue(html);

				entry.setDescription(description);
				entries.add(entry);
			}

			feed.setEntries(entries);
			SyndFeedOutput output = new SyndFeedOutput();
			String rss = output.outputString(feed);

			stringToS3(rss, s3Prefix + "feed.rss", "application/rss+xml");

			log.info("RSS feed uploaded");
		} catch (Exception e) {
			log.warn("Unable to create and upload RSS feed", e);
		}
	}

	@SuppressWarnings("unchecked")
	public static void exportJson() {
		JSONArray popularPlacesJson = new JSONArray();

		try {
			List<PopularPlace> popularPlaces = Place.loadPopularPlaces(minMentions,
					minMentionsDays);
			
			for (PopularPlace popularPlace : popularPlaces) {
			    // upload recent twitter messages so we can display them
				JSONArray messagesJson = new JSONArray();
				List<Message> recentMessages = popularPlace.loadRecentMessages(
						numMessages, Utils.messageThreshold);
				
				for (Message msg : recentMessages) {
				    // extract URLs from message, add to popular place's link set
				    // we do it here, to lazily expand short URLs
				    List<String> links = extractUrls(msg.getText());
				    if (links != null) {
				        for (String link : links) {
	                        popularPlace.links.add(link);
				        }
				    }
				    
					messagesJson.add(msg.toJSON());
				}

				String messagesJsonUrl = jsonArrToS3(messagesJson, s3Prefix + popularPlace.id
						+ "-messages.json");

				// add reference to messages JSON to place so we can
				// load it from GUI
				JSONObject popularPlaceJson = popularPlace.toJSON();
				popularPlaceJson.put("messagesUrl", messagesJsonUrl);
				popularPlacesJson.add(popularPlaceJson);
			}
			
			jsonArrToS3(popularPlacesJson, s3Prefix + "places.json");

			log.info("Created JSON & uploaded to S3");
		} catch (Exception e) {
			log.warn("Unable to create and upload json file", e);
		}
	}
	
	/**
	 * Finds and expands all URLs from the text of a tweet.
	 * @param text text of a tweet
	 * @return list of URLs, null if there are no URLs
	 */
	private static List<String> extractUrls(String text) {
	    List<String> ret = null;
        String[] words = split.split(text);
        for (String word : words) {
            // strip all kinds of weird symbols from beginning of word
            Matcher m = stripBeginning.matcher(word);
            word = m.replaceFirst("");

            // strip even more weird symbols from end of word
            m = stripEnding.matcher(word);
            word = m.replaceFirst("");

            if (word.length() > 9 && (word.startsWith("http://") || word.startsWith("https://"))) {
                // this word is a URL
                String expandedUrl = expandUrl(word, 0);

                if (expandedUrl.endsWith("/")) {
                    expandedUrl = expandedUrl.substring(0, expandedUrl.length() - 1);
                }
                
                if (ret == null) {
                    ret = new ArrayList<String>();
                }
                ret.add(expandedUrl);
            }
        }
        
        return ret;
	}

    /**
     * Expands a shortened URL. Recursively expands chained shortened URLS (e.g.
     * t.co -> bit.ly -> bit.ly...). Stops after 3 recursions. In case of any
     * exceptions, the original (short) URL is returned.
     * @param shortenedUrl the URL to expand
     * @param recursionDepth depth of recursion
     * @return the long URL
     */
    private static String expandUrl(String shortenedUrl, int recursionDepth) {
        if (log.isDebugEnabled()) {
            log.debug("expand URL " + shortenedUrl);
        }

        String expandedUrl;
        if (shortenedUrl.length() > 30 || recursionDepth >= 2) {
            expandedUrl = shortenedUrl;
        }
        else {
            URL url;
            HttpURLConnection connection = null;
            try {
                url = new URL(shortenedUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(1000);
                connection.connect();

                expandedUrl = connection.getHeaderField("Location");
                connection.getInputStream().close();

                if (expandedUrl == null || !expandedUrl.startsWith("http")) {
                    expandedUrl = shortenedUrl;
                }
                else {
                    expandedUrl = expandUrl(expandedUrl, recursionDepth + 1);
                }
            }
            catch (Exception e) {
                expandedUrl = shortenedUrl;
            }
            finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        return expandedUrl;
    }

	public static void main(String[] args) {
		log.info("Data Exporter starting, charset=" + Charset.defaultCharset()
				+ ", interval=" + interval);
		try {

			Timer t = new Timer();
			TimerTask tt = new TimerTask() {
				@Override
				public void run() {
					runTask();
				}
			};
			t.scheduleAtFixedRate(tt, 0, interval);

		} catch (Exception e) {
			log.error("Unable to use data exporter, exiting", e);
			System.exit(-1);
		}
	}

	private static RestS3Service s3 = null;
	private static AccessControlList bucketAcl = null;

	private static String jsonArrToS3(JSONArray arr, String s3Key) {
		return stringToS3(arr.toJSONString(), s3Key, "application/json");
	}

	private static String stringToS3(String data, String s3Key,
			String contentType) {
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
			S3Object dataFileObject = new S3Object(s3Key, data);
			dataFileObject.setAcl(bucketAcl);
			dataFileObject.setContentType(contentType);
			dataFileObject.setContentEncoding("UTF-8");

			s3.putObject(s3Bucket, dataFileObject);
			log.debug("Uploaded to " + s3Key);
			return s3.createUnsignedObjectUrl(s3Bucket, s3Key, true, false,
					false);
		} catch (Exception e) {
			log.warn("Unable to upload JSON to S3", e);
		}

		return "";
	}
}
