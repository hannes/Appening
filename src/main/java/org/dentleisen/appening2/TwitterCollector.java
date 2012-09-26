package org.dentleisen.appening2;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class TwitterCollector {
	private static Logger log = Logger.getLogger(TwitterCollector.class);
	private static DecimalFormat twitterPosFormat = new DecimalFormat("#.####");
	private static final String TWITTER_URL = "http://search.twitter.com/search.json";

	private static final long interval = Utils
			.getCfgInt("appening.collect.intervalSeconds") * 1000;

	private static final double circleCenterLat = Utils
			.getCfgDbl("appening.collect.circleLat");
	private static final double circleCenterLng = Utils
			.getCfgDbl("appening.collect.circleLng");
	private static final long circleRadiusKm = Utils
			.getCfgInt("appening.collect.circleRadiusKm");

	public static void main(String[] args) {

		Timer t = new Timer();
		TimerTask tt = new TimerTask() {
			@Override
			public void run() {
				List<Message> msgs = loadMessages(Message.getLastId(),
						circleCenterLat, circleCenterLng, circleRadiusKm);
				log.info("Loaded " + msgs.size() + " msgs");
				for (Message m : msgs) {
					m.save();
				}
			}
		};
		t.scheduleAtFixedRate(tt, 0, interval);
	}

	public static List<Message> loadMessages(String lastId, double lat,
			double lng, long radiusKm) {
		List<Message> resultMsgs = new ArrayList<Message>();

		Map<String, String> params = new HashMap<String, String>();
		params.put("geocode", twitterPosFormat.format(lat) + ","
				+ twitterPosFormat.format(lng) + "," + radiusKm + "km");
		params.put("result_type", "recent");

		if (lastId != null) {
			params.put("since_id", lastId);
		}
		int page = 1;
		String nextPageQuery = null;

		do {
			params.put("page", Integer.toString(page));
			String url = Utils.makeURL(TWITTER_URL, params);
			log.debug(url);

			JSONObject res = Utils.getJsonFromUrl(url);
			if (res == null) {
				log.warn("Got nothing back, breaking loop...");
				break;
			}

			JSONArray messages = (JSONArray) res.get("results");
			if (messages == null) {
				log.warn("Empty response, breaking...");
				break;
			}
			for (Object message : messages) {
				JSONObject jMessage = (JSONObject) message;
				Message m = Message.fromTwitterJson(jMessage);
				resultMsgs.add(m);
			}

			nextPageQuery = (String) res.get("next_page");
			page++;

		} while (nextPageQuery != null);
		return resultMsgs;
	}

}
