package org.dentleisen.appening2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

public class Place {
	protected static Logger log = Logger.getLogger(Place.class);

	public int id;
	public String name;
	public double lat;
	public double lng;

	public static final String NN = "NN";

	public Place(Place somePlace) {
		this.id = somePlace.id;
		this.name = somePlace.name;
		this.lat = somePlace.lat;
		this.lng = somePlace.lng;
	}

	public Place(double lat, double lng) {
		this.lat = lat;
		this.lng = lng;
		this.name = NN;
	}

	public Place(String name, double lat, double lng) {
		this.lat = lat;
		this.lng = lng;
		this.name = name;
	}

	public Place(int id, String name, double lat, double lng) {
		this.id = id;
		this.lat = lat;
		this.lng = lng;
		this.name = name;
	}

	// This is more or less fixed now...
	public static final int hoursMentions = 48;

	public long[] loadPlaceMentions(Date currentTime) {
		Timestamp currentTimeSql = new java.sql.Timestamp(currentTime.getTime());
		Map<Integer, Integer> mentions = new HashMap<Integer, Integer>();

		Connection c = null;
		PreparedStatement s = null;
		ResultSet rs = null;
		try {
			c = Utils.getConnection();
			s = c.prepareStatement("SELECT ROUND((UNIX_TIMESTAMP(?)-UNIX_TIMESTAMP(`start`))/3600) AS `hoursAgo`,`count` FROM `counts` WHERE `place`=? AND `start` > DATE_SUB(?,INTERVAL ? HOUR) AND start < ?  UNION SELECT 0 AS `hoursAgo`,`count` FROM `nowcounts` WHERE `place` = ? ORDER BY `hoursAgo` DESC");
			s.setTimestamp(1, currentTimeSql);
			s.setInt(2, id);
			s.setTimestamp(3, currentTimeSql);
			s.setInt(4, hoursMentions - 1);
			s.setTimestamp(5, currentTimeSql);
			s.setInt(6, id);
			rs = s.executeQuery();
			while (rs.next()) {
				mentions.put(rs.getInt("hoursAgo"), rs.getInt("count"));
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
		long[] mArr = new long[hoursMentions];
		for (Entry<Integer, Integer> e : mentions.entrySet()) {
			mArr[e.getKey()] = e.getValue();
		}
		return mArr;
	}

	@Override
	public int hashCode() {
		return (id + "#" + name + "#" + lat + "#" + lng).hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return hashCode() == o.hashCode();
	}

	public void save() {
		try {
			Connection c = Utils.getConnection();
			PreparedStatement s = c
					.prepareStatement("INSERT INTO `places` (`name`,`lat`,`lng`) VALUES (?,?,?)");

			s.setString(1, name);
			s.setDouble(2, lat);
			s.setDouble(3, lng);

			s.executeUpdate();
			s.close();
			c.close();
		} catch (SQLException e) {
			log.warn("Failed to save mention " + toString() + " to db", e);
		}
	}

	public static List<Place> loadPlaces() {
		List<Place> messages = new ArrayList<Place>();
		try {
			Connection c = Utils.getConnection();
			Statement s = c.createStatement();
			ResultSet rs = s.executeQuery("SELECT * FROM `places`");
			while (rs.next()) {
				messages.add(Place.fromSqlResult(rs));
			}

			rs.close();
			s.close();
			c.close();
		} catch (SQLException e) {
			log.warn("Failed to load places from db", e);

		}
		return messages;
	}

	private static Place fromSqlResult(ResultSet rs) throws SQLException {
		return new Place(rs.getInt("id"), rs.getString("name"),
				rs.getDouble("lat"), rs.getDouble("lng"));
	}

	public static List<PopularPlace> loadPopularPlaces(long minMentions,
			long mentionDays) {
		List<PopularPlace> places = new ArrayList<PopularPlace>();
		Connection c = null;
		PreparedStatement s = null;
		ResultSet rs = null;
		try {
			c = Utils.getConnection();
			s = c.prepareStatement("SELECT `id`,`name`,`lat`,`lng` FROM (SELECT `place`,SUM(`count`) AS `mentions` from counts WHERE `start` > DATE_SUB(NOW(),INTERVAL ? DAY) GROUP BY `place`) AS `counts` JOIN `places` ON `places`.`id`=`counts`.`place` WHERE `mentions` > ?");
			s.setLong(1, mentionDays);
			s.setLong(2, minMentions);
			rs = s.executeQuery();

			while (rs.next()) {
				places.add(new PopularPlace(fromSqlResult(rs)));
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

		Collections.sort(places, new Comparator<PopularPlace>() {
			@Override
			public int compare(PopularPlace arg0, PopularPlace arg1) {
				return Double.compare(arg0.rank, arg1.rank) * -1;
			}
		});
		return places;
	}

	public List<Message> loadRecentMessages(int numMessages, double threshold) {
		List<Message> messages = new ArrayList<Message>();
		Connection c = null;
		PreparedStatement s = null;
		ResultSet rs = null;
		try {
			c = Utils.getConnection();
			s = c.prepareStatement("SELECT * FROM `messages` WHERE MATCH (`text`) AGAINST (? IN NATURAL LANGUAGE MODE) > ? ORDER BY `created` DESC LIMIT ?");
			s.setString(1, name);
			s.setDouble(2, threshold);
			s.setInt(3, numMessages);
			rs = s.executeQuery();

			while (rs.next()) {
				messages.add(Message.fromSqlResult(rs));
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

		return messages;
	}
}
