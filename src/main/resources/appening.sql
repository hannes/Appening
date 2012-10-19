-- phpMyAdmin SQL Dump
-- version 3.4.10.1deb1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Oct 19, 2012 at 04:35 PM
-- Server version: 5.6.7
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

DECLARE `procedureName` VARCHAR(100) DEFAULT 'generateCountsProc';

DECLARE `threshold` INT DEFAULT 13;
DECLARE `presentCount` INT;
DECLARE `minHour`,`maxHour`,`curHour`,`endHour` DATETIME;

DECLARE `done` INT DEFAULT FALSE;
DECLARE `pid` INT DEFAULT 0;
DECLARE `pname` VARCHAR(200);

DECLARE `placesCursor` CURSOR FOR SELECT `id`,`name` FROM `places`;
DECLARE CONTINUE HANDLER FOR NOT FOUND SET `done` = TRUE;

SET time_zone='+0:00';

SET `minHour` = DATE_ADD(`generateDate`, INTERVAL 0 SECOND);
SET `maxHour`= DATE_SUB(DATE_SUB(NOW(), INTERVAL SECOND(NOW()) SECOND) , INTERVAL MINUTE(NOW()) MINUTE);

`procBody`:BEGIN
IF (EXISTS(SELECT `running` FROM `procstat` WHERE `procedure`=`procedureName` AND `running`=TRUE)) THEN
	LEAVE `procBody`;
END IF;
DELETE FROM `procstat` WHERE `procedure`=`procedureName`;
INSERT INTO `procstat` (`procedure`,`running`,`lastCompleted`) VALUES(`procedureName`,TRUE,NOW()); 


OPEN `placesCursor`;
`placesLoop`: LOOP
	FETCH `placesCursor` INTO `pid`,`pname`;
	IF `done` THEN
		LEAVE `placesLoop`;
	END IF;
	
	IF EXISTS(SELECT `updated` FROM `placeupdate` WHERE `place`=`pid`) THEN
		SELECT `updated` INTO `curHour` FROM `placeupdate` WHERE `place`=`pid`;
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
	
	
	IF EXISTS(SELECT `updated` FROM `placeupdate` WHERE `place`=`pid`) THEN
		UPDATE `placeupdate` SET `updated`=`maxHour` WHERE `place`=`pid`;
	ELSE
		INSERT INTO `placeupdate` (`place`,`updated`) VALUES (`pid`,`maxHour`);
	END IF;
	
END LOOP;
CLOSE `placesCursor`;

UPDATE `procstat` SET `running`=FALSE,`lastCompleted`=NOW() WHERE `procedure`=`procedureName`;

END;
	
END$$

DROP PROCEDURE IF EXISTS `generateRealtimeProc`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `generateRealtimeProc`()
    MODIFIES SQL DATA
    DETERMINISTIC
BEGIN

DECLARE `procedureName` VARCHAR(100) DEFAULT 'generateRealtimeProc';

DECLARE `threshold` INT DEFAULT 13;
DECLARE `presentCount` INT;
DECLARE `startHour`,`endHour` DATETIME;

DECLARE `done` INT DEFAULT FALSE;
DECLARE `pid` INT DEFAULT 0;
DECLARE `pname` VARCHAR(200);

DECLARE `placesCursor` CURSOR FOR SELECT `id`,`name` FROM `places`;
DECLARE CONTINUE HANDLER FOR NOT FOUND SET `done` = TRUE;

SET time_zone='+0:00';


SET `startHour`= DATE_SUB(DATE_SUB(NOW(), INTERVAL SECOND(NOW()) SECOND) , INTERVAL MINUTE(NOW()) MINUTE);
SET `endHour`= NOW();

`procBody`:BEGIN
IF (EXISTS(SELECT `running` FROM `procstat` WHERE `procedure`=`procedureName` AND `running`=TRUE)) THEN
	LEAVE `procBody`;
END IF;
DELETE FROM `procstat` WHERE `procedure`=`procedureName`;
INSERT INTO `procstat` (`procedure`,`running`,`lastCompleted`) VALUES(`procedureName`,TRUE,NOW()); 

OPEN `placesCursor`;

`placesLoop`: LOOP
	FETCH `placesCursor` INTO `pid`,`pname`;
	IF `done` THEN
		LEAVE `placesLoop`;
	END IF;
	DELETE FROM `nowcounts` WHERE `place`=`pid`;
	SET `presentCount` = 
		fulltext_score(`pname`,`threshold`,`startHour`,`endHour`);   	
	IF `presentCount` > 0 THEN 
		INSERT INTO `nowcounts` (`place`,`count`) VALUES (`pid`,`presentCount`);		
	END IF;			
		
END LOOP;
CLOSE `placesCursor`;

UPDATE `procstat` SET `running`=FALSE,`lastCompleted`=NOW() WHERE `procedure`=`procedureName`;

	
END;

END$$

DROP PROCEDURE IF EXISTS `oldMessagesMoveProc`$$
CREATE DEFINER=`root`@`localhost` PROCEDURE `oldMessagesMoveProc`()
    MODIFIES SQL DATA
    DETERMINISTIC
BEGIN

DECLARE `procedureName` VARCHAR(100) DEFAULT 'oldMessagesMoveProc';

DECLARE `cutoffHour` DATETIME;

SET `cutoffHour`= DATE_SUB(NOW(), INTERVAL 24 HOUR);

`procBody`:BEGIN
IF (EXISTS(SELECT `running` FROM `procstat` WHERE `procedure`=`procedureName` AND `running`=TRUE)) THEN
	LEAVE `procBody`;
END IF;
DELETE FROM `procstat` WHERE `procedure`=`procedureName`;
INSERT INTO `procstat` (`procedure`,`running`,`lastCompleted`) VALUES(`procedureName`,TRUE,NOW()); 

