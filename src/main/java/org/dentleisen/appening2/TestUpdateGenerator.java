package org.dentleisen.appening2;

import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

public class TestUpdateGenerator {

	private static Logger log = Logger.getLogger(TestUpdateGenerator.class);

	private static final long minMentions = Utils
			.getCfgInt("appening.twitter.minMentions");
	private static final long minMentionsDays = Utils
			.getCfgInt("appening.twitter.minMentionDays");

	private static final String urlPrefix = Utils
			.getCfgStr("appening.twitter.urlPrefix");

	private static final long interval = Utils
			.getCfgInt("appening.twitter.intervalSeconds") * 1000;

	private static final double minRank = Utils
			.getCfgDbl("appening.twitter.minRank");

	private static final double lastMentionedMins = Utils
			.getCfgDbl("appening.twitter.lastMentionedMins");

	private static final int numMessages = 20;

	public static void runTask() {
		try {
			log.info("Checking for places to push to feed...");
			List<PopularPlace> places = Place.loadPopularPlaces(minMentions,
					minMentionsDays);
			for (PopularPlace p : places) {
				/*if (p.rank < minRank) {
					log.debug("Ignoring " + p.name + " (" + p.id + ") - rank "
							+ p.rank + " < " + minRank);
					continue;
				}
				if (p.wasMentioned(lastMentionedMins)) {
					log.debug("Ignoring " + p.name + " (" + p.id
							+ ") - already mentioned in last "
							+ lastMentionedMins + " minutes");
					continue;
				}*/

					log.info(p.name);
				List<Message> recentMessages = p.loadRecentMessages(
						numMessages, Utils.messageThreshold);
				Collection<WebResource> wrs = WebResource
						.resolveLinks(recentMessages);
				for (WebResource wr : wrs) {
					log.info(wr);
				}

				// p.setMentioned();
			}
			log.info("Done checking places");
		} catch (Exception e) {
			log.warn("Unable to update twitter feed", e);
		}
	}

	public static void main(String[] args) {
		Timer t = new Timer();
		TimerTask tt = new TimerTask() {
			@Override
			public void run() {
				runTask();
			}
		};
		t.scheduleAtFixedRate(tt, 0, interval);

	}
}
