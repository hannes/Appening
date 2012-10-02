package org.dentleisen.appening2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.json.simple.JSONObject;

public class PopularPlace extends Place {

	public static class Popularity {
		public double slope;
		public long mentions;

		public Popularity(double slope, long mentions) {
			this.slope = slope;
			this.mentions = mentions;
		}

		@SuppressWarnings("unchecked")
		public JSONObject toJSON() {
			JSONObject jo = new JSONObject();
			jo.put("n", mentions);
			jo.put("s", slope);
			return jo;
		}
	}

	public Popularity popularity48h;
	public Popularity popularity24h;
	public Popularity popularity12h;
	public Popularity popularity6h;
	public Popularity popularity3h;

	public double rank = 0;

	public long[] mentions;

	PopularPlace(Place p) {
		super(p);
		mentions = this.loadPlaceMentions(Calendar.getInstance(
				TimeZone.getTimeZone("UTC")).getTime());

		popularity48h = generatePopularity(mentions, 48);
		popularity24h = generatePopularity(mentions, 24);
		popularity12h = generatePopularity(mentions, 12);
		popularity6h = generatePopularity(mentions, 6);
		popularity3h = generatePopularity(mentions, 3);

		if (popularity3h.mentions > 1) {
			rank = popularity6h.mentions * popularity6h.slope + 5
					* popularity3h.mentions * popularity3h.slope;
		}

	}

	private static Popularity generatePopularity(long[] mentions, int hours) {
		long[] subMntsArr = Utils.lastHours(mentions, hours);
		return new Popularity(Utils.slope(subMntsArr), sum(subMntsArr));
	}

	private static long sum(long[] mentions) {
		long mSum = 0;
		for (int i = 0; i < mentions.length; i++) {
			mSum += mentions[i];
		}
		return mSum;
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJSON() {
		JSONObject placeObj = new JSONObject();

		placeObj.put("id", id);
		placeObj.put("name", name);
		placeObj.put("lat", lat);
		placeObj.put("lng", lng);

		placeObj.put("sum", popularity48h.mentions);

		List<Long> mList = new ArrayList<Long>(mentions.length);
		for (long n : mentions) {
			mList.add(n);
		}
		placeObj.put("mentionsSeries", mList);

		JSONObject to = new JSONObject();
		to.put("h48", popularity48h.toJSON());
		to.put("h24", popularity24h.toJSON());
		to.put("h12", popularity12h.toJSON());
		to.put("h6", popularity6h.toJSON());
		to.put("h3", popularity3h.toJSON());
		to.put("rank", rank);

		placeObj.put("trend", to);
		return placeObj;
	}

	public boolean wasMentioned(double lastmentionedmins) {
		Connection c = null;
		PreparedStatement s = null;
		ResultSet rs = null;
		try {
			c = Utils.getConnection();
			s = c.prepareStatement("SELECT * FROM `placementioned` WHERE `place`=? AND `mentioned` > DATE_SUB(NOW(), INTERVAL ? MINUTE);");
			s.setInt(1, id);
			s.setDouble(2, lastmentionedmins);
			rs = s.executeQuery();

			return rs.next();
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
		return false;
	}

	public void setMentioned() {
		Connection c = null;
		PreparedStatement s1 = null;
		PreparedStatement s2 = null;
		try {
			c = Utils.getConnection();
			s1 = c.prepareStatement("DELETE FROM `placementioned` WHERE `place`=?;");
			s1.setInt(1, id);
			s1.execute();

			s2 = c.prepareStatement("INSERT INTO `placementioned` (`place`,`mentioned`) VALUES (?,NOW())");
			s2.setInt(1, id);
			s2.execute();

		} catch (SQLException e) {
			log.warn("Failed to run statement", e);
		} finally {

			try {
				s1.close();
			} catch (SQLException e) {
				log.warn("Failed to clean up after statement", e);
			}
			try {
				s2.close();
			} catch (SQLException e) {
				log.warn("Failed to clean up after statement", e);
			}
			try {
				c.close();
			} catch (SQLException e) {
				log.warn("Failed to clean up after statement", e);
			}
		}
	}
}