INSERT INTO `messagesold` SELECT * FROM `messages` WHERE `created` < `cutoffHour`;
DELETE FROM `messages` WHERE `created` < `cutoffHour`;

UPDATE `procstat` SET `running`=FALSE,`lastCompleted`=NOW() WHERE `procedure`=`procedureName`;

END;


END$$

--
-- Functions
--
DROP FUNCTION IF EXISTS `fulltext_score`$$
CREATE DEFINER=`root`@`localhost` FUNCTION `fulltext_score`(`paramPlace` VARCHAR(100) CHARSET utf8, `paramThreshold` FLOAT, `startDate` DATETIME, `endDate` DATETIME) RETURNS int(11)
    DETERMINISTIC
BEGIN

DECLARE `ct` INT;

SELECT COUNT(`id`) INTO `ct` FROM `messages` WHERE MATCH (`text`) AGAINST (`paramPlace` IN NATURAL LANGUAGE MODE) > `paramThreshold` AND `created` > `startDate` AND `created` < `endDate`;

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
-- Table structure for table `linksresolved`
--

DROP TABLE IF EXISTS `linksresolved`;
CREATE TABLE IF NOT EXISTS `linksresolved` (
  `place` int(11) NOT NULL,
  `resolved` datetime NOT NULL,
  PRIMARY KEY (`place`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
CREATE TABLE IF NOT EXISTS `messages` (
  `id` varchar(100) NOT NULL,
  `created` datetime NOT NULL,
  `user` varchar(100) NOT NULL,
  `text` mediumtext NOT NULL,
  PRIMARY KEY (`id`),
  KEY `dindex` (`created`),
  FULLTEXT KEY `ft` (`text`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- Table structure for table `messagesold`
--

DROP TABLE IF EXISTS `messagesold`;
CREATE TABLE IF NOT EXISTS `messagesold` (
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
-- Table structure for table `nowcounts`
--

DROP TABLE IF EXISTS `nowcounts`;
CREATE TABLE IF NOT EXISTS `nowcounts` (
  `place` int(11) NOT NULL,
  `count` int(11) NOT NULL,
  PRIMARY KEY (`place`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `placementioned`
--

DROP TABLE IF EXISTS `placementioned`;
CREATE TABLE IF NOT EXISTS `placementioned` (
  `place` int(11) NOT NULL,
  `mentioned` datetime NOT NULL,
  PRIMARY KEY (`place`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

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
) ENGINE=MyISAM  DEFAULT CHARSET=utf8 AUTO_INCREMENT=5934 ;

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

-- --------------------------------------------------------

--
-- Table structure for table `procstat`
--

DROP TABLE IF EXISTS `procstat`;
CREATE TABLE IF NOT EXISTS `procstat` (
  `procedure` varchar(100) NOT NULL,
  `running` tinyint(1) NOT NULL,
  `lastcompleted` datetime NOT NULL,
  PRIMARY KEY (`procedure`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Stand-in structure for view `top100places`
--
DROP VIEW IF EXISTS `top100places`;
CREATE TABLE IF NOT EXISTS `top100places` (
`id` int(11)
,`name` varchar(200)
,`lat` double
,`lng` double
,`cnt` decimal(32,0)
);
-- --------------------------------------------------------

--
-- Table structure for table `urls`
--

DROP TABLE IF EXISTS `urls`;
CREATE TABLE IF NOT EXISTS `urls` (
  `place` int(11) NOT NULL,
  `tweeted` datetime NOT NULL,
  `url` varchar(100) CHARACTER SET latin1 NOT NULL,
  `type` varchar(255) CHARACTER SET latin1 NOT NULL,
  `title` varchar(255) NOT NULL,
  `mediaUrl` varchar(255) CHARACTER SET latin1 NOT NULL,
  PRIMARY KEY (`url`),
  KEY `place` (`place`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- Structure for view `top100places`
--
DROP TABLE IF EXISTS `top100places`;

CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `top100places` AS select `places`.`id` AS `id`,`places`.`name` AS `name`,`places`.`lat` AS `lat`,`places`.`lng` AS `lng`,sum(`counts`.`count`) AS `cnt` from (`counts` join `places` on((`counts`.`place` = `places`.`id`))) group by `places`.`id`,`places`.`lat`,`places`.`lng` order by `cnt` desc limit 100;

DELIMITER $$
--
-- Events
--
DROP EVENT `generateCountsEvent`$$
CREATE DEFINER=`root`@`localhost` EVENT `generateCountsEvent` ON SCHEDULE EVERY 1 HOUR STARTS '2012-01-01 00:00:00' ON COMPLETION NOT PRESERVE ENABLE DO CALL generateCountsProc('2012-09-21')$$

DROP EVENT `generateRealtimeEvent`$$
CREATE DEFINER=`root`@`localhost` EVENT `generateRealtimeEvent` ON SCHEDULE EVERY 5 MINUTE STARTS '2012-01-01 00:00:00' ON COMPLETION NOT PRESERVE ENABLE DO CALL generateRealtimeProc()$$

DROP EVENT `oldMessagesMoveEvent`$$
CREATE DEFINER=`root`@`localhost` EVENT `oldMessagesMoveEvent` ON SCHEDULE EVERY 1 DAY STARTS '2012-01-01 04:06:00' ON COMPLETION NOT PRESERVE ENABLE DO CALL oldMessagesMoveProc()$$

DROP EVENT `purgeLogsEvent`$$
CREATE DEFINER=`root`@`localhost` EVENT `purgeLogsEvent` ON SCHEDULE EVERY 1 DAY STARTS '2012-01-01 00:00:00' ON COMPLETION NOT PRESERVE ENABLE DO purge binary logs before now()$$

DELIMITER ;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
