package org.dentleisen.appening2;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

public class AppeningBackend {
	private static Logger log = Logger.getLogger(AppeningBackend.class);

	private static final long exportInterval = Utils
			.getCfgInt("appening.export.intervalSeconds") * 1000;

	private static final long collectInterval = Utils
			.getCfgInt("appening.collect.intervalSeconds") * 1000;

	private static final long twitterInterval = Utils
			.getCfgInt("appening.twitter.intervalSeconds") * 1000;

	public static void main(String[] args) {
		log.info("Starting Appening Backend");
		log.info("Scheduling tweet collector every " + collectInterval / 1000
				+ " sec");
		log.info("Scheduling tweet creator every " + twitterInterval / 1000
				+ " sec");
		log.info("Scheduling data exporter every " + exportInterval / 1000
				+ " sec");

		Timer twitterTimer = new Timer();
		TimerTask twitterTask = new TimerTask() {
			@Override
			public void run() {
				log.info("running tweet creator");
				TwitterUpdateGenerator.runTask();
			}
		};
		Timer collectTimer = new Timer();
		TimerTask collectTask = new TimerTask() {
			@Override
			public void run() {
				log.info("running tweet collector");
				TwitterCollector.runTask();
			}
		};
		Timer exportTimer = new Timer();
		TimerTask exportTask = new TimerTask() {
			@Override
			public void run() {
				log.info("running data exporter");
				DataExporter.runTask();
			}
		};
		twitterTimer.scheduleAtFixedRate(twitterTask, 0, twitterInterval);
		exportTimer.scheduleAtFixedRate(exportTask, 0, exportInterval);
		collectTimer.scheduleAtFixedRate(collectTask, 0, collectInterval);

	}

}
