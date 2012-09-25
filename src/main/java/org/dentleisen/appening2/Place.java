package org.dentleisen.appening2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class Place {
	private static Logger log = Logger.getLogger(Place.class);

	public String name;
	public double lat;
	public double lng;

	public static final String NN = "NN";

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

	@Override
	public int hashCode() {
		return (name + lat + lng).hashCode();
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
		return new Place(rs.getString("name"), rs.getDouble("lat"),
				rs.getDouble("lng"));
	}
}
