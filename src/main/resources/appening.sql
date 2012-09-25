-- phpMyAdmin SQL Dump
-- version 3.4.10.1deb1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Sep 25, 2012 at 10:28 AM
-- Server version: 5.5.24
-- PHP Version: 5.3.10-1ubuntu3.4

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- Database: `appening2`
--

DELIMITER $$
--
-- Procedures
--
DROP PROCEDURE IF EXISTS `generateCountsProc`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `generateCountsProc`( `generateDate` DATE)
    MODIFIES SQL DATA
    DETERMINISTIC
BEGIN

DECLARE `threshold` INT DEFAULT 13;

DECLARE `presentCount` INT;

DECLARE `minHour`,`maxHour`,`curHour`,`endHour` DATETIME;



DECLARE `done` INT DEFAULT FALSE;

DECLARE `pid` INT DEFAULT 0;

DECLARE `pname` VARCHAR(200);



DECLARE `placesCursor` CURSOR FOR SELECT `id`,`name` FROM `places`;

DECLARE CONTINUE HANDLER FOR NOT FOUND SET `done` = TRUE;



SET `minHour` = DATE_ADD(`generateDate`, INTERVAL 0 SECOND);

SET `maxHour`= DATE_SUB(DATE_SUB(NOW(), INTERVAL SECOND(NOW()) SECOND) , INTERVAL MINUTE(NOW()) MINUTE);



OPEN `placesCursor`;



`placesLoop`: LOOP

	FETCH `placesCursor` INTO `pid`,`pname`;

	IF `done` THEN

		LEAVE `placesLoop`;

	END IF;

	

	IF EXISTS(SELECT `updated` FROM `placeupdate` WHERE `place`=`pid`) THEN

		SELECT `updated` INTO `curHour` FROM `placeupdate` WHERE `place`=`pid`;

		DELETE FROM `placeupdate` WHERE `place`=`pid`;

	ELSE

		SET `curHour`= `minHour`;

	END IF;

	

	WHILE `curHour` < `maxHour` DO

		SET `endHour` = DATE_ADD(`curHour`, INTERVAL 1 HOUR);

	 	SET `presentCount` = 

	 		fulltext_score(`pname`,`threshold`,`curHour`,`endHour`);   	

		IF `presentCount` > 0 THEN 

			INSERT INTO `counts` (`place`,`start`,`end`,`count`) VALUES 

				(`pid`,`curHour`,`endHour`,`presentCount`);	

		END IF;

		SET `curHour` = `endHour`;	

	END WHILE;

	

	INSERT INTO `placeupdate` (`place`,`updated`) VALUES (`pid`,`maxHour`);

	

END LOOP;

CLOSE `placesCursor`;

	

END$$

--
-- Functions
--
DROP FUNCTION IF EXISTS `fulltext_score`$$
CREATE DEFINER=`root`@`localhost` FUNCTION `fulltext_score`(`paramPlace` VARCHAR(100) CHARSET utf8, `paramThreshold` FLOAT, `startDate` DATETIME, `endDate` DATETIME) RETURNS int(11)
    DETERMINISTIC
BEGIN

DECLARE `ct` INT;

SELECT COUNT(*) INTO `ct` FROM (SELECT `id`, MATCH (`text`) AGAINST (CONCAT('"',`paramPlace`,'"') IN NATURAL LANGUAGE MODE) AS `score` FROM `messages` WHERE MATCH (`text`) AGAINST (CONCAT('"',`paramPlace`,'"') IN NATURAL LANGUAGE MODE) AND `created` > `startDate` AND `created` < `endDate`) AS `scores` WHERE `score`> `paramThreshold`;

RETURN (`ct`);

END$$

DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `counts`
--

DROP TABLE IF EXISTS `counts`;
CREATE TABLE IF NOT EXISTS `counts` (
  `place` int(11) NOT NULL,
  `start` datetime NOT NULL,
  `end` datetime NOT NULL,
  `count` int(11) NOT NULL,
  KEY `dindex` (`start`,`end`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
CREATE TABLE IF NOT EXISTS `messages` (
  `id` varchar(100) NOT NULL,
  `created` datetime NOT NULL,
  `user` varchar(100) NOT NULL,
  `text` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `dindex` (`created`),
  FULLTEXT KEY `ft` (`text`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `places`
--

DROP TABLE IF EXISTS `places`;
CREATE TABLE IF NOT EXISTS `places` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL,
  `lat` double NOT NULL,
  `lng` double NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM  DEFAULT CHARSET=latin1 AUTO_INCREMENT=5934 ;

-- --------------------------------------------------------

--
-- Table structure for table `placeupdate`
--

DROP TABLE IF EXISTS `placeupdate`;
CREATE TABLE IF NOT EXISTS `placeupdate` (
  `place` int(11) NOT NULL,
  `updated` datetime NOT NULL,
  PRIMARY KEY (`place`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

DELIMITER $$
--
-- Events
--
DROP EVENT `generateCountsEvent`$$
CREATE DEFINER=`root`@`localhost` EVENT `generateCountsEvent` ON SCHEDULE EVERY 1 HOUR STARTS '2012-09-24 16:01:00' ON COMPLETION NOT PRESERVE ENABLE DO CALL generateCountsProc('2012-09-21')$$

DELIMITER ;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
