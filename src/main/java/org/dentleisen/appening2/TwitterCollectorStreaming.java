package org.dentleisen.appening2;

import org.apache.log4j.Logger;

import twitter4j.FilterQuery;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.conf.ConfigurationBuilder;

@Deprecated
public class TwitterCollectorStreaming {

	private static Logger log = Logger.getLogger(TwitterCollector.class);

	private static class Twitter4jProperties {
		public static final String CONSUMER_KEY = "SQdi4HoRFtDIH0GKOLbsA";
		public static final String CONSUMER_SECRET = "tJUckSOKMSFG0VU9A4GwUc5jNAq1HaYOrfLpntQNPI";
		public static final String ACCESS_TOKEN = "138518015-AbmK7aIjMsPzEX9yiRH76AeRYcsFcahMiqiZDVfG";
		public static final String ACCESS_TOKEN_SECRET = "YFH3WKHRrYFqe1yOokFwNltEwCmxkjuSM4H8gXm9KU";
	}

	public static void main(String[] args) {

		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
				.setOAuthConsumerKey(Twitter4jProperties.CONSUMER_KEY)
				.setOAuthConsumerSecret(Twitter4jProperties.CONSUMER_SECRET)
				.setOAuthAccessToken(Twitter4jProperties.ACCESS_TOKEN)
				.setOAuthAccessTokenSecret(
						Twitter4jProperties.ACCESS_TOKEN_SECRET);

		TwitterStream twitterStream = new TwitterStreamFactory(cb.build())
				.getInstance();

		StatusListener listener = new StatusListener() {
			@Override
			public void onStatus(Status status) {
				log.info("@" + status.getUser().getScreenName() + " - "
						+ status.getText());
			}

			@Override
			public void onException(Exception ex) {
				ex.printStackTrace();
			}

			@Override
			public void onDeletionNotice(StatusDeletionNotice arg0) {
				log.info(arg0);
			}

			@Override
			public void onScrubGeo(long arg0, long arg1) {
				log.info(arg0);
				log.info(arg1);
			}

			@Override
			public void onTrackLimitationNotice(int arg0) {
				log.info(arg0);
			}
		};
		twitterStream.addListener(listener);
		twitterStream.filter(new FilterQuery().locations(new double[][] {
				{ 52.227, 4.589 }, { 52.482, 5.100 } }));
	}
}