package org.dentleisen.appening2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

/**
 * Counts place occurence in tweets by place and for specific time ranges.
 * replaced java code with stored procedure and fulltext index, see
 * appening2.sql
 */
public class WordCounter {
	private static Logger log = Logger.getLogger(WordCounter.class);

	public static void main(String[] args) {
		generateTsvSept();
	}

	public static void generateTsvSept() {
		try {
			Connection c = Utils.getConnection();
			Statement s = c.createStatement();
			ResultSet rs = s
					.executeQuery("SELECT `id`,`name`,`start`,`end`, fulltext_score(`name`,12,`start`,`end`) AS `mentioned` FROM (SELECT `id`,`name`,`start`,`end` FROM `places` CROSS JOIN `dateranges` WHERE `start` >= '2012-08-31' AND `end` <= '2012-09-31') AS `timeandplace`;");
			while (rs.next()) {
				System.out.println(rs.getString("id") + "\t"
						+ rs.getString("name") + "\t" + rs.getDate("start")
						+ "\t" + rs.getDate("end") + "\t"
						+ rs.getInt("mentioned"));
			}

			rs.close();
			s.close();
			c.close();
		} catch (SQLException e) {
			log.warn("Failed to load messages from db", e);
		}
	}
}
