package org.dentleisen.appening2;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import twitter4j.GeoLocation;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterUpdateGenerator {

	private static Logger log = Logger.getLogger(TwitterUpdateGenerator.class);

	private static final long minMentions = Utils
			.getCfgInt("appening.twitter.minMentions");
	private static final long minMentionsDays = Utils
			.getCfgInt("appening.twitter.minMentionDays");

	private static final String urlPrefix = Utils
			.getCfgStr("appening.twitter.urlPrefix");

	private static final String accessToken = Utils
			.getCfgStr("appening.twitter.accessToken");
	private static final String accessSecret = Utils
			.getCfgStr("appening.twitter.accessSecret");

	private static final String consumerKey = Utils
			.getCfgStr("appening.twitter.consumerKey");
	private static final String consumerSecret = Utils
			.getCfgStr("appening.twitter.consumerSecret");

	private static final long interval = Utils
			.getCfgInt("appening.twitter.intervalSeconds") * 1000;

	public static void main(String[] args) {
		
		 ConfigurationBuilder cb = new ConfigurationBuilder();
	        cb.setDebugEnabled(false)
	                .setOAuthConsumerKey(consumerKey)
	                .setOAuthConsumerSecret(consumerSecret)
	                .setOAuthAccessToken(accessToken)
	                .setOAuthAccessTokenSecret(accessSecret);

		TwitterFactory factory = new TwitterFactory(cb.build());
		final Twitter twitter = factory.getInstance();

		Timer t = new Timer();
		TimerTask tt = new TimerTask() {
			@Override
			public void run() {
				try {
					List<Place> places = Place.loadPopularPlaces(minMentions,
							minMentionsDays);
					for (Place p : places) {
						if (p.id != 663) {
							continue;
						}
						// recent mentions
						Map<Integer, Integer> m = p.loadPlaceMentions(Calendar
								.getInstance(TimeZone.getTimeZone("UTC"))
								.getTime());

						// TODO: generate trends in java already? stupid to have this both in js and java!
						

						StatusUpdate su = new StatusUpdate("Found buzz about " + p.name + " - " + urlPrefix
								+ "#-" + p.id);
						su.setLocation(new GeoLocation(p.lat, p.lng));
						log.info(su);
						twitter.updateStatus(su);
					}
					log.info("Published to Twitter.");
				} catch (Exception e) {
					log.warn("Unable to update twitter feed", e);
				}
			}
		};
		t.scheduleAtFixedRate(tt, 0, interval);

	}
}
